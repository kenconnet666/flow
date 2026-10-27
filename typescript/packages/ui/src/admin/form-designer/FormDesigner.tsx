/**
 * 表单设计器（0004 的 2、3）：左边是能拖的组件与字段列表，中间画布照常渲染表单，右边是选中的组件、字段的属性。
 *
 *   <FormDesigner source={source} onEdit={(edits, merge) => editor.edit(edits, { merge })} onUndo={editor.undo} onRedo={editor.redo} />
 *
 * 源码是唯一的状态：字段列表、组件树、属性面板都是每次从源码解析出来的（form-tools 的 parseDesign），设计操作算出要改的几段源码
 * 交给 onEdit——表单编辑器把它派发进代码编辑器，撤销重做就是代码编辑器的历史，只有一个栈。
 * 画布：设计时编译的源码给每个节点加 data-form-node（路径），上面盖一层透明的操作层，点击、拖放都由它处理，
 * 选中框、插入线按 data-form-node 元素的位置画；预览时去掉操作层，表单照常能填。源码有语法错误时停在上次能解析的结果，不能设计。
 */
import { Eye, PenTool, Plus, Redo2, Trash2, Undo2 } from '@lucide/icons';
import type { FieldDefinition, FieldType, FormManifest } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import {
  Alert,
  Button,
  Card,
  Flex,
  IconButton,
  Separator,
  Small,
  Splitter,
  Text,
  ToggleGroup,
  ToggleItem,
  Toolbar,
  useConfirm,
  type PeopleSource,
} from '@zerodep-dev/vue-tsx-pro-ui';
import { computed, onBeforeUnmount, onMounted, watch } from 'vue';
import {
  addField,
  allNodes,
  controlsFor,
  designSource,
  ensureImport,
  findNode,
  formComponents,
  insertNode,
  moveNode,
  parentOf,
  parseDesign,
  printComponent,
  printField,
  removeField,
  removeNode,
  UI_MODULE,
  type DesignNode,
  type FormComponent,
  type FormDesign,
  type TextEdit,
} from '../../form-tools';
import { FlowForm } from '../../form/FlowForm';
import { useFormDesignerMessages } from '../../lang/form-designer-messages';
import { PropertyPanel } from './PropertyPanel';

/** FormDesigner 的属性 */
export interface FormDesignerProps {
  /** 表单源码 */
  source: string;
  /** 改源码（位置按改之前的）；merge 为 true 时与紧挨着的上一次修改合成一步撤销 */
  onEdit: (edits: readonly TextEdit[], merge?: boolean) => void;
  onUndo?: () => void;
  onRedo?: () => void;
  /** 画布里人员字段的数据来源 */
  people?: PeopleSource;
  /** 表单代码还能导入的模块（业务组件），同 FlowForm */
  modules?: Readonly<Record<string, unknown>>;
}

/** 面板里能拖的一样东西 */
type Item =
  | { readonly kind: 'component'; readonly component: FormComponent }
  | { readonly kind: 'field'; readonly type: FieldType; readonly control: FormComponent; readonly label: string }
  | { readonly kind: 'place'; readonly name: string };

/** 正在拖的：面板里的一样东西，或画布上的一个节点 */
type Drag = Item | { readonly kind: 'move'; readonly path: string };

/** 放下的位置：容器与第几个子节点之前，加上画插入线的位置（相对画布） */
interface Drop {
  readonly parent: string;
  readonly index: number;
  readonly line: Rect;
}

interface Rect {
  readonly left: number;
  readonly top: number;
  readonly width: number;
  readonly height: number;
}

/** 面板里的字段：类型与默认控件（同一类型的第二个控件单列，如多行文字） */
const FIELD_ITEMS: readonly { type: FieldType; control: string }[] = [
  { type: 'text', control: 'TextField' },
  { type: 'text', control: 'Textarea' },
  { type: 'number', control: 'NumberField' },
  { type: 'decimal', control: 'NumberField' },
  { type: 'boolean', control: 'Switch' },
  { type: 'date', control: 'DatePicker' },
  { type: 'datetime', control: 'DatePicker' },
  { type: 'select', control: 'Select' },
  { type: 'people', control: 'PeoplePicker' },
];

const BUILTIN_GROUPS = new Set(['layout', 'display', 'field']);

