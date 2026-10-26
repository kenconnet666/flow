/**
 * 流程图（只读）：实例按的那个版本的节点与连线，走过的节点与连线是绿的，正停着的节点是强调色，没到的是灰的；
 * 退回、提级的线画成虚线。定义里没画布局时自动排（diagram-layout.ts）。建在组件库的 Diagram 上，能平移、缩放。
 *
 *   <FlowDiagram diagram={detail.diagram} />
 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import type { StatusTone } from '@zerodep-dev/vue-tsx-pro-ui';
import { Diagram, DiagramNode, type DiagramEdgeItem, type DiagramNodeItem } from '@zerodep-dev/vue-tsx-pro-ui/diagram';
import type { ProcessDiagram } from '../client/types';
import { useFlowMessages } from '../lang/messages';
import { layoutDiagram } from './diagram-layout';

/** FlowDiagram 的属性 */
export interface FlowDiagramProps {
  /** 实例详情里的流程图 */
  diagram: ProcessDiagram;
  /** 画布的高度，默认 20rem */
  height?: string;
}

interface Box extends DiagramNodeItem {
  name: string;
  type: string;
  state: string;
}

interface Line extends DiagramEdgeItem {
  label: string | null;
}

const NODE_TONES: Readonly<Record<string, StatusTone | undefined>> = { done: 'success', current: 'accent' };

/** 流程图 */
export const FlowDiagram = _component(({ diagram, height = '20rem' }: FlowDiagramProps) => {
  const messages = useFlowMessages();

  const boxes = (): Box[] => {
    const placed = layoutDiagram(diagram);
    return diagram.nodes.map((node, i) => ({ ...placed[i]!, name: node.name ?? node.id, type: node.type, state: node.state }));
  };
  const lines = (): Line[] =>
    diagram.edges.map((edge) => ({
      id: edge.id,
      from: edge.from,
      to: edge.to,
      label: edge.label,
      ...(edge.taken ? { tone: 'success' as const } : {}),
      ...(edge.kind === 'return' || edge.kind === 'escalate' ? { dashed: true } : {}),
    }));
  const nameOf = (id: string) => diagram.nodes.find((n) => n.id === id)?.name ?? id;

  return () => (
    <Diagram
      nodes={boxes()}
      edges={lines()}
      getNodeLabel={(box) => `${box.name}（${messages.nodeState[box.state] ?? box.state}）`}
      getEdgeLabel={(line) => `${nameOf(line.from)} → ${nameOf(line.to)}${line.label ? `：${line.label}` : ''}`}
      edgeText={(line) => line.label ?? undefined}
      routing="orthogonal"
      fitView
      height={height}
      aria-label={messages.diagram}
    >
      {(box) => (
        <DiagramNode
          title={box.name}
          {...(box.type === 'task' || box.type === 'start' || box.type === 'end' ? {} : { description: messages.nodeType[box.type] ?? box.type })}
          shape={box.type === 'start' || box.type === 'end' ? 'pill' : 'rect'}
          {...(NODE_TONES[box.state] ? { tone: NODE_TONES[box.state] } : {})}
          dashed={box.state === 'pending'}
        />
      )}
    </Diagram>
  );
});
