/**
 * 查询（0006 第 4 节，同 Java 的 Queries、PickPreview、DiagramView）：发起列表与发起页、收件箱与待办详情、实例详情（带流程图）、
 * 已办、我发起的、抄送我的、管理端的列表。
 */
import {
  fieldStates,
  FLOW_EVENT,
  isRef,
  selectEdges,
  type AssigneeSource,
  type CcItem,
  type FlowEvent,
  type Id,
  type NodeFormAccess,
  type Outcome,
  type ProcessDefinition,
  type ProcessGraph,
  type ProcessInstance,
  type TaskNode,
  type TodoItem,
  type WorkItem,
} from '@zerodep-dev/flow-model';
import type { Page } from '@zerodep-dev/query-core';
import type { Actor } from '../identity.js';
import { CANDIDATE, instanceView, STATUS, type InstanceRecord, type InstanceSearch, type Job, type JsonRecord } from '../store/types.js';
import type {
  DiagramNode,
  FormDocument,
  FormSummary,
  FormView,
  IncidentView,
  InstanceDetail,
  OutcomeView,
  PickView,
  ProcessDiagram,
  ProcessSummary,
  ReturnTarget,
  StartableProcess,
  StartView,
  WorkItemView,
} from '../view/types.js';
import { skipOutcomes } from './admin.js';
import { isCandidate, isStarter } from './assignees.js';
import type { EngineContext } from './context.js';
import type { LoadedDefinition } from './definitions.js';
import { forbidden, notFound } from './errors.js';
import { withdrawAllowed } from './handlers.js';
import { JOB, nodeOf, stillWaiting } from './jobs.js';
import { returnTargets } from './return-targets.js';
import { canWithdrawStep } from './step-withdrawal.js';
import { orgIds, readReference } from './values.js';

/** 页里的每一行换个样子 */
const mapPage = <R, T>(page: Page<R>, map: (row: R) => T): Page<T> => ({ ...page, rows: page.rows.map(map) });

/** 查询 */
export class Queries {
  constructor(private readonly context: EngineContext) {}

