/**
 * 流程定义的校验（docs/decisions/0005 第 7 节）：设计器保存时、后端发布时跑同一份规则。只给代号和参数，文案在前端的语言包里。
 * 错误（error）不能发布：引擎会走不下去；警告（warning）可以发布，多半是画漏了。
 *
 *   validateProcess(definition, { form: manifest });
 *   // → [{ code: 'outcome-unrouted', severity: 'error', node: 'check', params: { outcome: 'back' } }]
 *
 * 要查注册情况的几条（扩展是否注册、调用的流程是否存在、工作日历是否存在）只在给了 context 里对应的函数时才查，后端发布时给。
 */
import { conditionFields, isConditionComplete, type Condition } from './condition.js';
import {
  isBranching,
  BUILTIN_NODE_TYPES,
  isOrgId,
  isRef,
  type AssigneeSource,
  type FieldAccess,
  type Outcome,
  type ProcessDefinition,
  type ProcessEdge,
  type ProcessNode,
  type TaskNode,
} from './definition.js';
import { gatewayPairs } from './gateway-pairs.js';
import { DEFINITION_SCHEMA } from './schema.js';
import { processGraph, type ProcessGraph } from './graph.js';
import { manifestField, type FormManifest } from './manifest.js';

/** 问题的严重程度 */
export type Severity = 'error' | 'warning';

/** 校验发现的一个问题 */
export interface Problem {
  readonly code: string;
  readonly severity: Severity;
  readonly node?: string;
  readonly edge?: string;
  readonly lane?: string;
  /** 字段清单的校验：出问题的字段路径（明细表的列写成 `列表.列`） */
  readonly field?: string;
  readonly params?: Readonly<Record<string, string | number>>;
}

/** 扩展的种类 */
export type ExtensionKind = 'resolver' | 'action' | 'guard' | 'computed' | 'node' | 'completion';

/** 校验时能用到的外部信息；后端发布时给全，设计器里按能拿到的给 */
export interface ValidationContext {
  /** 流程引用的表单的字段清单 */
  readonly form?: FormManifest;
  /** 扩展有没有注册 */
  readonly isRegistered?: (kind: ExtensionKind, name: string) => boolean;
  /** 流程 key 存不存在、有没有停用 */
  readonly processExists?: (key: string) => boolean;
  /** 某个流程（最新版本）调用了哪些流程，查调用成环 */
  readonly callsOf?: (key: string) => readonly string[];
  /** 工作日历存不存在 */
  readonly calendarExists?: (name: string) => boolean;
}

/** 各代号的严重程度；没列出的都是错误 */
const WARNINGS: ReadonlySet<string> = new Set([
  'node-unreachable',
  'edge-default-not-gateway',
  'gateway-edge-unconditional',
  'parallel-edge-condition',
  'parallel-useless',
  'form-required-unfillable',
]);

/** `sys.` 下引擎给的只读值；`sys.round.节点` 另外判断 */
const SYSTEM_NAMES: ReadonlySet<string> = new Set(['initiator', 'startedAt', 'instanceId', 'businessKey']);

/** 校验一个流程定义；问题按规则的先后、再按定义里的先后排 */
export function validateProcess(definition: ProcessDefinition, context: ValidationContext = {}): Problem[] {
  const problems: Problem[] = [];
  const report = (code: string, at: { node?: string; edge?: string; lane?: string } = {}, params?: Record<string, string | number>) =>
    problems.push({ code, severity: WARNINGS.has(code) ? 'warning' : 'error', ...at, ...(params ? { params } : {}) });
  const graph = processGraph(definition);

  if (typeof definition.schema === 'number' && definition.schema > DEFINITION_SCHEMA) report('schema-newer', {}, { schema: definition.schema });
  checkIdentities(definition, report);
  checkStartAndEnd(definition, graph, report);
  checkEdges(definition, graph, report);
  for (const node of definition.nodes) checkNode(node, graph, context, report);
  checkReachability(definition, graph, report);
  checkGateways(definition, report);
  checkReturnsAndEscalations(definition, graph, report);
  checkReferences(definition, graph, context, report);
  checkOrgIds(definition, report);
  checkForm(definition, context, report);
  checkPublishOnly(definition, context, report);
  return dedupe(problems);
}