/** 表单设计器 */
export const FormDesigner = _component(({ source, onEdit, onUndo, onRedo, people, modules }: FormDesignerProps) => {
  const messages = useFormDesignerMessages();
  const confirm = useConfirm();
  let design = _state.raw<FormDesign | null>(null);
  let syntax = _state<string | null>(null);
  let mode = _state<'design' | 'preview'>('design');
  let selected = _state<string | null>(null);
  let drag = _state.raw<Drag | null>(null);
  let drop = _state.raw<Drop | null>(null);
  let boxes = _state.raw<Rect[]>([]);
  let canvas = _state.raw<HTMLElement | null>(null);

  // 每次源码变了重新解析；有语法错误时停在上次的结果
  watch(
    () => source,
    (code) => {
      try {
        design = parseDesign(code);
        syntax = null;
        if (selected && !findNode(design.root, selected)) selected = null;
      } catch (error) {
        syntax = (error as Error).message;
      }
    },
    { immediate: true },
  );

  const designing = () => mode === 'design' && syntax === null && design?.root != null;

  /** 画布上渲染的表单：设计时加上 data-form-node；清单读不全时不渲染 */
  const view = computed(() => {
    if (!design || design.fields.some((f) => !f.definition)) return null;
    const manifest: FormManifest = Object.fromEntries(design.fields.map((f) => [f.name, f.definition!]));
    return {
      manifest,
      values: {},
      access: { default: 'editable' as const },
      start: true,
      compiled: null,
      source: mode === 'design' ? designSource(source, design) : source,
    };
  });

  // ───────── 操作 ─────────

  /** 新放进来的东西：要改的源码与放好后的字段名 */
  function creation(item: Item, parent: string, index: number): TextEdit[] {
    const current = design!;
    if (item.kind === 'component') {
      const imported = ensureImport(source, current, item.component.module, item.component.name);
      return [...imported.edits, ...insertNode(source, current, parent, index, printComponent(item.component, imported.local))];
    }
    const name = item.kind === 'place' ? item.name : newFieldName(current);
    const definition = item.kind === 'place' ? current.fields.find((f) => f.name === name)?.definition : newField(item.type, current, messages);
    const control = item.kind === 'field' ? item.control : definition && controlsFor(definition)[0];
    if (!definition || !control) return [];
    const imported = ensureImport(source, current, control.module, control.name);
    return [
      ...(item.kind === 'field' ? addField(source, current, name, definition) : []),
      ...imported.edits,
      ...insertNode(source, current, parent, index, printField(name, control, imported.local)),
    ];
  }

  /** 放下：新放进来的或挪过来的，放好后选中它 */
  function place(what: Drag, parent: string, index: number) {
    if (!design) return;
    const edits = what.kind === 'move' ? moveNode(source, design, what.path, parent, index) : creation(what, parent, index);
    if (edits.length === 0) return;
    onEdit(edits);
    selected = what.kind === 'move' ? afterRemoval(`${parent}.${index}`, what.path) : `${parent}.${index}`;
  }

  /** 点面板里的一样东西（不用拖的做法）：放进选中的容器的最后，或选中节点的后面，没选时放在表单的最后 */
  function add(item: Item) {
    const root = design?.root;
    if (!root || !designing()) return;
    const node = selected ? findNode(root, selected) : null;
    if (node?.container) return place(item, node.path, node.children.length);
    const at = node ? parentOf(root, node.path) : null;
    if (at) return place(item, at.parent.path, at.index + 1);
    place(item, root.path, root.children.length);
  }

  function removeSelected() {
    if (!design || !selected) return;
    onEdit(removeNode(source, design, selected));
    selected = null;
  }

  async function deleteField(name: string, label: string) {
    if (!design || !(await confirm({ title: messages.removeField, description: messages.removeFieldConfirm(label), tone: 'danger' }))) return;
    // 画布上摆着的一起删（各自的区间不重叠）
    const placements = allNodes(design.root).filter((n) => n.field?.name === name);
    onEdit([...removeField(source, design, name), ...placements.flatMap((n) => removeNode(source, design!, n.path))]);
    selected = null;
  }

  function onKeydown(event: KeyboardEvent) {
    if (!designing() || (event.target as HTMLElement).closest('input, textarea, [contenteditable]')) return;
    const mod = event.ctrlKey || event.metaKey;
    if ((event.key === 'Delete' || event.key === 'Backspace') && selected && selected !== design?.root?.path) {
      event.preventDefault();
      removeSelected();
    } else if (mod && event.key.toLowerCase() === 'z') {
      event.preventDefault();
      (event.shiftKey ? onRedo : onUndo)?.();
    } else if (mod && event.key.toLowerCase() === 'y') {
      event.preventDefault();
      onRedo?.();
    }
  }

  // ───────── 画布上的位置 ─────────

  /** 一个节点在画布上的框（代码块可能有几个元素，取并起来的） */
  function rectOf(path: string): Rect | null {
    if (!canvas) return null;
    const elements = [...canvas.querySelectorAll<HTMLElement>(`[data-form-node="${path}"]`)];
    if (elements.length === 0) return null;
    const origin = canvas.getBoundingClientRect();
    const rects = elements.map((e) => e.getBoundingClientRect()).filter((r) => r.width > 0 || r.height > 0);
    if (rects.length === 0) return null;
    const left = Math.min(...rects.map((r) => r.left));
    const top = Math.min(...rects.map((r) => r.top));
    const right = Math.max(...rects.map((r) => r.right));
    const bottom = Math.max(...rects.map((r) => r.bottom));
    return { left: left - origin.left, top: top - origin.top, width: right - left, height: bottom - top };
  }

  /** 指针下面最里层的节点（操作层下面的元素） */
  function nodeAt(x: number, y: number): string | null {
    for (const element of document.elementsFromPoint(x, y)) {
      if (!canvas?.contains(element) || element.closest('[data-designer-overlay]')) continue;
      const hit = element.closest<HTMLElement>('[data-form-node]');
      if (hit && canvas.contains(hit)) return hit.dataset.formNode ?? null;
    }
    return null;
  }

  /** 指针在哪放下：容器里按子节点的位置排，别的节点放它前面或后面（按上下半） */
  function dropAt(x: number, y: number): Drop | null {
    const root = design?.root;
    if (!root || !canvas) return null;
    const origin = canvas.getBoundingClientRect();
    const py = y - origin.top;
    const path = nodeAt(x, y) ?? root.path;
    const node = findNode(root, path) ?? root;
    if (node.container) return dropInside(node, py);
    const at = parentOf(root, node.path);
    const rect = rectOf(node.path);
    if (!at || !rect) return dropInside(root, py);
    const after = py > rect.top + rect.height / 2;
    const index = at.index + (after ? 1 : 0);
    return { parent: at.parent.path, index, line: lineAt(rect, after) };
  }

  function dropInside(container: DesignNode, py: number): Drop | null {
    const rects = container.children.map((c) => rectOf(c.path));
    const index = rects.findIndex((r) => r !== null && py < r.top + r.height / 2);
    const own = rectOf(container.path);
    if (!own) return null;
    if (index >= 0) return { parent: container.path, index, line: lineAt(rects[index]!, false) };
    const last = [...rects].reverse().find((r) => r !== null);
    return {
      parent: container.path,
      index: container.children.length,
      line: last ? lineAt(last, true) : { left: own.left + 8, top: own.top + own.height - 6, width: Math.max(own.width - 16, 24), height: 2 },
    };
  }

  const lineAt = (rect: Rect, after: boolean): Rect => ({ left: rect.left, top: after ? rect.top + rect.height + 2 : rect.top - 3, width: rect.width, height: 2 });

  // 画布的内容（重新编译、窗口变化）变了时重量选中框
  let observer: MutationObserver | undefined;
  let resize: ResizeObserver | undefined;
  let frame = 0;
  const measure = () => {
    cancelAnimationFrame(frame);
    frame = requestAnimationFrame(() => {
      const box = selected ? rectOf(selected) : null;
      boxes = box ? [box] : [];
    });
  };
  onMounted(() => {
    observer = new MutationObserver(measure);
    resize = new ResizeObserver(measure);
    if (canvas) {
      observer.observe(canvas, { subtree: true, childList: true, attributes: true });
      resize.observe(canvas);
    }
  });
  onBeforeUnmount(() => {
    observer?.disconnect();
    resize?.disconnect();
    cancelAnimationFrame(frame);
  });
  watch(() => [selected, mode], measure);

  // ───────── 操作层的事件 ─────────

  const overlayEvents = {
    onClick: (event: MouseEvent) => {
      selected = nodeAt(event.clientX, event.clientY);
      (event.currentTarget as HTMLElement).focus();
    },
    onDragstart: (event: DragEvent) => {
      const path = nodeAt(event.clientX, event.clientY);
      if (!path || path === design?.root?.path) return event.preventDefault();
      selected = path;
      drag = { kind: 'move', path };
      event.dataTransfer?.setData('text/plain', path);
    },
    onDragover: (event: DragEvent) => {
      if (!drag) return;
      event.preventDefault();
      drop = dropAt(event.clientX, event.clientY);
    },
    onDragleave: () => (drop = null),
    onDrop: (event: DragEvent) => {
      event.preventDefault();
      const target = dropAt(event.clientX, event.clientY);
      if (drag && target) place(drag, target.parent, target.index);
      drag = null;
      drop = null;
    },
    onDragend: () => {
      drag = null;
      drop = null;
    },
  };

  const startDrag = (item: Item) => (event: DragEvent) => {
    drag = item;
    event.dataTransfer?.setData('text/plain', item.kind === 'place' ? item.name : item.kind);
  };

  // ───────── 画面 ─────────

  const paletteButton = (item: Item, label: string, key: string) => (
    <Button
      key={key}
      variant="ghost"
      size="sm"
      draggable
      disabled={!designing()}
      onDragstart={startDrag(item)}
      onDragend={overlayEvents.onDragend}
      onClick={() => add(item)}
      style={{ justifyContent: 'flex-start' }}
    >
      {label}
    </Button>
  );

  const palette = () => {
    const components = formComponents();
    const groups = [...new Set(components.map((c) => c.group))];
    const fieldItems: { item: Item; label: string; key: string }[] = FIELD_ITEMS.flatMap(({ type, control }, i) => {
      const component = components.find((c) => c.module === UI_MODULE && c.name === control);
      if (!component) return [];
      const label = FIELD_ITEMS.findIndex((f) => f.type === type) === i ? (messages.fieldTypes[type] ?? type) : component.label;
      return [{ item: { kind: 'field', type, control: component, label }, label, key: `${type}:${control}` }];
    });
    const businessFields = components
      .filter((c) => !BUILTIN_GROUPS.has(c.group) && c.field?.length)
      .map((c) => ({ item: { kind: 'field', type: c.field![0]!, control: c, label: c.label } as Item, label: c.label, key: `${c.module}#${c.name}` }));
    return (
      <Flex direction="column" gap="sm" role="group" aria-label={messages.palette} style={{ minWidth: '9rem' }}>
        {groups.map((group) => {
          const items =
            group === 'field'
              ? fieldItems
              : components
                  .filter((c) => c.group === group && !c.field)
                  .map((c) => ({ item: { kind: 'component', component: c } as Item, label: c.label, key: `${c.module}#${c.name}` }));
          const all = group === 'field' ? items : [...items, ...businessFields.filter((b) => (b.item as { control: FormComponent }).control.group === group)];
          if (all.length === 0) return null;
          return (
            <Flex key={group} direction="column" gap="xs" role="group" aria-label={messages.groups[group] ?? group}>
              <Small tone="muted">{messages.groups[group] ?? group}</Small>
              {all.map(({ item, label, key }) => paletteButton(item, label, key))}
            </Flex>
          );
        })}
      </Flex>
    );
  };

  /** 字段列表：没摆的能拖进画布；点摆着的选中它 */
  const fieldList = () => {
    if (!design) return null;
    const current = design;
    return (
      <Card header={messages.fields}>
        <Flex direction="column" gap="xs">
          {current.fields.map((field) => {
            const label = field.definition?.label ?? field.name;
            const placedAt = allNodes(current.root).find((n) => n.field?.name === field.name);
            return (
              <Flex key={field.name} gap="xs" align="center">
                {placedAt ? (
                  <Button variant="ghost" size="sm" style={{ flex: '1 1 auto', justifyContent: 'flex-start' }} onClick={() => (selected = placedAt.path)}>
                    {label}
                  </Button>
                ) : (
                  paletteButton({ kind: 'place', name: field.name }, `${label} · ${messages.unplaced}`, field.name)
                )}
                <IconButton icon={Trash2} variant="ghost" size="sm" aria-label={`${messages.removeField} ${label}`} disabled={!designing()} onClick={() => void deleteField(field.name, label)} />
              </Flex>
            );
          })}
          <Button
            variant="outline"
            size="sm"
            icon={Plus}
            disabled={!designing()}
            onClick={() => design && onEdit(addField(source, design, newFieldName(design), newField('text', design, messages)))}
          >
            {messages.addField}
          </Button>
        </Flex>
      </Card>
    );
  };

  const warnings = () => {
    if (!design) return [];
    return [...design.placed].filter(([, count]) => count > 1).map(([name]) => messages.placedTwice(name));
  };

  const canvasArea = () => (
    <div ref={_toRef(canvas)} style={{ position: 'relative', minHeight: '20rem', padding: '0.75rem' }}>
      {view.value ? <FlowForm view={view.value} keepPrevious {...(people ? { people } : {})} {...(modules ? { modules } : {})} /> : <Text tone="muted">{messages.noRoot}</Text>}
      {designing() && (
        <div
          data-designer-overlay
          tabindex={0}
          role="application"
          aria-label={messages.canvas}
          draggable
          onKeydown={onKeydown}
          {...overlayEvents}
          style={{ position: 'absolute', inset: 0, cursor: 'default', outline: 'none' }}
        >
          {boxes.map((box, i) => (
            <div key={i} style={{ position: 'absolute', ...px(box), outline: '2px solid var(--designer-accent, #2563eb)', outlineOffset: '2px', borderRadius: '4px', pointerEvents: 'none' }} />
          ))}
          {drop && <div style={{ position: 'absolute', ...px(drop.line), background: 'var(--designer-accent, #2563eb)', pointerEvents: 'none' }} />}
        </div>
      )}
    </div>
  );

  const panel = () => {
    const node = design && selected ? findNode(design.root, selected) : null;
    if (!design || !node) return <Text tone="muted">{messages.nothingSelected}</Text>;
    const title = node.kind === 'code' ? messages.codeBlock : node.kind === 'field' ? (design.fields.find((f) => f.name === node.field!.name)?.definition?.label ?? node.field!.name) : (node.component?.label ?? node.element?.tag ?? '');
    return (
      <Card header={title}>
        <Flex direction="column" gap="md">
          <PropertyPanel key={node.path} source={source} design={design} node={node} onEdit={onEdit} />
          {node !== design.root && (
            <Button variant="outline" tone="danger" size="sm" icon={Trash2} onClick={removeSelected} style={{ alignSelf: 'flex-start' }}>
              {messages.remove}
            </Button>
          )}
        </Flex>
      </Card>
    );
  };

  return () => (
    <Flex direction="column" gap="sm" onKeydown={onKeydown}>
      <Toolbar aria-label={messages.canvas}>
        <IconButton icon={Undo2} variant="ghost" aria-label={messages.undo} disabled={!onUndo} onClick={() => onUndo?.()} />
        <IconButton icon={Redo2} variant="ghost" aria-label={messages.redo} disabled={!onRedo} onClick={() => onRedo?.()} />
        <IconButton icon={Trash2} variant="ghost" aria-label={messages.remove} disabled={!designing() || !selected || selected === design?.root?.path} onClick={removeSelected} />
        <Separator orientation="vertical" />
        <ToggleGroup aria-label={messages.design} required size="sm" _value={mode} onUpdate:_value={(next: string) => (mode = next as 'design' | 'preview')}>
          <ToggleItem value="design" icon={PenTool}>{messages.design}</ToggleItem>
          <ToggleItem value="preview" icon={Eye}>{messages.preview}</ToggleItem>
        </ToggleGroup>
      </Toolbar>
      {syntax && <Alert tone="warning">{messages.syntaxError(syntax)}</Alert>}
      {warnings().map((w) => (
        <Alert key={w} tone="warning">{w}</Alert>
      ))}
      <Splitter
        _size={72}
        start={
          <Flex gap="sm" align="start">
            <Flex direction="column" gap="sm" style={{ width: '12rem', flex: 'none' }}>
              {palette()}
              {fieldList()}
            </Flex>
            <Card style={{ flex: '1 1 0', minWidth: 0 }}>{canvasArea()}</Card>
          </Flex>
        }
        end={<Flex direction="column" gap="sm" style={{ paddingInlineStart: '0.5rem' }}>{panel()}</Flex>}
      />
    </Flex>
  );
});

