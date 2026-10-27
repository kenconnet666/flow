/**
 * 设计器对流程定义（0005）的编辑：都是纯函数，传进一份定义、返回改好的新一份，不改原来的——撤销重做只要记下每一份
 * （组件库的 createHistory，clone 写成按引用）。节点的位置记在定义的 layout 里（0005 的"布局单独放"）。
 *
 *   let { definition, id } = addNode(definition, 'task', { name: '审批', outcome: '同意' }, { x: 200, y: 80 });
 *   definition = connect(definition, 'start', id).definition;
 */
import { GATEWAY_TYPES, isBranching, type Outcome, type ProcessDefinition, type ProcessEdge, type ProcessNode } from '@zerodep-dev/flow-model';
import { layoutDiagram, sizeOf } from '../../runtime/parts/diagram-layout';

/** 设计器的工具栏里能加的节点（开始节点每个流程有且只有一个，不在里面） */
export const PALETTE = ['task', 'exclusive', 'parallel', 'inclusive', 'service', 'timer', 'receive', 'cc', 'call', 'end'] as const;

/** 能加的节点类型 */
export type PaletteType = (typeof PALETTE)[number];

/** 能加的节点：工具栏里的内置类型，或接入方注册的自定义节点（x-名字，扩展目录里列出的） */
export type NewNodeType = PaletteType | `x-${string}`;

/** 新节点的文字：节点名，人工节点默认的一个处理结果的名字 */
export interface NewNodeTexts {
  readonly name: string;
  /** 人工节点默认的两个结果：同意往下走，不同意直接结束流程（结果 rejected） */
  readonly approve: string;
  readonly reject: string;
}

/** 画布上的一个节点：定义里的节点加上位置与大小 */
export interface PlacedNode {
  readonly id: string;
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
  readonly node: ProcessNode;
}

/** 选中的节点与连线 */
export interface Selection {
  readonly nodes: readonly string[];
  readonly edges: readonly string[];
}

/** 一个没用过的标识：prefix 本身，或者后面加 2、3…… */
export function uniqueId(taken: Iterable<string>, prefix: string): string {
  const used = new Set(taken);
  if (!used.has(prefix)) return prefix;
  for (let n = 2; ; n++) if (!used.has(`${prefix}${n}`)) return `${prefix}${n}`;
}

/** 一个新节点：带上这种节点必填的部分（人工节点一个处理结果，自动节点空的动作名……），发布前在属性面板里补齐 */
export function newNode(type: NewNodeType, id: string, texts: NewNodeTexts): ProcessNode {
  const base = { id, name: texts.name };
  switch (type) {
    case 'task':
      return { ...base, type, assignees: [], outcomes: defaultOutcomes(texts) };
    case 'service':
      return { ...base, type, action: '' };
    case 'timer':
      return { ...base, type, wait: { hours: 1 } };
    case 'receive':
      return { ...base, type, event: '' };
    case 'cc':
      return { ...base, type, to: [] };
    case 'call':
      return { ...base, type, process: '' };
    default:
      return { ...base, type } as ProcessNode;
  }
}

/** 人工节点默认的结果：没有"不同意"的审批几乎用不上，新画的节点直接带上，不要的在属性面板里删 */
export function defaultOutcomes(texts: Pick<NewNodeTexts, 'approve' | 'reject'>): Outcome[] {
  return [
    { key: 'approve', label: texts.approve },
    { key: 'reject', label: texts.reject, kind: 'terminate', result: 'rejected', comment: 'required' },
  ];
}

/** 节点放在 at（画布坐标，节点的左上角） */
export function addNode(
  definition: ProcessDefinition,
  type: NewNodeType,
  texts: NewNodeTexts,
  at: { x: number; y: number },
): { definition: ProcessDefinition; id: string } {
  const id = uniqueId(
    definition.nodes.map((n) => n.id),
    type,
  );
  const next = { ...definition, nodes: [...definition.nodes, newNode(type, id, texts)] };
  return { definition: withPositions(next, [{ id, x: at.x, y: at.y }]), id };
}

/**
 * 加在一个节点后面（设计器里选中一个节点，再点左边的节点类型）：
 * - 它不是网关、只有一条出边时插进这条线中间：原来的线改连到新节点（结果、条件照旧），新节点再连到原来的终点（新的是结束节点时不连），
 *   原来在它右边的节点往右让出新节点的位置；
 * - 网关后面加的是一条新分支：新节点放在已有分支的下方，不叠在一起；已有分支直接通向结束或汇合的网关时，新分支也连过去；
 * - 别的节点没有出边、或有几条时同网关，新开一条。
 */
