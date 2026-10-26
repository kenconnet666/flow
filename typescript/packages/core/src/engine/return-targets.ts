/**
 * 退回时能选的节点（处理结果 `{ kind: "return", target: "choose" }`，0005 的 5.4，同 Java 的 ReturnTargets）：
 * 当前节点的上游（沿前进的连线能走到当前节点）、这个实例里走到过的人工节点，按定义里的先后；
 * 不在当前节点之外的并行区域里——旁边的分支、已经汇合过的区域进去了汇合等不齐。
 */
import type { ProcessGraph, TaskNode } from '@zerodep-dev/flow-model';
import type { RunState } from '../run-state.js';
import type { LoadedDefinition } from './definitions.js';
import { invalid } from './errors.js';

/** 当前节点 current 能退回到的人工节点 */
export function returnTargets(definition: LoadedDefinition, state: RunState, current: string): TaskNode[] {
  const upstream = upstreamOf(definition.graph, current);
  const around = new Set(definition.pairs.forksAround(current));
  return definition.model.nodes.filter(
    (node): node is TaskNode =>
      node.type === 'task' &&
      node.id !== current &&
      upstream.has(node.id) &&
      state.round(node.id) > 0 &&
      definition.pairs.forksAround(node.id).every((fork) => around.has(fork)),
  );
}

/** 处理命令给的目标要是能选的一个：没给报 returnTarget: required，不能选报 returnTarget: not-upstream */
export function checkReturnTarget(definition: LoadedDefinition, state: RunState, current: string, target: string | null | undefined): string {
  if (!target?.trim()) throw invalid('returnTarget', 'required');
  if (!returnTargets(definition, state, current).some((node) => node.id === target)) throw invalid('returnTarget', 'not-upstream');
  return target;
}

/** 沿前进的连线（不算退回、提级）往回走能到的节点 */
function upstreamOf(graph: ProcessGraph, node: string): Set<string> {
  const seen = new Set<string>();
  const queue = [node];
  while (queue.length > 0) {
    for (const edge of graph.incoming(queue.shift()!)) {
      if (edge.kind === undefined && !seen.has(edge.from)) {
        seen.add(edge.from);
        queue.push(edge.from);
      }
    }
  }
  return seen;
}
