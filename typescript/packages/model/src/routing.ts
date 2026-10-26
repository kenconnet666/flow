/**
 * 选出边与连线的副作用（docs/decisions/0005 第 6 节）：引擎推进时用，前端预览"会走到哪些节点"时也用（0005 的 5.6）。
 * 与 Java 的 `Routing` 同一套规则，两边跑 spec/routing 的样例。
 *
 *   selectEdges(graph, 'review', { outcome: 'pass', read });   // → 走哪几条线
 *   applyEffects(edge.effects ?? [], variables, read);         // → 走过这条线后的流程变量
 */
import { evaluateCondition, type ConditionReader } from './condition.js';
import { isRef, type Effect, type ProcessEdge } from './definition.js';
import type { ProcessGraph } from './graph.js';
import { decimalText } from './values.js';

/** 选边时的输入 */
export interface RouteInput {
  /** 节点给出的结果：人工节点的处理结果、自动节点与子流程返回的、外部事件带回的；网关没有 */
  readonly outcome?: string | null;
  /** 条件里按字段名取值 */
  readonly read: ConditionReader;
}

/**
 * 从一个节点走哪几条线。
 * - 并行网关：全部出边（条件不起作用，校验会提示）；
 * - 包容网关：条件成立的都走，都不成立时走默认边；
 * - 其余：条件成立的边里，先取写明了这个结果的，再取没写结果的，都没有时走默认边——最多一条（沿用旧契约的 nextProcessEdge）。
 * 一条也选不出时返回空数组：定义有漏洞（校验会报出来），或者结果没有对应的出边。
 */
export function selectEdges(graph: ProcessGraph, nodeId: string, input: RouteInput): ProcessEdge[] {
  const edges = graph.outgoing(nodeId);
  const type = graph.node(nodeId)?.type;
  if (type === 'parallel') return [...edges];
  const outcome = input.outcome ?? null;
  const fits = (edge: ProcessEdge) => edge.outcome === undefined || edge.outcome === outcome;
  const holds = (edge: ProcessEdge) => edge.when === undefined || evaluateCondition(edge.when, input.read);
  const candidates = edges.filter((edge) => fits(edge) && !edge.default && holds(edge));
  const fallback = edges.filter((edge) => edge.default && fits(edge));
  if (type === 'inclusive') return candidates.length > 0 ? candidates : fallback.slice(0, 1);
  const chosen = candidates.find((edge) => edge.outcome !== undefined && edge.outcome === outcome) ?? candidates[0] ?? fallback[0];
  return chosen ? [chosen] : [];
}

/** 流程变量 */
export type Variables = Readonly<Record<string, unknown>>;

/**
 * 按顺序应用连线的副作用，返回新的流程变量（不改原对象）。
 * - `set`：值是引用时用 read 取；
 * - `increment`：没有、或不是数字的当作 0；按精确的小数加（`0.1 + 0.2` 是 `0.3`），结果是整数时写成数字，否则写成小数文字。
 */
export function applyEffects(effects: readonly Effect[], variables: Variables, read: ConditionReader): Record<string, unknown> {
  const next: Record<string, unknown> = { ...variables };
  for (const effect of effects) {
    if ('set' in effect) next[effect.set] = isRef(effect.value) ? read(effect.value.ref) : effect.value;
    else next[effect.increment] = addNumbers(next[effect.increment], effect.by ?? 1);
  }
  return next;
}

/** 两个数精确相加：不是数字的当作 0；结果放得进安全整数时是 number，否则是小数文字 */
function addNumbers(current: unknown, by: number): number | string {
  const sum = decimalText(current, by);
  const asNumber = Number(sum);
  return /^-?\d+$/.test(sum) && Number.isSafeInteger(asNumber) ? asNumber : sum;
}