export function addAfter(
  definition: ProcessDefinition,
  anchorId: string,
  type: NewNodeType,
  texts: NewNodeTexts,
  gap: number,
): { definition: ProcessDefinition; id: string } {
  const boxes = placeNodes(definition);
  const anchor = boxes.find((b) => b.id === anchorId)!;
  const size = sizeOf(type);
  const outgoing = definition.edges.filter((e) => e.from === anchorId);
  const x = anchor.x + anchor.width + gap;
  const middle = anchor.y + (anchor.height - size.height) / 2;
  if (outgoing.length === 1 && !GATEWAY_TYPES.has(anchor.node.type)) {
    const line = outgoing[0]!;
    const added = addNode(definition, type, texts, { x, y: middle });
    const pushed = boxes.filter((b) => b.id !== anchorId && b.x >= x).map((b) => ({ id: b.id, x: b.x + size.width + gap, y: b.y }));
    let next = withPositions(added.definition, pushed);
    next = { ...next, edges: next.edges.map((e) => (e.id === line.id ? { ...e, to: added.id } : e)) };
    if (type !== 'end') next = connect(next, added.id, line.to)?.definition ?? next;
    return { definition: next, id: added.id };
  }
  const targets = boxes.filter((b) => outgoing.some((e) => e.to === b.id));
  const y = targets.length ? Math.max(...targets.map((b) => b.y + b.height)) + gap / 2 : middle;
  const added = addNode(definition, type, texts, { x, y });
  let next = connect(added.definition, anchorId, added.id)?.definition ?? added.definition;
  const rejoin = targets.find((b) => b.node.type === 'end' || GATEWAY_TYPES.has(b.node.type));
  if (rejoin && type !== 'end') {
    next = connect(next, added.id, rejoin.id)?.definition ?? next;
    // 连回去的那个节点挡在新分支这一列时，它和它右边的往右让开，线不用往回绕
    if (rejoin.x < x + size.width + gap) {
      const shift = x + size.width + gap - rejoin.x;
      next = withPositions(next, boxes.filter((b) => b.x >= rejoin.x && b.id !== anchorId).map((b) => ({ id: b.id, x: b.x + shift, y: b.y })));
    }
  }
  return { definition: next, id: added.id };
}

/**
 * 连一条线。从按结果分出边的节点（人工、自动……）连出时，先用还没有出边的第一个结果；起点是结束节点、终点是开始节点、
 * 连到自己时不连（返回 null）
 */
export function connect(definition: ProcessDefinition, from: string, to: string): { definition: ProcessDefinition; id: string } | null {
  const source = definition.nodes.find((n) => n.id === from);
  const target = definition.nodes.find((n) => n.id === to);
  if (!source || !target || from === to || source.type === 'end' || target.type === 'start') return null;
  const id = uniqueId(
    definition.edges.map((e) => e.id),
    `${from}-${to}`,
  );
  const outcome = isBranching(source.type) ? unusedOutcome(definition, source) : undefined;
  const edge: ProcessEdge = { id, from, to, ...(outcome ? { outcome } : {}) };
  return { definition: { ...definition, edges: [...definition.edges, edge] }, id };
}

/** 节点上写的、还没有出边的第一个结果 */
function unusedOutcome(definition: ProcessDefinition, node: ProcessNode): string | undefined {
  const used = new Set(definition.edges.filter((e) => e.from === node.id).map((e) => e.outcome));
  return outcomeKeys(node).find((key) => !used.has(key));
}

/** 节点上能选的结果：人工节点的 outcomes，等事件的 outcomes */
export function outcomeKeys(node: ProcessNode): string[] {
  if (node.type === 'task') return node.outcomes.map((o) => o.key);
  if (node.type === 'receive') return [...(node.outcomes ?? [])];
  return [];
}

/** 连线的一端改接到别的节点；接不上（同 connect 的规则）时原样返回 */
export function reconnect(definition: ProcessDefinition, edgeId: string, end: 'from' | 'to', node: string): ProcessDefinition {
  const edge = definition.edges.find((e) => e.id === edgeId);
  const other = edge?.[end === 'from' ? 'to' : 'from'];
  const moved = definition.nodes.find((n) => n.id === node);
  if (!edge || !moved || node === other) return definition;
  if ((end === 'from' && moved.type === 'end') || (end === 'to' && moved.type === 'start')) return definition;
  // 换了起点：原来的结果属于原来的节点，去掉
  const changed: ProcessEdge = end === 'from' ? withoutOutcome({ ...edge, from: node }) : { ...edge, to: node };
  return { ...definition, edges: definition.edges.map((e) => (e.id === edgeId ? changed : e)) };
}

const withoutOutcome = ({ outcome: _, ...rest }: ProcessEdge): ProcessEdge => rest;

/** 节点的新位置写进 layout */
export function withPositions(definition: ProcessDefinition, moves: readonly { id: string; x: number; y: number }[]): ProcessDefinition {
  const nodes = { ...definition.layout?.nodes };
  for (const { id, x, y } of moves) nodes[id] = { x: Math.round(x), y: Math.round(y) };
  return { ...definition, layout: { ...definition.layout, nodes } };
}