type Report = (code: string, at?: { node?: string; edge?: string; lane?: string }, params?: Record<string, string | number>) => void;

/** 同一个代号、同一个位置、同样的参数只留一条 */
function dedupe(problems: Problem[]): Problem[] {
  const seen = new Set<string>();
  return problems.filter((problem) => {
    const key = JSON.stringify([problem.code, problem.node, problem.edge, problem.lane, problem.params]);
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

// ---------- 标识、开始与结束、连线 ----------

function checkIdentities(definition: ProcessDefinition, report: Report): void {
  const duplicates = (ids: readonly string[]) => ids.filter((id, i) => ids.indexOf(id) !== i);
  for (const id of new Set(duplicates(definition.nodes.map((n) => n.id)))) report('node-duplicate', { node: id });
  for (const id of new Set(duplicates(definition.edges.map((e) => e.id)))) report('edge-duplicate', { edge: id });
  for (const id of new Set(duplicates((definition.lanes ?? []).map((l) => l.id)))) report('lane-duplicate', { lane: id });
  for (const node of definition.nodes) {
    if (!BUILTIN_NODE_TYPES.has(node.type) && !node.type.startsWith('x-')) report('node-type-unknown', { node: node.id }, { type: node.type });
    if (node.lane !== undefined && !(definition.lanes ?? []).some((lane) => lane.id === node.lane)) {
      report('lane-unknown', { node: node.id }, { lane: node.lane });
    }
  }
}

function checkStartAndEnd(definition: ProcessDefinition, graph: ProcessGraph, report: Report): void {
  const starts = definition.nodes.filter((n) => n.type === 'start');
  if (starts.length === 0) report('start-missing');
  for (const extra of starts.slice(1)) report('start-multiple', { node: extra.id });
  for (const start of starts) if (graph.incoming(start.id).length > 0) report('start-incoming', { node: start.id });
  if (!definition.nodes.some((n) => n.type === 'end')) report('end-missing');
}

function checkEdges(definition: ProcessDefinition, graph: ProcessGraph, report: Report): void {
  for (const edge of definition.edges) {
    for (const side of ['from', 'to'] as const) {
      if (!graph.node(edge[side])) report('edge-unknown-node', { edge: edge.id }, { side, node: edge[side] });
    }
    const from = graph.node(edge.from);
    if (!from) continue;
    if (edge.outcome !== undefined) {
      if (!isBranching(from.type)) report('edge-outcome-not-branching', { edge: edge.id }, { outcome: edge.outcome });
      else {
        const declared = declaredOutcomes(from);
        if (declared && !declared.includes(edge.outcome)) report('edge-unknown-outcome', { edge: edge.id }, { outcome: edge.outcome });
      }
    }
    if (edge.default && from.type !== 'exclusive' && from.type !== 'inclusive') report('edge-default-not-gateway', { edge: edge.id });
    if (edge.when && !isConditionComplete(edge.when)) report('condition-incomplete', { edge: edge.id });
  }
}

/** 节点写明了的结果；自动节点、调用流程的结果由运行时给出，没有清单 */
function declaredOutcomes(node: ProcessNode): readonly string[] | undefined {
  if (node.type === 'task') return (node.outcomes ?? []).map((o) => o.key);
  if (node.type === 'receive') return node.outcomes;
  return undefined;
}

// ---------- 各类节点 ----------

function checkNode(node: ProcessNode, graph: ProcessGraph, context: ValidationContext, report: Report): void {
  const out = graph.outgoing(node.id);
  if (node.type === 'end') {
    if (out.length > 0) report('end-outgoing', { node: node.id });
    return;
  }
  if (out.length === 0) report('node-no-outgoing', { node: node.id });

  if (node.type === 'task') checkTask(node, graph, context, report);
  if (node.type === 'receive') {
    const outcomes = node.outcomes ?? [];
    for (const key of new Set(outcomes.filter((k, i) => outcomes.indexOf(k) !== i))) report('outcome-duplicate', { node: node.id }, { outcome: key });
    checkUnrouted(node.id, outcomes, out, report);
    if (node.overdue?.action === 'complete' && !outcomes.includes(node.overdue.outcome)) {
      report('overdue-unknown-outcome', { node: node.id }, { outcome: node.overdue.outcome });
    }
  }
  if (node.type === 'exclusive' || node.type === 'inclusive') {
    if (out.filter((e) => e.default).length > 1) report('gateway-multiple-default', { node: node.id });
    if (out.length > 1) for (const edge of out) if (!edge.when && !edge.default) report('gateway-edge-unconditional', { edge: edge.id });
  }
  if (node.type === 'parallel') {
    for (const edge of out) if (edge.when) report('parallel-edge-condition', { edge: edge.id });
    if (out.length === 1 && graph.incoming(node.id).length === 1) report('parallel-useless', { node: node.id });
  }
}

function checkTask(node: TaskNode, graph: ProcessGraph, context: ValidationContext, report: Report): void {
  // 草稿里可能还没写结果：当作空的，报 task-no-outcome，而不是校验本身出错
  const outcomes = node.outcomes ?? [];
  const keys = outcomes.map((o) => o.key);
  if (keys.length === 0) report('task-no-outcome', { node: node.id });
  for (const key of new Set(keys.filter((k, i) => keys.indexOf(k) !== i))) report('outcome-duplicate', { node: node.id }, { outcome: key });
  // 终止的结果、处理时选退回节点的结果不走出边
  checkUnrouted(
    node.id,
    outcomes.filter((o) => o.kind !== 'terminate' && !choosesTarget(o)).map((o) => o.key),
    graph.outgoing(node.id),
    report,
  );
  for (const outcome of outcomes) {
    if ((outcome.target as string | undefined) !== undefined && !choosesTarget(outcome)) {
      report('return-target-invalid', { node: node.id }, { outcome: outcome.key });
    }
  }
  if (node.overdue?.action === 'complete' && !keys.includes(node.overdue.outcome)) {
    report('overdue-unknown-outcome', { node: node.id }, { outcome: node.overdue.outcome });
  }
  // 引擎自己选的结果（超时自动完成、会签没达到门槛时的 fallback）没人选退回的节点
  const automatic = [node.overdue?.action === 'complete' ? node.overdue.outcome : undefined, node.multi?.fallback];
  for (const key of automatic) {
    if (key !== undefined && outcomes.some((o) => o.key === key && choosesTarget(o))) {
      report('outcome-choose-automatic', { node: node.id }, { outcome: key });
    }
  }
  const lane = graph.lane(node.lane);
  // 会签时每份待办给参与人，不看 assignees：参与人是空列表（不是引用）才算没写
  const nobody = node.multi ? Array.isArray(node.multi.participants) && node.multi.participants.length === 0 : !node.assignees?.length;
  if (nobody && !lane?.inbox) report('assignee-missing', { node: node.id });
  const sources = [...(node.assignees ?? []), ...(Array.isArray(node.multi?.participants) ? node.multi.participants : [])];
  for (const source of flattenSources(sources)) {
    if ('sameAs' in source && graph.node(source.sameAs)?.type !== 'task') {
      report('assignee-unknown-node', { node: node.id }, { sameAs: source.sameAs });
    }
  }
  const multi = node.multi;
  if (multi && (multi.complete === 'ratio' || multi.complete === 'votes')) {
    if (multi.fallback === undefined) report('multi-no-fallback', { node: node.id });
    else if (!keys.includes(multi.fallback)) report('multi-no-fallback', { node: node.id }, { fallback: multi.fallback });
  }
  if (multi && !['all', 'any', 'ratio', 'votes', 'sequential'].includes(multi.complete) && context.isRegistered?.('completion', multi.complete) === false) {
    report('extension-unknown', { node: node.id }, { kind: 'completion', name: multi.complete });
  }
}

/** 处理时选退回到哪个节点的结果（kind: return、target: choose）：不走出边 */
function choosesTarget(outcome: Outcome): boolean {
  return outcome.kind === 'return' && outcome.target === 'choose';
}

/** 每个结果都要有出边：写明这个结果的，或没写结果的 */
function checkUnrouted(nodeId: string, outcomes: readonly string[], out: readonly ProcessEdge[], report: Report): void {
  const catchAll = out.some((e) => e.outcome === undefined);
  for (const key of new Set(outcomes)) {
    if (!catchAll && !out.some((e) => e.outcome === key)) report('outcome-unrouted', { node: nodeId }, { outcome: key });
  }
}

/** 来源连同"上一步选"里嵌着的来源，平铺开 */
function flattenSources(sources: readonly AssigneeSource[]): AssigneeSource[] {
  return sources.flatMap((source) => ('pick' in source ? [source, ...flattenSources(source.pick.from ?? [])] : [source]));
}

// ---------- 走得通 ----------

function checkReachability(definition: ProcessDefinition, graph: ProcessGraph, report: Report): void {
  const start = graph.start;
  if (!start) return;
  const reached = walk([start.id], (id) => graph.outgoing(id).map((e) => e.to));
  for (const node of definition.nodes) if (!reached.has(node.id)) report('node-unreachable', { node: node.id });
  const ends = definition.nodes.filter((n) => n.type === 'end').map((n) => n.id);
  if (ends.length === 0) return;
  const reachesEnd = walk(ends, (id) => graph.incoming(id).map((e) => e.from));
  for (const node of definition.nodes) {
    if (reached.has(node.id) && !reachesEnd.has(node.id)) report('node-dead-end', { node: node.id });
  }
}

/** 从 roots 出发能走到的节点 */
function walk(roots: readonly string[], next: (id: string) => readonly string[]): Set<string> {
  const seen = new Set(roots);
  const queue = [...roots];
  for (let id = queue.shift(); id !== undefined; id = queue.shift()) {
    for (const to of next(id)) {
      if (!seen.has(to)) {
        seen.add(to);
        queue.push(to);
      }
    }
  }
  return seen;
}

// ---------- 并行与包容的配对（0005 的 4.4） ----------

/** 去掉退回、提级连线后的前进图：结构化配对与"上游"都按它算 */
function forwardEdges(definition: ProcessDefinition, graph: ProcessGraph): ProcessEdge[] {
  return definition.edges.filter((e) => e.kind !== 'return' && e.kind !== 'escalate' && graph.node(e.from) && graph.node(e.to));
}

/** 直接支配节点（Cooper–Harvey–Kennedy 的迭代算法）；root 自己映射到自己，走不到的节点不在结果里 */
/** 并行、包容的分叉与汇合要配对（算法在 gateway-pairs.ts，引擎推进时用同一份） */
function checkGateways(definition: ProcessDefinition, report: Report): void {
  for (const id of gatewayPairs(definition).unpaired) report('gateway-unpaired', { node: id });
}

// ---------- 退回与提级 ----------

function checkReturnsAndEscalations(definition: ProcessDefinition, graph: ProcessGraph, report: Report): void {
  const edges = forwardEdges(definition, graph);
  const next = (id: string) => edges.filter((e) => e.from === id).map((e) => e.to);
  for (const edge of definition.edges) {
    if (!graph.node(edge.from) || !graph.node(edge.to)) continue;
    if (edge.kind === 'return' && !walk([edge.to], next).has(edge.from)) report('return-not-upstream', { edge: edge.id });
    if (edge.kind === 'escalate') {
      const target = graph.node(edge.to);
      if (target?.type !== 'task' || !hasInbox(target, graph)) report('escalate-no-inbox', { edge: edge.id });
    }
  }
}

/** 人工节点有没有收件箱：处理人里有角色、部门或扩展的种类，或者没写处理人而泳道有收件箱 */
function hasInbox(node: TaskNode, graph: ProcessGraph): boolean {
  if (node.assignees === undefined || node.assignees.length === 0) return graph.lane(node.lane)?.inbox !== undefined;
  return node.assignees.some((s) => 'role' in s || 'department' in s || 'group' in s);
}

// ---------- 组织的编号 ----------

/**
 * 直接写在定义里的用户、角色、部门编号要是正整数（接入方组织表的主键），写成名字（`"role": "finance"`）时报 `org-id-invalid`；
 * 引用（`{ ref }`）到运行时才有值，不在这里查。走的来源同 {@link collectReferences}，另加泳道的收件箱本身。与 Java 一致。
 */
function checkOrgIds(definition: ProcessDefinition, report: Report): void {
  const check = (sources: readonly AssigneeSource[] | undefined, at: { node?: string }) => {
    for (const source of flattenSources(sources ?? [])) {
      const id = 'user' in source ? source.user : 'role' in source ? source.role : 'department' in source ? source.department : 'group' in source ? source.id : undefined;
      if (id !== undefined && !isRef(id) && !isOrgId(id)) report('org-id-invalid', at, { value: String(id) });
    }
  };
  check(definition.starters, {});
  for (const lane of definition.lanes ?? []) {
    if (!lane.inbox) continue;
    check([lane.inbox as AssigneeSource], {});
    check(lane.inbox.manager, {});
  }
  for (const node of definition.nodes) {
    if (node.type === 'task') {
      check(node.assignees, { node: node.id });
      if (node.multi && !isRef(node.multi.participants)) check(node.multi.participants, { node: node.id });
    } else if (node.type === 'cc') check(node.to, { node: node.id });
  }
}

// ---------- 引用（0005 的 1.1） ----------

interface Reference {
  readonly name: string;
  readonly at: { node?: string; edge?: string };
  /** 用在连线条件里（`fn.` 只能用在这里） */
  readonly inCondition?: boolean;
  /** 期望的类型，查 ref-type-mismatch */
  readonly expect?: 'people' | 'date' | 'list';
}

function checkReferences(definition: ProcessDefinition, graph: ProcessGraph, context: ValidationContext, report: Report): void {
  const variables = new Map((definition.variables ?? []).map((v) => [v.name, v]));
  for (const ref of collectReferences(definition)) {
    const problem = resolveReference(ref, definition, graph, variables, context);
    if (problem) report(problem.code, ref.at, problem.params);
  }
  for (const edge of definition.edges) {
    for (const effect of edge.effects ?? []) {
      const target = 'set' in effect ? effect.set : effect.increment;
      if (!variables.has(target)) report('ref-unknown', { edge: edge.id }, { ref: target });
    }
  }
}

function resolveReference(
  ref: Reference,
  definition: ProcessDefinition,
  graph: ProcessGraph,
  variables: ReadonlyMap<string, { type: string }>,
  context: ValidationContext,
): { code: string; params: Record<string, string> } | undefined {
  const { name } = ref;
  const unknown = { code: 'ref-unknown', params: { ref: name } };
  if (name.startsWith('form.')) {
    if (!definition.form) return unknown;
    if (!context.form) return undefined;
    const field = manifestField(context.form, name.slice(5));
    if (!field) return unknown;
    return mismatch(ref, field.type, field.multiple === true) ? { code: 'ref-type-mismatch', params: { ref: name, type: field.type } } : undefined;
  }
  if (name.startsWith('sys.')) {
    const rest = name.slice(4);
    if (SYSTEM_NAMES.has(rest)) return undefined;
    if (rest.startsWith('round.') && graph.node(rest.slice(6))) return undefined;
    return unknown;
  }
  if (name.startsWith('fn.')) {
    if (!ref.inCondition) return unknown;
    return context.isRegistered?.('computed', name.slice(3)) === false
      ? { code: 'extension-unknown', params: { kind: 'computed', name: name.slice(3) } }
      : undefined;
  }
  const variable = variables.get(name);
  if (!variable) return unknown;
  return mismatch(ref, variable.type, false) ? { code: 'ref-type-mismatch', params: { ref: name, type: variable.type } } : undefined;
}

/** 引用的类型用不上 */
function mismatch(ref: Reference, type: string, multiple: boolean): boolean {
  switch (ref.expect) {
    case 'people':
      return type !== 'people' && type !== 'text' && type !== 'select';
    case 'date':
      return type !== 'date' && type !== 'datetime';
    case 'list':
      return type !== 'list' && type !== 'people' && !(type === 'select' && multiple);
    default:
      return false;
  }
}

/** 定义里所有的引用，连同它们在哪 */
function collectReferences(definition: ProcessDefinition): Reference[] {
  const refs: Reference[] = [];
  const add = (value: unknown, at: Reference['at'], expect?: Reference['expect']) => {
    if (isRef(value)) refs.push({ name: value.ref, at, ...(expect ? { expect } : {}) });
  };
  const addCondition = (condition: Condition | undefined, at: Reference['at']) => {
    if (condition) for (const field of conditionFields(condition)) refs.push({ name: field, at, inCondition: true });
  };
  const addSources = (sources: readonly AssigneeSource[] | undefined, at: Reference['at']) => {
    for (const source of flattenSources(sources ?? [])) {
      if ('user' in source) add(source.user, at, 'people');
      if ('role' in source) add(source.role, at);
      if ('department' in source) add(source.department, at);
      if ('group' in source) add(source.id, at);
    }
  };
  const addDue = (due: unknown, at: Reference['at']) => {
    if (typeof due === 'object' && due !== null && 'until' in due) add((due as { until: unknown }).until, at, 'date');
  };
  for (const name of definition.summary ?? []) refs.push({ name, at: {} });
  for (const match of (definition.title ?? '').matchAll(/\{([^{}]+)\}/g)) refs.push({ name: match[1]!.trim(), at: {} });
  addSources(definition.starters, {});
  for (const lane of definition.lanes ?? []) addSources(lane.inbox?.manager, {});
  for (const node of definition.nodes) {
    const at = { node: node.id };
    if (node.type === 'task') {
      addSources(node.assignees, at);
      if (node.multi) {
        if (isRef(node.multi.participants)) add(node.multi.participants, at, 'list');
        else addSources(node.multi.participants, at);
      }
      addDue(node.due, at);
    } else if (node.type === 'timer') addDue(node.wait, at);
    else if (node.type === 'receive') addDue(node.due, at);
    else if (node.type === 'cc') addSources(node.to, at);
    else if (node.type === 'call') {
      add(node.forEach, at, 'list');
      for (const value of Object.values(node.input ?? {})) {
        // forEach 循环里那一项（及它的字段 as.名字）只在 input 里有效
        if (isRef(value) && node.as && (value.ref === node.as || value.ref.startsWith(node.as + '.'))) continue;
        add(value, at);
      }
    } else if (node.type === 'service') for (const value of Object.values(node.args ?? {})) add(value, at);
  }
  for (const edge of definition.edges) {
    addCondition(edge.when, { edge: edge.id });
    for (const effect of edge.effects ?? []) if ('set' in effect) add(effect.value, { edge: edge.id });
  }
  return refs;
}

// ---------- 表单（0004 第 4 节） ----------

function checkForm(definition: ProcessDefinition, context: ValidationContext, report: Report): void {
  const manifest = context.form;
  if (!manifest) return;
  for (const node of definition.nodes) {
    if (node.type !== 'task' && node.type !== 'start') continue;
    for (const path of Object.keys(node.form?.fields ?? {})) {
      if (!manifestField(manifest, path)) report('form-field-unknown', { node: node.id }, { field: path });
    }
  }
  const start = definition.nodes.find((n) => n.type === 'start');
  if (start?.type !== 'start') return;
  const access = (name: string): FieldAccess => start.form?.fields?.[name] ?? start.form?.default ?? 'editable';
  for (const [name, field] of Object.entries(manifest)) {
    if (field.required && (access(name) === 'hidden' || access(name) === 'readonly')) {
      report('form-required-unfillable', { node: start.id }, { field: name });
    }
  }
}

// ---------- 只在发布时查的（要后端的注册情况） ----------

function checkPublishOnly(definition: ProcessDefinition, context: ValidationContext, report: Report): void {
  const registered = context.isRegistered;
  if (registered) {
    const need = (kind: ExtensionKind, name: string, at: { node?: string; edge?: string }) => {
      if (!registered(kind, name)) report('extension-unknown', at, { kind, name });
    };
    for (const node of definition.nodes) {
      const at = { node: node.id };
      if (node.type.startsWith('x-')) need('node', node.type, at);
      if (node.type === 'service') need('action', node.action, at);
      if (node.type === 'task') {
        for (const outcome of node.outcomes ?? []) for (const guard of outcome.guards ?? []) need('guard', guard, at);
        for (const source of flattenSources(node.assignees ?? [])) if ('resolver' in source) need('resolver', source.resolver, at);
      }
    }
  }
  if (context.processExists) {
    for (const node of definition.nodes) {
      if (node.type === 'call' && !context.processExists(node.process)) report('call-unknown-process', { node: node.id }, { process: node.process });
    }
  }
  if (context.callsOf) {
    const calls = definition.nodes.filter((n) => n.type === 'call').map((n) => (n as { process: string }).process);
    const reaches = walk(calls, (key) => (key === definition.key ? [] : context.callsOf!(key)));
    if (reaches.has(definition.key)) report('call-cycle', {}, { process: definition.key });
  }
  const calendar = definition.settings?.calendar;
  if (calendar !== undefined && context.calendarExists && !context.calendarExists(calendar)) {
    report('due-unknown-calendar', {}, { calendar });
  }
}
