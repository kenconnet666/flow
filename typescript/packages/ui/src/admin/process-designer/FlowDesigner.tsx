/**
 * 流程设计器（⑤）：在组件库的画布（Diagram）上画 0005 的流程定义。左边是能加的节点（点一下加在选中节点的后面并连上，
 * 没选时加在视口中间；也能拖到画布上），中间画布上拖动节点、从端口拖出连线、拖连线的端点改接，右边是选中的节点、连线
 * 或流程本身的属性，下面列出按模型包规则校验出的问题（点一条选中出问题的地方）。撤销重做、Delete 删除、自动排版。
 *
 *   let definition = _state<ProcessDefinition>(…);
 *   <FlowDesigner _value={definition} people={peopleSource} />
 *
 * 编辑的是定义对象本身（_value 双向绑定，父级宜用 _state.raw 存），位置记在 layout 里；每次改动换一份新的定义（不改原来的），撤销记的就是这一份份。
 * 父级整份换掉（载入别的版本、在 JSON 里改）时撤销历史从新的这一份重新开始。
 */
import {
  CircleStop,
  Cog,
  GitBranch,
  GitFork,
  GitMerge,
  Hourglass,
  LayoutGrid,
  Play,
  Radio,
  Redo2,
  Send,
  Trash2,
  Undo2,
  UserCheck,
  Workflow,
} from '@lucide/icons';
import { GATEWAY_TYPES, isRef, validateProcess, type AssigneeSource, type FormManifest, type ProcessDefinition, type ProcessEdge, type ProcessNode, type Problem } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import {
  Alert,
  Button,
  Card,
  createHistory,
  Flex,
  Icon,
  IconButton,
  Link,
  Select,
  Separator,
  Small,
  Splitter,
  Text,
  Toolbar,
  useRequest,
  type IconData,
  type PeopleSource,
} from '@zerodep-dev/vue-tsx-pro-ui';
import {
  Diagram,
  DiagramControls,
  DiagramNode,
  diagramInternals,
  useDiagram,
  type DiagramEdgeItem,
  type DiagramSelection,
} from '@zerodep-dev/vue-tsx-pro-ui/diagram';
import { toRaw, watch } from 'vue';
import { useFlowClient } from '../../client/flow-client';
import { useDesignerMessages } from '../../lang/process-designer-messages';
import { useFlowMessages } from '../../lang/messages';
import { addNode, autoLayout, connect, PALETTE, placeNodes, reconnect, removeItems, replaceEdge, replaceNode, withPositions, type PaletteType, type PlacedNode } from './editing';
import { EdgePanel } from './EdgePanel';
import { NodePanel } from './NodePanel';
import { ProcessPanel } from './ProcessPanel';

/** FlowDesigner 的属性 */
export interface FlowDesignerProps {
  /** 编辑的定义：父级写 _value={definition} 双向绑定 */
  _value?: ProcessDefinition;
  /** 选处理人用的组织数据；不给时按编号填 */
  people?: PeopleSource;
  /** 流程已经发布过：标识不能再改 */
  keyLocked?: boolean;
  /** 画布的高，默认 36rem */
  height?: string;
}

/** 每种节点的图标 */
const ICONS: Readonly<Record<string, IconData>> = {
  start: Play,
  task: UserCheck,
  exclusive: GitBranch,
  parallel: GitFork,
  inclusive: GitMerge,
  service: Cog,
  timer: Hourglass,
  receive: Radio,
  cc: Send,
  call: Workflow,
  end: CircleStop,
};

/** 拖进画布时 dataTransfer 里的类型 */
const DRAG_TYPE = 'application/x-flow-node-type';

/** 加在选中节点后面时隔开多远（画布坐标） */
const GAP = 60;

interface Line extends DiagramEdgeItem {
  readonly edge: ProcessEdge;
}

const EMPTY: DiagramSelection = { nodes: [], edges: [] };

