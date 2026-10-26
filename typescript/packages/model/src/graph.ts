/**
 * 流程的结构索引：按标识找节点、泳道，按节点找出边、入边。同一个定义对象只建一次（定义发布后不可变）。
 *
 *   const graph = processGraph(definition);
 *   graph.outgoing('check');   // 从"资料审核"出去的连线，按定义的先后
 */
import type { Lane, ProcessDefinition, ProcessEdge, ProcessNode, StartNode } from './definition.js';

/** 流程的结构索引 */
export interface ProcessGraph {
  readonly definition: ProcessDefinition;
  /** 按标识找节点；标识重复时取第一个 */
  node(id: string): ProcessNode | undefined;
  /** 按标识找泳道 */
  lane(id: string | undefined): Lane | undefined;
  /** 从这个节点出去的连线，按定义的先后 */
  outgoing(id: string): readonly ProcessEdge[];
  /** 进这个节点的连线 */
  incoming(id: string): readonly ProcessEdge[];
  /** 开始节点；没有时为 undefined */
  readonly start: StartNode | undefined;
}

const graphs = new WeakMap<ProcessDefinition, ProcessGraph>();

/** 流程的结构索引（同一个定义对象只建一次） */
export function processGraph(definition: ProcessDefinition): ProcessGraph {
  const cached = graphs.get(definition);
  if (cached) return cached;
  const nodes = new Map<string, ProcessNode>();
  for (const node of definition.nodes) if (!nodes.has(node.id)) nodes.set(node.id, node);
  const lanes = new Map<string, Lane>();
  for (const lane of definition.lanes ?? []) if (!lanes.has(lane.id)) lanes.set(lane.id, lane);
  const outgoing = new Map<string, ProcessEdge[]>();
  const incoming = new Map<string, ProcessEdge[]>();
  for (const edge of definition.edges) {
    (outgoing.get(edge.from) ?? outgoing.set(edge.from, []).get(edge.from)!).push(edge);
    (incoming.get(edge.to) ?? incoming.set(edge.to, []).get(edge.to)!).push(edge);
  }
  const graph: ProcessGraph = {
    definition,
    node: (id) => nodes.get(id),
    lane: (id) => (id == null ? undefined : lanes.get(id)),
    outgoing: (id) => outgoing.get(id) ?? [],
    incoming: (id) => incoming.get(id) ?? [],
    start: definition.nodes.find((node): node is StartNode => node.type === 'start'),
  };
  graphs.set(definition, graph);
  return graph;
}
