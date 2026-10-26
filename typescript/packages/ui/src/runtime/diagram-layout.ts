/**
 * 流程图的自动排版：定义里没画过布局（layout）的节点，按从开始节点走过去的步数从左往右分列，同一列从上往下排。
 * 退回、提级的线和绕回去的线（环）不算步数，否则会越排越远。画过布局的节点用它自己的位置。
 */
import type { ProcessDiagram } from '../client/types';

/** 排好的一个节点：画布坐标与大小 */
export interface PlacedNode {
  readonly id: string;
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
}

/** 列宽、行高、边距（画布坐标） */
const COLUMN = 190;
const ROW = 96;
const MARGIN = 32;

/** 节点的大小：开始、结束是胶囊，网关小一点，其余是卡片 */
export function sizeOf(type: string): { width: number; height: number } {
  if (type === 'start' || type === 'end') return { width: 96, height: 40 };
  if (type === 'exclusive' || type === 'parallel' || type === 'inclusive') return { width: 88, height: 40 };
  return { width: 140, height: 52 };
}

/** 排好每个节点的位置 */
export function layoutDiagram(diagram: ProcessDiagram): PlacedNode[] {
  const ids = diagram.nodes.map((n) => n.id);
  const forward = new Map<string, string[]>(ids.map((id) => [id, []]));
  for (const edge of diagram.edges) {
    if (edge.kind === 'return' || edge.kind === 'escalate') continue;
    forward.get(edge.from)?.push(edge.to);
  }

  // 深度优先找出绕回去的线（指向还在路上的节点），去掉它们之后是无环的
  const back = new Set<string>();
  const state = new Map<string, 'visiting' | 'done'>();
  const order: string[] = [];
  const visit = (id: string) => {
    state.set(id, 'visiting');
    for (const next of forward.get(id) ?? []) {
      if (state.get(next) === 'visiting') back.add(`${id}>${next}`);
      else if (!state.has(next)) visit(next);
    }
    state.set(id, 'done');
    order.unshift(id);
  };
  const starts = diagram.nodes.filter((n) => n.type === 'start').map((n) => n.id);
  for (const id of [...starts, ...ids]) if (!state.has(id)) visit(id);

  // 按拓扑序算最长步数：列号
  const column = new Map<string, number>(ids.map((id) => [id, 0]));
  for (const id of order) {
    for (const next of forward.get(id) ?? []) {
      if (back.has(`${id}>${next}`)) continue;
      column.set(next, Math.max(column.get(next) ?? 0, (column.get(id) ?? 0) + 1));
    }
  }

  // 同一列按深度优先的先后从上往下排，每列在最高的那列里垂直居中
  const rows = new Map<number, string[]>();
  for (const id of order) {
    const c = column.get(id) ?? 0;
    rows.set(c, [...(rows.get(c) ?? []), id]);
  }
  const tallest = Math.max(1, ...[...rows.values()].map((r) => r.length));
  const byId = new Map(diagram.nodes.map((n) => [n.id, n]));
  const placed: PlacedNode[] = [];
  for (const [c, members] of rows) {
    members.forEach((id, i) => {
      const node = byId.get(id)!;
      const size = sizeOf(node.type);
      const autoX = MARGIN + c * COLUMN + (140 - size.width) / 2;
      const autoY = MARGIN + (i + (tallest - members.length) / 2) * ROW + (52 - size.height) / 2;
      placed.push({ id, x: node.x ?? autoX, y: node.y ?? autoY, ...size });
    });
  }
  return diagram.nodes.map((n) => placed.find((p) => p.id === n.id)!);
}