  /** 我能发起的流程：启用着、发起人名单里有我，按分类、名称排 */
  async startable(actor: Actor): Promise<StartableProcess[]> {
    const out: StartableProcess[] = [];
    for (const process of await this.context.stores.definitions.processes()) {
      if (process.status !== STATUS.ACTIVE) continue;
      if (!isStarter(actor, (await this.#latest(process.processKey)).model.starters)) continue;
      out.push({ key: process.processKey, name: process.name, category: process.category, version: process.latestVersion });
    }
    return out;
  }

  /** 发起页：流程与开始节点上的表单（没写权限的字段默认能改）；流程停用、我不能发起时报 `flow.forbidden` */
  async startView(actor: Actor, key: string): Promise<StartView> {
    const process = await this.context.stores.definitions.process(key);
    if (!process) throw notFound('process', key);
    if (process.status !== STATUS.ACTIVE) throw forbidden('process', key, 'disabled');
    const definition = await this.#latest(key);
    if (!isStarter(actor, definition.model.starters)) throw forbidden('process', key, 'not-a-starter');
    const read = (name: string) => (name === 'sys.initiator' ? actor.id : null);
    const start = definition.graph.start!;
    const form = definition.manifest ? await this.#formView(definition, start.form, true, {}, read) : null;
    return {
      process: { key, name: process.name, category: process.category, version: process.latestVersion },
      form,
      picks: this.#picksAfter(definition.graph, start.id, null, read),
    };
  }

  /** 我的待办：落到我的，加上我、我的角色、部门或扩展种类能认领的（我没被排除），按到达时间从新到旧；每行带实例的标题与摘要列 */
  async todo(actor: Actor, page: number, size: number): Promise<Page<TodoItem>> {
    const memberships = new Map<string, ReadonlySet<Id>>(Object.entries(actor.groups ?? {}));
    memberships.set(CANDIDATE.USER, new Set([actor.id]));
    memberships.set(CANDIDATE.ROLE, actor.roles);
    memberships.set(CANDIDATE.DEPARTMENT, actor.departments);
    return this.#withInstances(await this.context.stores.workItems.todo({ userId: actor.id, memberships }, page, size));
  }

  /** 我处理过的：按处理时间从新到旧，每行带实例的标题与摘要列 */
  async done(actor: Actor, page: number, size: number): Promise<Page<TodoItem>> {
    return this.#withInstances(await this.context.stores.workItems.done(actor.id, page, size));
  }

  /** 等我指定承办人的待办 */
  async designations(actor: Actor, page: number, size: number): Promise<Page<TodoItem>> {
    return this.#withInstances(await this.context.stores.workItems.awaitingDesignation(actor.id, page, size));
  }

  /** 我发起的：按发起时间从新到旧 */
  async started(actor: Actor, page: number, size: number): Promise<Page<ProcessInstance>> {
    return mapPage(await this.context.stores.instances.startedBy(actor.id, page, size), instanceView);
  }

  /** 抄送我的：未读在前，每行带实例的标题、摘要列与状态 */
  async ccList(actor: Actor, page: number, size: number): Promise<Page<CcItem>> {
    const ccs = await this.context.stores.ccs.received(actor.id, page, size);
    const instances = await this.#instancesOf(ccs.rows.map((c) => c.instanceId));
    return mapPage(ccs, (cc) => {
      const instance = instances.get(cc.instanceId);
      return instance ? { cc, title: instance.title, summary: instance.summary, status: instance.status } : { cc, title: null, summary: {}, status: null };
    });
  }

  /** 管理端搜实例：按发起时间从新到旧 */
  async instances(search: InstanceSearch, page: number, size: number): Promise<Page<ProcessInstance>> {
    return mapPage(await this.context.stores.instances.search(search, page, size), instanceView);
  }

  /** 管理端的流程列表：全部流程（停用的也在），按分类、名称排 */
  async processes(): Promise<ProcessSummary[]> {
    return (await this.context.stores.definitions.processes()).map((p) => ({
      key: p.processKey,
      name: p.name,
      category: p.category,
      status: p.status,
      latestVersion: p.latestVersion,
    }));
  }

  /** 异常：重试用完的作业，新的在前；instanceId 不为 null 时只看这个实例的 */
  async incidents(instanceId: Id | null, page: number, size: number): Promise<Page<IncidentView>> {
    const jobs = await this.context.stores.jobs.failed(instanceId, page, size);
    const instances = await this.#instancesOf(jobs.rows.map((j) => j.instanceId));
    const rows: IncidentView[] = [];
    for (const job of jobs.rows) rows.push(await this.#incident(job, instances.get(job.instanceId)));
    return { ...jobs, rows };
  }

  /** 一个异常：按实例的定义版本找节点的名称、跳过时能选的结果 */
  async #incident(job: Job, instance: InstanceRecord | undefined): Promise<IncidentView> {
    let node = nodeOf(job);
    if (node === null && job.workItemId !== null) node = (await this.context.stores.workItems.find(job.workItemId))?.node ?? null;
    const base = { jobId: job.id, kind: job.kind, instanceId: job.instanceId, node, attempts: job.attempts, error: job.lastError, failedAt: job.dueAt };
    if (!instance) return { ...base, instanceTitle: null, processKey: null, nodeName: null, skippable: false, outcomes: [] };
    const graph = (await this.context.definitions.ofInstance(instance)).graph;
    const skippable = job.kind === JOB.ACTION && instance.endedAt === null && stillWaiting(job, instance.state);
    return {
      ...base,
      instanceTitle: instance.title,
      processKey: instance.processKey,
      nodeName: node === null ? null : (graph.node(node)?.name ?? null),
      skippable,
      outcomes: node === null ? [] : skipOutcomes(graph, node),
    };
  }

  /** 管理端的表单列表：停用的也在，按分类、名称排 */
  async forms(): Promise<FormSummary[]> {
    return (await this.context.stores.forms.forms()).map((f) => ({ key: f.formKey, name: f.name, category: f.category, status: f.status, latestVersion: f.latestVersion }));
  }

  /** 表单的全部版本，新的在前 */
  async formVersions(formKey: string) {
    if (!(await this.context.stores.forms.form(formKey))) throw notFound('form', formKey);
    return this.context.stores.forms.versions(formKey);
  }

  /** 表单某个版本的全部内容（编辑时载入）；version 为 null 时取最新的 */
  async formDocument(formKey: string, version: number | null): Promise<FormDocument> {
    const form = await this.context.stores.forms.form(formKey);
    if (!form) throw notFound('form', formKey);
    const wanted = version ?? form.latestVersion;
    const source = await this.context.stores.forms.source(formKey, wanted);
    if (!source) throw notFound('form', `${formKey}@${wanted}`);
    return {
      key: form.formKey,
      name: form.name,
      category: form.category,
      version: source.version,
      manifest: source.manifest,
      clientSource: source.clientSource,
      serverLanguage: source.serverLanguage,
      serverSource: source.serverSource,
    };
  }

  /** 流程的全部版本，新的在前 */
  async versions(processKey: string) {
    if (!(await this.context.stores.definitions.process(processKey))) throw notFound('process', processKey);
    return this.context.stores.definitions.versions(processKey);
  }

  /** 某个版本的定义（发布时的原文）；version 为 null 时取最新的 */
  async definitionJson(processKey: string, version: number | null): Promise<string> {
    const stored = version == null ? await this.context.stores.definitions.latest(processKey) : await this.context.stores.definitions.version(processKey, version);
    if (!stored) throw notFound('definition', version == null ? processKey : `${processKey}@${version}`);
    return stored.model;
  }

  /**
   * 待办详情：待办与实例、这个节点上的表单（按节点权限去掉隐藏的字段）、能选的处理结果，以及我现在能不能认领、处理。
   * 待办已处理、实例已结束时照样能看，只是两样都不能做。
   */
  async workItem(actor: Actor, workItemId: Id): Promise<WorkItemView> {
    const item = await this.context.stores.workItems.find(workItemId);
    if (!item) throw notFound('workItem', workItemId);
    const instance = await this.context.stores.instances.find(item.instanceId);
    if (!instance) throw notFound('instance', item.instanceId);
    const definition = await this.context.definitions.ofInstance(instance);
    const task = definition.graph.node(item.node) as TaskNode;

    const open = item.state === 'open' && instance.status === 'active';
    const mine = actor.id === item.assigneeId;
    const candidate = item.assigneeId === null && isCandidate(actor, await this.context.stores.workItems.candidates(item.id));

    const stored = (await this.context.stores.formData.read(instance.id))?.values ?? {};
    const read = (name: string) => readReference(name, instance, stored);
    const form = definition.manifest ? await this.#formView(definition, task.form, false, stored, read) : null;
    const completable = open && (mine || candidate);
    const choosing = task.outcomes.some(choosesTarget);
    const targets = completable && choosing ? await this.#returnTargets(definition, instance, item.node) : [];
    // 选节点的退回在没有能退回的节点时（还在第一个人工节点上）不给：点了也做不成
    const outcomes = task.outcomes
      .filter((o) => !(completable && choosesTarget(o) && targets.length === 0))
      .map((o) => outcomeView(o, completable ? this.#picksAfter(definition.graph, task.id, o.key, read) : [], choosesTarget(o) ? targets : []));
    const assign = open && (await this.context.stores.workItems.awaitsDesignation(item.id, actor.id));
    const withdrawStep = await canWithdrawStep(this.context, actor, item, definition.graph, instance);
    const allow = task.allow ?? {};
    const actions = completable
      ? {
          transfer: allow.transfer !== false,
          delegate: allow.delegate !== false,
          addSigners: allow.addSign !== false,
          release: mine && item.assignment === 'claimed',
          assign,
          withdrawStep,
        }
      : { transfer: false, delegate: false, addSigners: false, release: false, assign, withdrawStep };
    return { workItem: item, instance: instanceView(instance), form, outcomes, claimable: open && candidate, completable, actions };
  }

  /** 实例详情：实例、表单（都只读，按流程级的 formView 去掉隐藏的字段）、全部待办、流转记录，以及我能不能撤回、催办 */
  async instance(actor: Actor, instanceId: Id): Promise<InstanceDetail> {
    const instance = await this.context.stores.instances.find(instanceId);
    if (!instance) throw notFound('instance', instanceId);
    const definition = await this.context.definitions.ofInstance(instance);
    const stored = (await this.context.stores.formData.read(instanceId))?.values ?? {};
    const access: NodeFormAccess = { default: 'readonly', ...(definition.model.settings?.formView ? { fields: definition.model.settings.formView } : {}) };
    const form = definition.manifest ? await this.#formView(definition, access, false, stored, (name) => readReference(name, instance, stored)) : null;
    const items = await this.context.stores.workItems.ofInstance(instanceId);
    const starterOfActive = actor.id === instance.startedBy && instance.status === 'active';
    const timeline = await this.context.stores.events.timeline(instanceId);
    return {
      instance: instanceView(instance),
      form,
      workItems: items,
      timeline,
      actions: { withdraw: starterOfActive && withdrawAllowed(definition, items), remind: starterOfActive && items.some((w) => w.state === 'open') },
      nodes: definition.model.nodes
        .filter((n) => n.type !== 'start' && n.type !== 'exclusive' && n.type !== 'parallel' && n.type !== 'inclusive')
        .map((n) => ({ node: n.id, name: n.name, type: n.type })),
      diagram: diagramOf(definition.model, instanceView(instance), timeline),
    };
  }

  // ───────── 共用 ─────────

  /** 流程最新发布的版本（解析好的） */
  async #latest(key: string): Promise<LoadedDefinition> {
    const stored = await this.context.stores.definitions.latest(key);
    if (!stored) throw notFound('process', key);
    return this.context.definitions.of(stored);
  }

  /** 一批实例，按编号 */
  async #instancesOf(ids: readonly Id[]): Promise<Map<Id, InstanceRecord>> {
    const rows = await this.context.stores.instances.findAll([...new Set(ids)]);
    return new Map(rows.map((r) => [r.id, r]));
  }

  /** 待办列表的每一行带上实例的标题、摘要列与状态，处理过的再带上结果的名字 */
  async #withInstances(items: Page<WorkItem>): Promise<Page<TodoItem>> {
    const instances = await this.#instancesOf(items.rows.map((w) => w.instanceId));
    const rows: TodoItem[] = [];
    for (const item of items.rows) {
      const instance = instances.get(item.instanceId);
      if (!instance) rows.push({ workItem: item, title: null, summary: {}, status: null, outcomeLabel: null });
      else rows.push({ workItem: item, title: instance.title, summary: instance.summary, status: instance.status, outcomeLabel: await this.#outcomeLabel(instance, item) });
    }
    return { ...items, rows };
  }

  /** 处理结果的名字：按实例的定义版本找节点上的 outcome；还没处理、隐式的结果时为 null */
  async #outcomeLabel(instance: InstanceRecord, item: WorkItem): Promise<string | null> {
    if (item.outcome === null) return null;
    const node = (await this.context.definitions.ofInstance(instance)).graph.node(item.node);
    if (node?.type !== 'task') return null;
    return node.outcomes.find((o) => o.key === item.outcome)?.label ?? null;
  }

  /**
   * 表单在一个节点上的样子：清单、前端代码、节点上的权限，值里去掉节点上隐藏的字段
   * （只按节点权限去掉，显示条件不成立的字段由前端按实时的值判断）。
   */
  async #formView(definition: LoadedDefinition, access: NodeFormAccess | undefined, isStart: boolean, values: JsonRecord, read: (name: string) => unknown): Promise<FormView> {
    const states = fieldStates(definition.manifest!, { access, isStart, read });
    const visible: Record<string, unknown> = {};
    for (const [name, value] of Object.entries(values)) {
      if (states.get(name)?.hiddenBy !== 'access') visible[name] = value;
    }
    const form = definition.form!;
    const client = await this.context.stores.forms.client(form.id);
    return {
      versionId: form.id,
      key: form.formKey,
      version: form.version,
      manifest: definition.manifest as JsonRecord,
      source: client?.clientSource ?? null,
      compiled: client?.clientCompiled ?? null,
      compilerVersion: client?.compilerVersion ?? null,
      access: { ...(access?.default ? { default: access.default } : {}), ...(access?.fields ? { fields: access.fields } : {}) },
      start: isStart,
      values: visible,
    };
  }

  /** 从 nodeId 按 outcome 往下走会到的、要选人的节点（穿过网关与抄送节点，到人工节点为止） */
  #picksAfter(graph: ProcessGraph, nodeId: string, outcome: string | null, read: (name: string) => unknown): PickView[] {
    const out = new Map<string, PickView>();
    const seen = new Set<string>();
    const visit = (id: string) => {
      if (seen.has(id)) return;
      seen.add(id);
      const node = graph.node(id);
      if (node?.type === 'task') {
        const sources = node.multi ? (isRef(node.multi.participants) ? [] : node.multi.participants) : (node.assignees ?? []);
        for (const source of sources) if ('pick' in source && !out.has(node.id)) out.set(node.id, this.#pickView(node, source));
      } else if (node && (node.type === 'exclusive' || node.type === 'parallel' || node.type === 'inclusive' || node.type === 'cc')) {
        for (const edge of selectEdges(graph, id, { outcome: null, read })) visit(edge.to);
      }
    };
    for (const edge of selectEdges(graph, nodeId, { outcome, read })) visit(edge.to);
    return [...out.values()];
  }

  #pickView(task: TaskNode, source: Extract<AssigneeSource, { pick: unknown }>): PickView {
    return { node: task.id, nodeName: task.name, max: source.pick.max ?? null, candidates: this.#candidates(source.pick.from ?? []) };
  }

  /** from 展开成人；没写、或写了要看实例才知道的来源时为 null（不限，提交时引擎再核对） */
  #candidates(from: readonly AssigneeSource[]): PickView['candidates'] {
    if (from.length === 0) return null;
    const users = new Set<Id>();
    const members = (kind: string, value: unknown) => {
      for (const id of orgIds(value)) for (const member of this.context.org.members?.(kind, id) ?? []) users.add(member);
    };
    for (const source of from) {
      if ('user' in source && !isRef(source.user)) for (const id of orgIds(source.user)) users.add(id);
      else if ('role' in source && !isRef(source.role)) members(CANDIDATE.ROLE, source.role);
      else if ('department' in source && !isRef(source.department)) members(CANDIDATE.DEPARTMENT, source.department);
      else if ('group' in source && !isRef(source.id)) members(source.group, source.id);
      else return null;
    }
    return [...users].map((id) => ({ id, name: this.context.org.userName(id) }));
  }

  /** 退回时能选的节点，带上次在那里处理的人（取这个节点最近处理完的待办） */
  async #returnTargets(definition: LoadedDefinition, instance: InstanceRecord, current: string): Promise<ReturnTarget[]> {
    const items = await this.context.stores.workItems.ofInstance(instance.id);
    return returnTargets(definition, instance.state, current).map((node) => {
      const handled = items.filter((i) => i.node === node.id && i.completedAt !== null).sort((a, b) => (a.completedAt! < b.completedAt! ? 1 : -1));
      return { node: node.id, nodeName: node.name, handlerName: handled[0]?.completedByName ?? null };
    });
  }
}