const px = (rect: Rect) => ({ left: `${rect.left}px`, top: `${rect.top}px`, width: `${rect.width}px`, height: `${rect.height}px` });

/** 下一个没用过的 fieldN */
function newFieldName(design: FormDesign): string {
  const used = new Set(design.fields.map((f) => f.name));
  let n = 1;
  while (used.has(`field${n}`)) n++;
  return `field${n}`;
}

/** 新字段的定义：标签先给"字段 n"，选项字段带一个选项 */
function newField(type: FieldType, design: FormDesign, messages: ReturnType<typeof useFormDesignerMessages>): FieldDefinition {
  const n = Number(newFieldName(design).slice(5));
  return type === 'select'
    ? { label: messages.newFieldLabel(n), type, options: [{ value: '1', label: messages.newOption(1) }] }
    : { label: messages.newFieldLabel(n), type };
}

/**
 * 挪动后节点的新路径：挪走的那个原来排在目标的某一层祖先前面时，那一层的序号少一
 * （path 是按挪之前数的目标位置）
 */
function afterRemoval(path: string, removed: string): string {
  const target = path.split('.');
  const gone = removed.split('.');
  const depth = gone.length - 1;
  const sameParent = gone.slice(0, depth).every((segment, i) => target[i] === segment);
  if (sameParent && target.length > depth && Number(target[depth]) > Number(gone[depth])) target[depth] = String(Number(target[depth]) - 1);
  return target.join('.');
}
