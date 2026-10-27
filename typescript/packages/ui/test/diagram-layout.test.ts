// 流程图的自动排版（算法本身在组件库的 layoutLayers 里测）：退回的线不算步数；节点按类型的大小；画过布局的节点用自己的位置
import { describe, expect, it } from 'vitest';
import type { ProcessDiagram } from '../src/client/types';
import { layoutDiagram } from '../src/runtime/diagram-layout';

const node = (id: string, type = 'task', x: number | null = null, y: number | null = null) => ({ id, name: id, type, typeLabel: null, x, y, state: 'pending' });
const edge = (id: string, from: string, to: string, kind: string | null = null) => ({ id, from, to, label: null, kind, taken: false });

describe('layoutDiagram', () => {
  it('按从开始节点走过去的步数从左往右分列；退回与绕回去的线不算', () => {
    const diagram: ProcessDiagram = {
      nodes: [node('start', 'start'), node('a'), node('b'), node('gate', 'exclusive'), node('end', 'end')],
      edges: [
        edge('e1', 'start', 'a'),
        edge('e2', 'a', 'b'),
        edge('e3', 'b', 'gate'),
        edge('loop', 'gate', 'a'),
        edge('back', 'b', 'a', 'return'),
        edge('e4', 'gate', 'end'),
      ],
    };
    const xs = Object.fromEntries(layoutDiagram(diagram).map((p) => [p.id, p.x + p.width / 2]));
    expect(xs['start']! < xs['a']!).toBe(true);
    expect(xs['a']! < xs['b']!).toBe(true);
    expect(xs['b']! < xs['gate']!).toBe(true);
    expect(xs['gate']! < xs['end']!).toBe(true);
  });

  it('分叉的几条分支在同一列上下排开；画过布局的节点用自己的位置', () => {
    const diagram: ProcessDiagram = {
      nodes: [node('start', 'start'), node('left'), node('right'), node('fixed', 'task', 500, 20)],
      edges: [edge('e1', 'start', 'left'), edge('e2', 'start', 'right'), edge('e3', 'left', 'fixed')],
    };
    const placed = Object.fromEntries(layoutDiagram(diagram).map((p) => [p.id, p]));
    expect(placed['left']!.x).toBe(placed['right']!.x);
    expect(placed['left']!.y).not.toBe(placed['right']!.y);
    expect(placed['fixed']).toMatchObject({ x: 500, y: 20 });
  });
});