/** 处理时选退回到哪个节点的结果 */
const choosesTarget = (outcome: Outcome) => outcome.kind === 'return' && outcome.target === 'choose';

/** 处理结果给前端的样子：没写意见要求时是 none */
const outcomeView = (o: Outcome, picks: PickView[], targets: ReturnTarget[]): OutcomeView => ({
  key: o.key,
  label: o.label,
  kind: o.kind ?? 'approve',
  comment: o.comment ?? 'none',
  confirm: o.confirm === true,
  picks,
  targets,
});

/** 实例的流程图：离开过的节点是走过的，"正在处理的节点"是正停着的，离开时记下的连线是走过的；位置取定义的 layout.nodes */
function diagramOf(definition: ProcessDefinition, instance: ProcessInstance, timeline: readonly FlowEvent[]): ProcessDiagram {
  const left = new Set<string>();
  const taken = new Set<string>();
  for (const event of timeline) {
    if (event.type !== FLOW_EVENT.NODE_LEFT) continue;
    if (event.node !== null) left.add(event.node);
    if (event.edge !== null) taken.add(event.edge);
  }
  const current = new Set(instance.current.map((c) => c.node));
  // 结束了：走到的结束节点也算走过（结束节点不会"离开"）
  const ended = instance.endedAt !== null;
  const reached = (node: string) => definition.edges.some((e) => e.to === node && taken.has(e.id));
  const positions = definition.layout?.nodes ?? {};
  const nodes: DiagramNode[] = definition.nodes.map((node) => ({
    id: node.id,
    name: node.name,
    type: node.type,
    x: positions[node.id]?.x ?? null,
    y: positions[node.id]?.y ?? null,
    state: current.has(node.id) ? 'current' : left.has(node.id) || (ended && node.type === 'end' && reached(node.id)) ? 'done' : 'pending',
  }));
  const labelOf = (edge: ProcessDefinition['edges'][number]) => {
    if (edge.label !== undefined) return edge.label;
    if (edge.outcome === undefined) return null;
    const from = definition.nodes.find((n) => n.id === edge.from);
    return (from?.type === 'task' ? from.outcomes.find((o) => o.key === edge.outcome)?.label : undefined) ?? edge.outcome;
  };
  return {
    nodes,
    edges: definition.edges.map((edge) => ({ id: edge.id, from: edge.from, to: edge.to, label: labelOf(edge), kind: edge.kind ?? null, taken: taken.has(edge.id) })),
  };
}