/** 流程设计器 */
export const FlowDesigner = _component(({ _value: definition = _bindable<ProcessDefinition>({ key: '', name: '', nodes: [], edges: [] }), people, keyLocked = false, height = '36rem' }: FlowDesignerProps) => {
  const flow = useFlowClient();
  const messages = useDesignerMessages();
  const flowMessages = useFlowMessages();
  const controller = useDiagram();
  let selection = _state<DiagramSelection>(EMPTY);

  // 撤销重做：定义按不可变的方式换，每一步记引用就够了
  let written: ProcessDefinition = definition;
  const history = createHistory<ProcessDefinition>({
    read: () => definition,
    write: (value) => {
      written = value;
      definition = value;
    },
    clone: (value) => value,
  });
  /** 换上新的一份并记一步撤销 */
  function apply(next: ProcessDefinition, label: string, merge?: string) {
    if (next === definition) return;
    written = next;
    definition = next;
    history.commit(label, merge ? { merge } : {});
  }
  // 父级整份换掉（载入版本、改 JSON）：从新的这一份重新记
  watch(
    () => definition,
    (value) => {
      // 父级用深层的 _state 存时拿回来的是代理：按原对象比
      if (toRaw(value) === toRaw(written)) return;
      written = value;
      history.reset();
      selection = EMPTY;
    },
  );

  const forms = useRequest((signal) => flow.forms(signal));
  const processes = useRequest((signal) => flow.processes(signal));
  const manifest = useRequest(
    async (key: string | null, signal): Promise<FormManifest | null> => (key ? (JSON.parse((await flow.formDocument(key, undefined, signal)).manifest) as FormManifest) : null),
    { params: () => definition.form?.key ?? null },
  );

  /** 按模型包的规则校验（表单的字段清单、流程存不存在按拿得到的给） */
  const problems = (): Problem[] =>
    validateProcess(definition, {
      ...(manifest.data ? { form: manifest.data } : {}),
      ...(processes.data ? { processExists: (key: string) => processes.data!.some((p) => p.key === key && p.status !== 'disabled') } : {}),
    });
  const flagged = () => {
    const errors = problems().filter((p) => p.severity === 'error');
    return { nodes: new Set(errors.map((p) => p.node)), edges: new Set(errors.map((p) => p.edge)) };
  };

  const boxes = (): PlacedNode[] => placeNodes(definition);
  const lines = (): Line[] =>
    definition.edges.map((edge) => ({
      id: edge.id,
      from: edge.from,
      to: edge.to,
      edge,
      ...(edge.kind ? { dashed: true } : {}),
      ...(flagged().edges.has(edge.id) ? { tone: 'danger' as const } : {}),
    }));
  const nameOf = (id: string) => definition.nodes.find((n) => n.id === id)?.name || id;

  /** 连线上的字：线自己的 label，否则结果的名字、默认、有条件 */
  const lineText = (edge: ProcessEdge) => {
    if (edge.label) return edge.label;
    const from = definition.nodes.find((n) => n.id === edge.from);
    if (edge.outcome && from?.type === 'task') return from.outcomes.find((o) => o.key === edge.outcome)?.label ?? edge.outcome;
    if (edge.default) return messages.defaultEdge;
    return edge.outcome ?? (edge.when ? messages.condition : undefined);
  };

  /** 一个处理人来源的简短写法：记了名字用名字，否则"角色 1"这样 */
  const sourceText = (s: AssigneeSource): string => {
    if ('name' in s && s.name) return s.name;
    if ('initiator' in s) return messages.initiator;
    if ('sameAs' in s) return nameOf(s.sameAs);
    if ('resolver' in s) return s.resolver;
    const key = (['user', 'role', 'department'] as const).find((k) => k in s);
    if (!key) return '…';
    const id = (s as Record<string, unknown>)[key];
    return messages.orgEntry(key, isRef(id) ? `{${id.ref}}` : String(id));
  };

  /** 节点下面的小字：人工节点、抄送写处理人，网关只看图标，别的写种类 */
  const describe = (node: ProcessNode) => {
    const sources = node.type === 'task' ? (node.assignees ?? []) : node.type === 'cc' ? node.to : null;
    if (sources) return sources.length ? sources.map(sourceText).join('、') : messages.nodeTypes[node.type];
    if (node.type === 'start' || node.type === 'end' || GATEWAY_TYPES.has(node.type)) return undefined;
    return messages.nodeTypes[node.type];
  };

  // ───────── 编辑 ─────────

  /** 视口中间（画布坐标） */
  function viewportCenter() {
    const element = diagramInternals(controller).element.value;
    if (!element) return { x: 40, y: 40 };
    const rect = element.getBoundingClientRect();
    return controller.toDiagram(rect.left + rect.width / 2, rect.top + rect.height / 2);
  }

  /** 加一个节点：选中了一个节点时放在它右边并连上，否则放在 at 或视口中间 */
  function add(type: PaletteType, at?: { x: number; y: number }) {
    const texts = { name: messages.nodeTypes[type] ?? type, outcome: messages.defaultOutcome };
    const anchor = !at && selection.nodes.length === 1 ? boxes().find((b) => b.id === selection.nodes[0]) : undefined;
    const position = at ?? (anchor ? { x: anchor.x + anchor.width + GAP, y: anchor.y } : centered(viewportCenter()));
    let { definition: next, id } = addNode(definition, type, texts, position);
    if (anchor && anchor.node.type !== 'end') next = connect(next, anchor.id, id)?.definition ?? next;
    apply(next, messages.add(texts.name));
    selection = { nodes: [id], edges: [] };
  }
  /** 节点的中心落在 point 上时的左上角 */
  const centered = (point: { x: number; y: number }) => ({ x: point.x - 70, y: point.y - 26 });

  function removeSelected() {
    if (selection.nodes.length === 0 && selection.edges.length === 0) return;
    apply(removeItems(definition, selection), messages.remove);
    selection = EMPTY;
  }

  function onDrop(event: DragEvent) {
    const type = event.dataTransfer?.getData(DRAG_TYPE) as PaletteType | undefined;
    if (!type || !PALETTE.includes(type)) return;
    event.preventDefault();
    add(type, centered(controller.toDiagram(event.clientX, event.clientY)));
  }

  /** 画布上的快捷键：Delete 删除选中的，Ctrl / ⌘ + Z 撤销，加 Shift（或 Ctrl + Y）重做；输入框里不管 */
  function onKeydown(event: KeyboardEvent) {
    const target = event.target as HTMLElement;
    if (target.closest('input, textarea, [contenteditable="true"]')) return;
    const mod = event.ctrlKey || event.metaKey;
    if (event.key === 'Delete' || event.key === 'Backspace') {
      event.preventDefault();
      removeSelected();
    } else if (mod && event.key.toLowerCase() === 'z') {
      event.preventDefault();
      if (event.shiftKey) history.redo();
      else history.undo();
    } else if (mod && event.key.toLowerCase() === 'y') {
      event.preventDefault();
      history.redo();
    }
  }

  /** 点一条问题：选中出问题的节点或连线 */
  function focusProblem(problem: Problem) {
    if (problem.node) selection = { nodes: [problem.node], edges: [] };
    else if (problem.edge) selection = { nodes: [], edges: [problem.edge] };
  }
  const where = (p: Problem) => [p.node && nameOf(p.node), p.edge, p.lane, p.field].filter(Boolean).join(' / ');
  const textParams = (params: Problem['params']) => Object.fromEntries(Object.entries(params ?? {}).map(([k, v]) => [k, String(v)]));

  // ───────── 画面 ─────────

  const palette = () => (
    <Flex direction="column" gap="xs" role="group" aria-label={messages.palette}>
      {PALETTE.map((type) => (
        <Button
          key={type}
          variant="ghost"
          size="sm"
          icon={ICONS[type]!}
          draggable
          onDragstart={(event: DragEvent) => event.dataTransfer?.setData(DRAG_TYPE, type)}
          onClick={() => add(type)}
          aria-label={messages.add(messages.nodeTypes[type] ?? type)}
          style={{ justifyContent: 'flex-start' }}
        >
          {messages.nodeTypes[type]}
        </Button>
      ))}
    </Flex>
  );

  const canvas = () => (
    <div onDragover={(event: DragEvent) => event.preventDefault()} onDrop={onDrop} onKeydown={onKeydown}>
      <Diagram
        controller={controller}
        nodes={boxes()}
        edges={lines()}
        getNodeLabel={(box) => `${box.node.name || box.id}（${messages.nodeTypes[box.node.type] ?? box.node.type}）`}
        getEdgeLabel={(line) => `${nameOf(line.from)} → ${nameOf(line.to)}${lineText(line.edge) ? `：${lineText(line.edge)}` : ''}`}
        edgeText={(line) => lineText(line.edge)}
        selectable
        _selected={selection}
        draggable
        connectable
        canConnect={(from, to) => from.node.type !== 'end' && to.node.type !== 'start'}
        dragMode="select"
        grid="dots"
        snapToGrid
        routing="orthogonal"
        fitView
        height={height}
        overlay={<DiagramControls />}
        onNodesMove={(moves) => apply(withPositions(definition, moves), messages.steps.move)}
        onConnect={(c) => connectTo(c.from, c.to)}
        onReconnect={(change) => apply(reconnect(definition, change.edge, change.end, change.node), messages.steps.reconnect)}
        aria-label={messages.canvas}
      >
        {(box) => (
          <DiagramNode
            title={box.node.name || box.id}
            description={describe(box.node)}
            start={<Icon icon={ICONS[box.node.type] ?? Cog} />}
            shape={box.node.type === 'start' || box.node.type === 'end' ? 'pill' : 'rect'}
            align="start"
            {...(flagged().nodes.has(box.id) ? { tone: 'danger' as const } : {})}
          />
        )}
      </Diagram>
    </div>
  );

  /** 从一个节点连出去：画布上从端口拖，这里是不用拖的做法（WCAG 2.5.7），也列出已有的出边，点一条选中它 */
  function connectTo(from: string, to: string) {
    const made = connect(definition, from, to);
    if (!made) return;
    apply(made.definition, messages.steps.connect);
    selection = { nodes: [], edges: [made.id] };
  }
  const outgoing = (node: ProcessNode) => (
    <Flex direction="column" gap="xs" style={{ marginBlockStart: '1rem' }}>
      <Select
        items={definition.nodes.filter((n) => n.id !== node.id && n.type !== 'start').map((n) => ({ value: n.id, label: n.name || n.id, description: n.id }))}
        _value={null}
        searchable
        placeholder={messages.connectTo}
        aria-label={messages.connectTo}
        onUpdate:_value={(to: string | null) => to && connectTo(node.id, to)}
      />
      {definition.edges
        .filter((e) => e.from === node.id)
        .map((e) => (
          <Link key={e.id} href="#" onClick={(event: MouseEvent) => (event.preventDefault(), (selection = { nodes: [], edges: [e.id] }))}>
            → {nameOf(e.to)}
            {lineText(e) ? `（${lineText(e)}）` : ''}
          </Link>
        ))}
    </Flex>
  );

  /** 右边的属性：选中一个节点、一条连线时是它的，否则是流程的 */
  const panel = () => {
    const node = selection.nodes.length === 1 && selection.edges.length === 0 ? definition.nodes.find((n) => n.id === selection.nodes[0]) : undefined;
    const edge = selection.edges.length === 1 && selection.nodes.length === 0 ? definition.edges.find((e) => e.id === selection.edges[0]) : undefined;
    if (node) {
      return (
        <Card header={`${messages.nodePanel} · ${messages.nodeTypes[node.type] ?? node.type}`}>
          <NodePanel
            key={node.id}
            node={node}
            definition={definition}
            manifest={manifest.data ?? null}
            people={people}
            processes={processes.data ?? []}
            onChange={(next, merge) => {
              apply(replaceNode(definition, node.id, next), messages.steps.node, merge);
              if (next.id !== node.id) selection = { nodes: [next.id], edges: [] };
            }}
          />
          {node.type !== 'end' && outgoing(node)}
        </Card>
      );
    }
    if (edge) {
      return (
        <Card header={messages.edgePanel}>
          <EdgePanel
            key={edge.id}
            edge={edge}
            definition={definition}
            manifest={manifest.data ?? null}
            onChange={(next, merge) => apply(replaceEdge(definition, edge.id, next), messages.steps.edge, merge)}
          />
        </Card>
      );
    }
    return (
      <Card header={messages.processPanel}>
        <ProcessPanel
          definition={definition}
          forms={forms.data ?? []}
          manifest={manifest.data ?? null}
          keyLocked={keyLocked}
          onChange={(next, merge) => apply(next, messages.steps.process, merge)}
        />
      </Card>
    );
  };

  const problemList = () => {
    const found = problems();
    if (found.length === 0) return <Alert tone="success">{messages.noProblems}</Alert>;
    return (
      <Alert tone={found.some((p) => p.severity === 'error') ? 'danger' : 'warning'} title={`${messages.problems}（${found.length}）`}>
        <Flex direction="column" gap="xs">
          {found.map((p, i) => (
            <Text key={i}>
              {p.severity === 'error' ? '✕' : '!'}{' '}
              {p.node || p.edge ? (
                <Link href="#" onClick={(event: MouseEvent) => (event.preventDefault(), focusProblem(p))}>
                  {flowMessages.problem(p.code, textParams(p.params))}
                </Link>
              ) : (
                flowMessages.problem(p.code, textParams(p.params))
              )}
              {where(p) ? <Small>（{where(p)}）</Small> : null}
            </Text>
          ))}
        </Flex>
      </Alert>
    );
  };

  return () => (
    <Flex direction="column" gap="sm">
      <Toolbar aria-label={messages.canvas}>
        <IconButton icon={Undo2} variant="ghost" aria-label={`${messages.undo} ${history.undoLabel ?? ''}`.trim()} disabled={!history.canUndo} onClick={history.undo} />
        <IconButton icon={Redo2} variant="ghost" aria-label={`${messages.redo} ${history.redoLabel ?? ''}`.trim()} disabled={!history.canRedo} onClick={history.redo} />
        <Separator orientation="vertical" />
        <IconButton
          icon={Trash2}
          variant="ghost"
          aria-label={messages.remove}
          disabled={selection.nodes.length === 0 && selection.edges.length === 0}
          onClick={removeSelected}
        />
        <IconButton icon={LayoutGrid} variant="ghost" aria-label={messages.autoLayout} onClick={() => (apply(autoLayout(definition), messages.autoLayout), controller.fit())} />
      </Toolbar>
      <Splitter
        _size={70}
        start={
          <Flex gap="sm" align="start">
            {palette()}
            <div style={{ flex: '1 1 0', minWidth: 0 }}>{canvas()}</div>
          </Flex>
        }
        end={
          <Flex direction="column" gap="sm" style={{ paddingInlineStart: '0.5rem' }}>
            {panel()}
            {problemList()}
          </Flex>
        }
      />
    </Flex>
  );
});
