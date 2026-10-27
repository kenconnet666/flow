// 设计器的编辑：都返回新的一份定义；连线、改名、删除时把引用一起改对
import type { ProcessDefinition } from '@zerodep-dev/flow-model';
import { validateProcess } from '@zerodep-dev/flow-model';
import { describe, expect, it } from 'vitest';
import {
  addAfter,
  addNode,
  autoLayout,
  connect,
  placeNodes,
  reconnect,
  removeItems,
  replaceEdge,
  replaceNode,
  uniqueId,
  withPositions,
} from '../src/admin/process-designer/editing';

const TEXTS = { name: '审批', approve: '同意', reject: '不同意' };

const base: ProcessDefinition = {
  key: 'p',
  name: '流程',
  nodes: [
    { id: 'start', type: 'start', name: '提交' },
    { id: 'end', type: 'end', name: '结束' },
  ],
  edges: [],
};

describe('设计器的编辑', () => {
  it('加节点：标识按类型取、不重复，位置记在 layout；原来那份不变', () => {
    const first = addNode(base, 'task', TEXTS, { x: 200.4, y: 80 });
    const second = addNode(first.definition, 'task', TEXTS, { x: 400, y: 80 });
    expect([first.id, second.id]).toEqual(['task', 'task2']);
    expect(second.definition.layout?.nodes?.['task']).toEqual({ x: 200, y: 80 });
    expect(base.nodes).toHaveLength(2);
    expect(uniqueId(['a', 'a2'], 'a')).toBe('a3');
  });

  it('画出最小的流程：开始 → 审批 → 结束，发布校验通过（审批人补上之后）', () => {
    let { definition, id } = addNode(base, 'task', TEXTS, { x: 200, y: 80 });
    definition = connect(definition, 'start', id)!.definition;
    definition = connect(definition, id, 'end')!.definition;
    // 从人工节点连出：用上还没有出边的那个结果
    expect(definition.edges.find((e) => e.from === id)?.outcome).toBe('approve');
    const task = definition.nodes.find((n) => n.id === id)!;
    definition = replaceNode(definition, id, { ...task, assignees: [{ initiator: true }] } as typeof task);
    expect(validateProcess(definition).filter((p) => p.severity === 'error')).toEqual([]);
  });

  it('连不上的不连：连到自己、从结束连出、连到开始', () => {
    expect(connect(base, 'start', 'start')).toBeNull();
    expect(connect(base, 'end', 'start')).toBeNull();
    expect(connect(base, 'end', 'end')).toBeNull();
  });

  it('改接起点时去掉原来的结果；改接到开始节点不改', () => {
    let { definition, id } = addNode(base, 'task', TEXTS, { x: 0, y: 0 });
    const edge = connect(definition, id, 'end')!;
    definition = edge.definition;
    expect(reconnect(definition, edge.id, 'to', 'start')).toBe(definition);
    const moved = reconnect(definition, edge.id, 'from', 'start');
    expect(moved.edges.find((e) => e.id === edge.id)).toEqual({ id: edge.id, from: 'start', to: 'end' });
  });

  it('改节点的标识：连线、布局、"同某节点"的处理人跟着改', () => {
    let { definition, id } = addNode(base, 'task', TEXTS, { x: 10, y: 20 });
    definition = connect(definition, 'start', id)!.definition;
    const other = addNode(definition, 'task', TEXTS, { x: 30, y: 20 });
    definition = other.definition;
    const second = definition.nodes.find((n) => n.id === other.id)!;
    definition = replaceNode(definition, other.id, { ...second, assignees: [{ sameAs: id }] } as typeof second);
    const task = definition.nodes.find((n) => n.id === id)!;
    definition = replaceNode(definition, id, { ...task, id: 'review' });
    expect(definition.edges[0]).toMatchObject({ from: 'start', to: 'review' });
    expect(definition.layout?.nodes?.['review']).toEqual({ x: 10, y: 20 });
    expect(definition.layout?.nodes?.[id]).toBeUndefined();
    expect(definition.nodes.find((n) => n.id === other.id)).toMatchObject({ assignees: [{ sameAs: 'review' }] });
  });

  it('改了处理结果的标识：按这个结果走的出边跟着改', () => {
    let { definition, id } = addNode(base, 'task', TEXTS, { x: 0, y: 0 });
    definition = connect(definition, id, 'end')!.definition;
    const task = definition.nodes.find((n) => n.id === id)!;
    if (task.type !== 'task') throw new Error('not a task');
    definition = replaceNode(definition, id, { ...task, outcomes: [{ key: 'pass', label: '通过' }, ...task.outcomes.slice(1)] });
    expect(definition.edges[0]).toMatchObject({ from: id, to: 'end', outcome: 'pass' });
  });

  it('删除：连着的线一起删，开始节点不删', () => {
    let { definition, id } = addNode(base, 'task', TEXTS, { x: 0, y: 0 });
    definition = connect(definition, 'start', id)!.definition;
    definition = connect(definition, id, 'end')!.definition;
    const removed = removeItems(definition, { nodes: [id, 'start'], edges: [] });
    expect(removed.nodes.map((n) => n.id)).toEqual(['start', 'end']);
    expect(removed.edges).toEqual([]);
    expect(removed.layout?.nodes?.[id]).toBeUndefined();
  });

  it('设成默认出边时，同一个网关原来的默认出边不再是默认', () => {
    const gate = addNode(base, 'exclusive', TEXTS, { x: 0, y: 0 });
    const otherEnd = addNode(gate.definition, 'end', TEXTS, { x: 0, y: 0 });
    const a = connect(otherEnd.definition, gate.id, 'end')!;
    const b = connect(a.definition, gate.id, otherEnd.id)!;
    const edgeOf = (definition: ProcessDefinition, id: string) => definition.edges.find((e) => e.id === id)!;
    let definition = replaceEdge(b.definition, a.id, { ...edgeOf(b.definition, a.id), default: true });
    definition = replaceEdge(definition, b.id, { ...edgeOf(definition, b.id), default: true });
    expect(definition.edges.filter((e) => e.default).map((e) => e.id)).toEqual([b.id]);
  });

  it('新的人工节点带同意与不同意：不同意直接结束流程、意见必填，不用连线', () => {
    const { definition, id } = addNode(base, 'task', TEXTS, { x: 0, y: 0 });
    const task = definition.nodes.find((n) => n.id === id)!;
    expect(task.type === 'task' && task.outcomes).toEqual([
      { key: 'approve', label: '同意' },
      { key: 'reject', label: '不同意', kind: 'terminate', result: 'rejected', comment: 'required' },
    ]);
  });

  describe('加在选中的节点后面', () => {
    /** 开始 → 审批 → 结束，排好位置 */
    const chain = () => {
      const task = addNode(base, 'task', TEXTS, { x: 0, y: 0 });
      let definition = connect(task.definition, 'start', task.id)!.definition;
      definition = connect(definition, task.id, 'end')!.definition;
      return { definition: autoLayout(definition), task: task.id };
    };
    const pairs = (definition: ProcessDefinition) => definition.edges.map((e) => `${e.from}>${e.to}`).sort();
    const at = (definition: ProcessDefinition, id: string) => placeNodes(definition).find((p) => p.id === id)!;

    it('只有一条出边时插进这条线中间，后面的往右让开', () => {
      const { definition, task } = chain();
      const endBefore = at(definition, 'end').x;
      const added = addAfter(definition, task, 'task', TEXTS, 40);
      expect(pairs(added.definition)).toEqual(['start>' + task, `${added.id}>end`, `${task}>${added.id}`].sort());
      expect(at(added.definition, added.id).x).toBeGreaterThan(at(added.definition, task).x);
      expect(at(added.definition, 'end').x).toBeGreaterThan(endBefore);
    });

    it('网关后面加一条新分支：排在已有分支下面，连回结束', () => {
      const { definition, task } = chain();
      const gate = addAfter(definition, task, 'exclusive', TEXTS, 40);
      const first = addAfter(gate.definition, gate.id, 'task', TEXTS, 40);
      const second = addAfter(first.definition, gate.id, 'task', TEXTS, 40);
      const result = second.definition;
      expect(pairs(result)).toEqual(
        [`start>${task}`, `${task}>${gate.id}`, `${gate.id}>end`, `${gate.id}>${first.id}`, `${first.id}>end`, `${gate.id}>${second.id}`, `${second.id}>end`].sort(),
      );
      expect(at(result, second.id).y).toBeGreaterThan(at(result, first.id).y);
      expect(at(result, 'end').x).toBeGreaterThan(at(result, first.id).x);
      expect(validateProcess(result).filter((p) => p.code === 'edge.overlap')).toEqual([]);
    });

    it('结束节点不往后接', () => {
      const { definition } = chain();
      const added = addAfter(definition, 'end', 'task', TEXTS, 40);
      expect(added.definition.edges.filter((e) => e.from === 'end')).toEqual([]);
    });
  });

  it('位置：layout 里有的用它，没有的自动排；自动排版写回全部节点', () => {
    const moved = withPositions(base, [{ id: 'end', x: 500, y: 300 }]);
    const placed = placeNodes(moved);
    expect(placed.find((p) => p.id === 'end')).toMatchObject({ x: 500, y: 300 });
    expect(placed.find((p) => p.id === 'start')!.x).toBeLessThan(500);
    const laid = autoLayout(moved);
    expect(Object.keys(laid.layout?.nodes ?? {}).sort()).toEqual(['end', 'start']);
    expect(laid.layout?.nodes?.['end']).not.toEqual({ x: 500, y: 300 });
  });
});
