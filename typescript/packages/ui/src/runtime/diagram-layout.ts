/**
 * 流程图的自动排版：交给组件库的分层排版（layoutLayers，按步数从左往右分列），这里只定流程图自己的两件事——
 * 节点按类型的大小，以及退回、提级的线不算步数（否则会越排越远）。定义里画过布局的节点用它自己的位置。
 */
import { layoutLayers, type PlacedLayerNode } from '@zerodep-dev/vue-tsx-pro-ui/diagram';

/** 排好的一个节点：画布坐标与大小 */
export type PlacedNode = PlacedLayerNode;

/** 要排的图：实例详情的流程图（ProcessDiagram）、设计器里的定义都给得出这个形状 */
export interface LayoutInput {
  readonly nodes: readonly { readonly id: string; readonly type: string; readonly x: number | null; readonly y: number | null }[];
  readonly edges: readonly { readonly from: string; readonly to: string; readonly kind: string | null }[];
}

/** 节点的大小：开始、结束是胶囊，网关小一点，其余是卡片 */
export function sizeOf(type: string): { width: number; height: number } {
  if (type === 'start' || type === 'end') return { width: 96, height: 40 };
  if (type === 'exclusive' || type === 'parallel' || type === 'inclusive') return { width: 120, height: 40 };
  return { width: 140, height: 52 };
}

/** 排好每个节点的位置 */
export function layoutDiagram(diagram: LayoutInput): PlacedNode[] {
  return layoutLayers(
    diagram.nodes.map((n) => ({ id: n.id, x: n.x, y: n.y, ...sizeOf(n.type) })),
    diagram.edges.filter((e) => e.kind !== 'return' && e.kind !== 'escalate'),
    { roots: diagram.nodes.filter((n) => n.type === 'start').map((n) => n.id) },
  );
}