/** 删掉选中的节点（连着它们的线一起）与连线；开始节点不删 */
export function removeItems(definition: ProcessDefinition, selection: Selection): ProcessDefinition {
  const nodes = new Set(selection.nodes.filter((id) => definition.nodes.find((n) => n.id === id)?.type !== 'start'));
  const edges = new Set(selection.edges);
  const layoutNodes = Object.fromEntries(Object.entries(definition.layout?.nodes ?? {}).filter(([id]) => !nodes.has(id)));
  return {
    ...definition,
    nodes: definition.nodes.filter((n) => !nodes.has(n.id)),
    edges: definition.edges.filter((e) => !edges.has(e.id) && !nodes.has(e.from) && !nodes.has(e.to)),
    layout: { ...definition.layout, nodes: layoutNodes },
  };
}

/**
 * 换掉一个节点（属性面板改的）。标识变了时连线、布局、"与某节点相同"的处理人一起改过去；
 * 人工节点改了某个结果的标识（同一个位置上的结果换了 key）时，从它连出、按这个结果走的线跟着改
 */
export function replaceNode(definition: ProcessDefinition, id: string, node: ProcessNode): ProcessDefinition {
  const renamed = node.id !== id;
  const rename = (value: string) => (renamed && value === id ? node.id : value);
  const outcomes = outcomeRenames(definition.nodes.find((n) => n.id === id), node);
  const nodes = definition.nodes.map((n) => {
    const current = n.id === id ? node : n;
    if (!renamed || current.type !== 'task' || !current.assignees?.some((s) => 'sameAs' in s && s.sameAs === id)) return current;
    return { ...current, assignees: current.assignees.map((s) => ('sameAs' in s ? { ...s, sameAs: rename(s.sameAs) } : s)) };
  });
  if (!renamed && outcomes.size === 0) return { ...definition, nodes };
  const edges = definition.edges.map((e) => {
    if (e.from !== id && e.to !== id) return e;
    const outcome = e.from === id && e.outcome !== undefined ? (outcomes.get(e.outcome) ?? e.outcome) : e.outcome;
    return compactEdge({ ...e, from: rename(e.from), to: rename(e.to), outcome });
  });
  const layoutNodes = Object.fromEntries(Object.entries(definition.layout?.nodes ?? {}).map(([key, at]) => [rename(key), at]));
  return { ...definition, nodes, edges, layout: { ...definition.layout, nodes: layoutNodes } };
}

/** 人工节点前后两版同一位置上换了 key 的结果：旧 key → 新 key */
function outcomeRenames(before: ProcessNode | undefined, after: ProcessNode): Map<string, string> {
  const renames = new Map<string, string>();
  if (before?.type !== 'task' || after.type !== 'task' || before.outcomes.length !== after.outcomes.length) return renames;
  before.outcomes.forEach((o, i) => {
    const key = after.outcomes[i]!.key;
    if (o.key !== key) renames.set(o.key, key);
  });
  return renames;
}

const compactEdge = (edge: ProcessEdge): ProcessEdge => (edge.outcome === undefined ? withoutOutcome(edge) : edge);

/**
 * 换掉一条连线（属性面板改的）。改成排他、包容网关的默认出边时，同一个网关的其余出边不再是默认
 */
export function replaceEdge(definition: ProcessDefinition, id: string, edge: ProcessEdge): ProcessDefinition {
  return {
    ...definition,
    edges: definition.edges.map((e) => {
      if (e.id === id) return edge;
      if (edge.default && e.from === edge.from && e.default) {
        const { default: _, ...rest } = e;
        return rest;
      }
      return e;
    }),
  };
}

/**
 * 每个节点在画布上的位置：layout 里记着的用它，没有的按步数自动排（同只读的流程图）
 */
export function placeNodes(definition: ProcessDefinition): PlacedNode[] {
  const at = definition.layout?.nodes ?? {};
  const placed = layoutDiagram({
    nodes: definition.nodes.map((n) => ({ id: n.id, type: n.type, x: at[n.id]?.x ?? null, y: at[n.id]?.y ?? null })),
    edges: definition.edges.map((e) => ({ from: e.from, to: e.to, kind: e.kind ?? null })),
  });
  return definition.nodes.map((node, i) => ({ ...placed[i]!, node }));
}

/** 全部重新自动排：去掉记着的位置，按步数排好后写回 layout */
export function autoLayout(definition: ProcessDefinition): ProcessDefinition {
  const { layout, ...rest } = definition;
  const fresh = placeNodes(rest);
  return withPositions({ ...rest, ...(layout?.edges ? { layout: { edges: layout.edges } } : {}) }, fresh);
}
