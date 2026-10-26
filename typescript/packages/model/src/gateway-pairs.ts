/**
 * 并行、包容网关的分叉与汇合配对（0005 的 4.4，结构化）：每个分叉有一个汇合，中间的分支不跳出去。
 *
 * 按前进图（去掉退回、提级连线）算：汇合是分叉的直接后支配节点，分叉是汇合的直接支配节点，两者同类型。
 * 校验用它报 `gateway-unpaired`，引擎用它在分叉时记下"汇合要等几条分支"、在退回跳出区域时找出要取消的分支。
 * 与 Java 的 `GatewayPairs` 同一套算法。
 */
import type { ProcessDefinition } from './definition.js';
import { processGraph } from './graph.js';

/** 一份定义的配对 */
export interface GatewayPairs {
  /** 分叉配对的汇合；不是分叉、或没配上对时为 undefined */
  joinOf(fork: string): string | undefined;
  /** 汇合配对的分叉 */
  forkOf(join: string): string | undefined;
  /** 没配上对的并行、包容网关（按定义里的先后） */
  readonly unpaired: readonly string[];
  /** 分叉与汇合之间的节点（不含两头） */
  region(fork: string): ReadonlySet<string>;
  /** 节点在哪些区域里，由里到外（嵌套的并行） */
  forksAround(node: string): string[];
}

/** 结束节点都连到这个虚拟的出口，后支配树才有唯一的根 */
const EXIT = '\u0000exit';

const cache = new WeakMap<ProcessDefinition, GatewayPairs>();

/** 算出一份定义的配对（同一个定义对象只算一次） */
export function gatewayPairs(definition: ProcessDefinition): GatewayPairs {
  const cached = cache.get(definition);
  if (cached) return cached;
  const graph = processGraph(definition);
  const succ = new Map<string, string[]>();
  const pred = new Map<string, string[]>();
  const link = (map: Map<string, string[]>, from: string, to: string) => (map.get(from) ?? map.set(from, []).get(from)!).push(to);
  for (const e of definition.edges) {
    if (e.kind === 'return' || e.kind === 'escalate' || !graph.node(e.from) || !graph.node(e.to)) continue;
    link(succ, e.from, e.to);
    link(pred, e.to, e.from);
  }
  for (const node of definition.nodes) {
    if (node.type !== 'end') continue;
    link(succ, node.id, EXIT);
    link(pred, EXIT, node.id);
  }
  const successors = (id: string) => succ.get(id) ?? [];
  const predecessors = (id: string) => pred.get(id) ?? [];

  const joins = new Map<string, string>();
  const forks = new Map<string, string>();
  const unpaired: string[] = [];
  const regions = new Map<string, ReadonlySet<string>>();
  if (graph.start) {
    const idom = immediateDominators(graph.start.id, successors, predecessors);
    const ipdom = immediateDominators(EXIT, predecessors, successors);
    const type = (id: string | undefined) => (id === undefined ? undefined : graph.node(id)?.type);
    const paired = (fork: string, join: string | undefined) =>
      join !== undefined && join !== EXIT && type(join) === type(fork) && predecessors(join).length > 1 && idom.get(join) === fork;
    for (const node of definition.nodes) {
      if (node.type !== 'parallel' && node.type !== 'inclusive') continue;
      if (!idom.has(node.id) || !ipdom.has(node.id)) continue;
      if (successors(node.id).length > 1) {
        const join = ipdom.get(node.id);
        if (paired(node.id, join)) {
          joins.set(node.id, join!);
          forks.set(join!, node.id);
          regions.set(node.id, between(node.id, join!, successors));
        } else {
          unpaired.push(node.id);
        }
      }
      if (predecessors(node.id).length > 1) {
        const fork = idom.get(node.id);
        if (fork === undefined || !paired(fork, node.id) || ipdom.get(fork) !== node.id) unpaired.push(node.id);
      }
    }
  }
  const pairs: GatewayPairs = {
    joinOf: (fork) => joins.get(fork),
    forkOf: (join) => forks.get(join),
    unpaired: [...new Set(unpaired)],
    region: (fork) => regions.get(fork) ?? new Set(),
    forksAround: (node) =>
      [...regions.entries()]
        .filter(([, region]) => region.has(node))
        .sort(([, a], [, b]) => a.size - b.size)
        .map(([fork]) => fork),
  };
  cache.set(definition, pairs);
  return pairs;
}

/** 从分叉往前走、不过汇合能到的节点 */
function between(fork: string, join: string, successors: (id: string) => readonly string[]): ReadonlySet<string> {
  const seen = new Set<string>();
  const queue = [...successors(fork)];
  while (queue.length) {
    const id = queue.shift()!;
    if (id === join || id === EXIT || seen.has(id)) continue;
    seen.add(id);
    queue.push(...successors(id));
  }
  return seen;
}

/** 直接支配节点（Cooper–Harvey–Kennedy 的迭代算法）；root 映射到自己，走不到的节点不在结果里 */
function immediateDominators(root: string, successors: (id: string) => readonly string[], predecessors: (id: string) => readonly string[]): Map<string, string> {
  const order: string[] = [];
  const visited = new Set<string>();
  const visit = (id: string) => {
    visited.add(id);
    for (const next of successors(id)) if (!visited.has(next)) visit(next);
    order.push(id);
  };
  visit(root);
  const rpo = order.reverse();
  const index = new Map(rpo.map((id, i) => [id, i]));
  const idom = new Map<string, string>([[root, root]]);
  const intersect = (a: string, b: string): string => {
    let x = a;
    let y = b;
    while (x !== y) {
      while (index.get(x)! > index.get(y)!) x = idom.get(x)!;
      while (index.get(y)! > index.get(x)!) y = idom.get(y)!;
    }
    return x;
  };
  for (let changed = true; changed; ) {
    changed = false;
    for (const id of rpo) {
      if (id === root) continue;
      const preds = predecessors(id).filter((p) => idom.has(p));
      if (preds.length === 0) continue;
      const next = preds.slice(1).reduce(intersect, preds[0]!);
      if (idom.get(id) !== next) {
        idom.set(id, next);
        changed = true;
      }
    }
  }
  return idom;
}
