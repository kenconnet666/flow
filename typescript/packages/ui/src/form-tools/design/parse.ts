/**
 * 表单源码 → 设计视图（0004 的 2、3.1）：字段列表与组件树都是从源码解析出来的，不另存一份模型。
 * 每次改动后重新解析（几毫秒），结构信息总是最新的；写回（edit.ts）按这里记下的源码区间只改受影响的那一段。
 *
 *   const design = parseDesign(source);         // 语法错误时抛 Babel 的错误（带行列）
 *   design.fields      // 字段：名字、定义、在 defineFields 里的区间
 *   design.root        // 组件树：根是 <form.Form>，路径 '0'；子节点 '0.2'、'0.2.1'……（第几个子元素）
 *   design.unplaced    // 清单里有、画布上没摆的字段
 *
 * 认得的：form.Form、form.Field（加里面的一个控件）、form.List（明细表，当一个字段）、登记过的组件（registry.ts）；别的元素、{表达式}、文字都是代码块，
 * 能整块移动、删除，里面不深入。
 */
import { parse } from '@babel/parser';
import type {
  JSXAttribute,
  JSXElement,
  JSXExpressionContainer,
  JSXFragment,
  JSXSpreadChild,
  JSXText,
  Node,
  Program,
} from '@babel/types';
import type { FieldDefinition } from '@zerodep-dev/flow-model';
import { findFieldsCall, literal, type ExtractProblem } from '../extract';
import { findFormComponent, type FormComponent } from './registry';

/** 源码里的一段：[from, to) */
export interface SourceRange {
  readonly from: number;
  readonly to: number;
}

/** 属性的值：字面量（字符串、数字、布尔、简单 JSON）或者一段代码 */
export type PropValue = { readonly literal: unknown } | { readonly code: string };

/** 元素上的一个属性 */
export interface PropInfo {
  readonly name: string;
  /** 整个属性（`title="基本信息"`） */
  readonly range: SourceRange;
  readonly value: PropValue;
}

/** 一个 JSX 元素的写法：改属性、改内容、往里放东西时用 */
export interface ElementInfo {
  /** 标签名的源码（`FormSection`、`form.Field`） */
  readonly tag: string;
  readonly range: SourceRange;
  /** 开始标签里最后一个属性（没有时是标签名）的结尾：新属性加在这里 */
  readonly attributesEnd: number;
  readonly selfClosing: boolean;
  /** 属性；展开（`{...rest}`）不在里面 */
  readonly props: readonly PropInfo[];
  /** 开始与结束标签之间；自闭合时为 null */
  readonly inner: SourceRange | null;
  /** 内容只有文字时的文字（Text、Alert 的内容）；有别的时为 null */
  readonly text: string | null;
}

/** 组件树的一个节点 */
export interface DesignNode {
  /** 从根起每层是第几个子元素：'0'、'0.2'、'0.2.1' */
  readonly path: string;
  /** form：根上的 form.Form；field：form.Field 或 form.List；component：登记过的组件；code：代码块 */
  readonly kind: 'form' | 'field' | 'component' | 'code';
  readonly range: SourceRange;
  /** component 是它本身，field 是里面的控件；认不出时为 null */
  readonly component: FormComponent | null;
  /** 节点自己的元素（code 里的表达式为 null） */
  readonly element: ElementInfo | null;
  /** field：字段名与里面的控件元素（不是单个元素时为 null） */
  readonly field?: { readonly name: string; readonly control: ElementInfo | null };
  /** 能往里面放东西：form 与登记为容器的组件 */
  readonly container: boolean;
  readonly children: readonly DesignNode[];
  /** 设计时编译要加 data-form-node 的位置（标签名的结尾）：代码块里的每个顶层元素都加 */
  readonly marks: readonly number[];
}

/** 字段清单里的一项 */
export interface FieldEntry {
  readonly name: string;
  /** 定义；不是字面量时为 null */
  readonly definition: FieldDefinition | null;
  /** 整个属性（`amount: { … }`） */
  readonly range: SourceRange;
  /** 值（`{ … }`） */
  readonly value: SourceRange;
}

/** 设计视图 */
export interface FormDesign {
  /** defineFields 的对象字面量；没有时为 null */
  readonly fieldsObject: SourceRange | null;
  readonly fields: readonly FieldEntry[];
  /** 组件树的根；找不到 defineFlowForm 的渲染内容时为 null */
  readonly root: DesignNode | null;
  /** 摆在画布上的字段：名字 → 次数（代码块里的也算） */
  readonly placed: ReadonlyMap<string, number>;
  /** 清单里有、哪里都没摆的字段 */
  readonly unplaced: readonly string[];
  /** 导入：本地名 → 模块与导出名（`import * as ui` 的导出名是 *） */
  readonly imports: ReadonlyMap<string, { readonly module: string; readonly name: string }>;
  /** 每个模块最后一条命名导入的区间与最后一个导入名的结尾：补导入时用 */
  readonly importSites: ReadonlyMap<string, { readonly declaration: SourceRange; readonly lastSpecifierEnd: number | null }>;
  /** 最后一条 import 的结尾；没有 import 时为 0 */
  readonly importsEnd: number;
  /** 清单里不合规的写法（extractFields 的问题） */
  readonly problems: readonly ExtractProblem[];
}

/** 解析表单源码；语法错误时抛出 */
export function parseDesign(source: string): FormDesign {
  const program = parse(source, { sourceType: 'module', plugins: ['typescript', 'jsx'] }).program;
  const imports = new Map<string, { module: string; name: string }>();
  const importSites = new Map<string, { declaration: SourceRange; lastSpecifierEnd: number | null }>();
  let importsEnd = 0;
  for (const statement of program.body) {
    if (statement.type !== 'ImportDeclaration') continue;
    importsEnd = statement.end!;
    const module = statement.source.value;
    let lastSpecifierEnd: number | null = null;
    for (const specifier of statement.specifiers) {
      if (specifier.type === 'ImportSpecifier') {
        const imported = specifier.imported.type === 'Identifier' ? specifier.imported.name : specifier.imported.value;
        imports.set(specifier.local.name, { module, name: imported });
        lastSpecifierEnd = specifier.end!;
      } else if (specifier.type === 'ImportNamespaceSpecifier') {
        imports.set(specifier.local.name, { module, name: '*' });
      }
    }
    if (lastSpecifierEnd !== null && statement.importKind !== 'type') {
      importSites.set(module, { declaration: rangeOf(statement), lastSpecifierEnd });
    }
  }

  const { fieldsObject, fields, problems } = readFields(program);
  const setup = findSetup(program);
  const rootElement = setup ? firstElement(setup) : null;
  const resolve = (tag: string) => resolveTag(tag, imports);
  const root = rootElement ? toNode(rootElement, '0', source, resolve) : null;

  const placed = new Map<string, number>();
  if (setup) {
    walk(setup, (node) => {
      if (node.type !== 'JSXElement' || (tagOf(node) !== 'form.Field' && tagOf(node) !== 'form.List')) return;
      const name = stringProp(node, 'name');
      if (name !== null) placed.set(name, (placed.get(name) ?? 0) + 1);
    });
  }
  const unplaced = fields.map((f) => f.name).filter((name) => !placed.has(name));
  return { fieldsObject, fields, root, placed, unplaced, imports, importSites, importsEnd, problems };
}

/** 按路径找节点 */
export function findNode(root: DesignNode | null, path: string): DesignNode | null {
  if (!root) return null;
  if (root.path === path) return root;
  if (!path.startsWith(`${root.path}.`)) return null;
  for (const child of root.children) {
    const found = findNode(child, path);
    if (found) return found;
  }
  return null;
}

/** 节点所在的容器与它在里面的位置；根返回 null */
export function parentOf(root: DesignNode | null, path: string): { parent: DesignNode; index: number } | null {
  const at = path.lastIndexOf('.');
  if (at < 0) return null;
  const parent = findNode(root, path.slice(0, at));
  return parent ? { parent, index: Number(path.slice(at + 1)) } : null;
}

/** 树上的全部节点，先根 */
export function allNodes(root: DesignNode | null): DesignNode[] {
  if (!root) return [];
  return [root, ...root.children.flatMap(allNodes)];
}

// ───────── 字段清单 ─────────

function readFields(program: Program): Pick<FormDesign, 'fieldsObject' | 'fields' | 'problems'> {
  const call = findFieldsCall(program);
  if ('problem' in call) return { fieldsObject: null, fields: [], problems: [call.problem] };
  const problems: ExtractProblem[] = [];
  const fields: FieldEntry[] = [];
  for (const property of call.argument.properties) {
    if (property.type !== 'ObjectProperty' || property.computed) {
      literal(property, problems);
      continue;
    }
    const name = property.key.type === 'Identifier' ? property.key.name : property.key.type === 'StringLiteral' ? property.key.value : null;
    if (name === null) continue;
    const before = problems.length;
    const definition = literal(property.value, problems) as FieldDefinition;
    fields.push({ name, definition: problems.length > before ? null : definition, range: rangeOf(property), value: rangeOf(property.value) });
  }
  return { fieldsObject: rangeOf(call.argument), fields, problems };
}

// ───────── 组件树 ─────────

/** `export default defineFlowForm(fields, setup)` 的 setup */
function findSetup(program: Program): Node | null {
  for (const statement of program.body) {
    if (statement.type !== 'ExportDefaultDeclaration') continue;
    const call = statement.declaration;
    if (call.type === 'CallExpression' && call.callee.type === 'Identifier' && call.callee.name === 'defineFlowForm') {
      return (call.arguments[1] as Node | undefined) ?? null;
    }
  }
  return null;
}

/** 先序遍历里遇到的第一个 JSX 元素：渲染函数返回的根 */
function firstElement(node: Node): JSXElement | null {
  let found: JSXElement | null = null;
  walk(node, (child) => {
    if (!found && child.type === 'JSXElement') found = child;
    return !found;
  });
  return found;
}

type Resolved = { kind: 'form' } | { kind: 'field' } | { kind: 'component'; component: FormComponent } | null;

/** 标签名 → 认得的什么：form.Form、form.Field、登记的组件（经 import 找到模块与导出名） */
function resolveTag(tag: string, imports: ReadonlyMap<string, { module: string; name: string }>): Resolved {
  if (tag === 'form.Form') return { kind: 'form' };
  if (tag === 'form.Field' || tag === 'form.List') return { kind: 'field' };
  const [local, member] = tag.split('.');
  const imported = imports.get(local!);
  if (!imported) return null;
  const name = member === undefined ? imported.name : imported.name === '*' ? member : null;
  const component = name ? findFormComponent(imported.module, name) : undefined;
  return component ? { kind: 'component', component } : null;
}

type Child = JSXElement | JSXExpressionContainer | JSXText | JSXFragment | JSXSpreadChild;

function toNode(child: Child, path: string, source: string, resolve: (tag: string) => Resolved): DesignNode {
  const range = rangeOf(child);
  if (child.type !== 'JSXElement') return codeNode(child, path, range, null);
  const resolved = resolve(tagOf(child));
  const element = elementInfo(child, source);
  const mark = [child.openingElement.name.end!];
  if (resolved?.kind === 'field') {
    const controls = significant(child.children).filter((c): c is JSXElement => c.type === 'JSXElement');
    const control = controls.length === 1 && significant(child.children).length === 1 ? controls[0]! : null;
    const component = control ? resolve(tagOf(control)) : null;
    return {
      path, kind: 'field', range, element, container: false, children: [], marks: mark,
      component: component?.kind === 'component' ? component.component : null,
      field: { name: stringProp(child, 'name') ?? '', control: control ? elementInfo(control, source) : null },
    };
  }
  if (resolved?.kind === 'form' || resolved?.kind === 'component') {
    const component = resolved.kind === 'component' ? resolved.component : null;
    const container = resolved.kind === 'form' || component?.container === true;
    const children = container ? significant(child.children).map((c, i) => toNode(c, `${path}.${i}`, source, resolve)) : [];
    return { path, kind: resolved.kind, range, element, component, container, children, marks: mark };
  }
  return codeNode(child, path, range, element);
}

/** 代码块：里面的顶层元素都加标识，点中哪个都选中整块 */
function codeNode(child: Child, path: string, range: SourceRange, element: ElementInfo | null): DesignNode {
  const marks: number[] = [];
  const collect = (node: Node) =>
    walk(node, (inner) => {
      if (inner.type !== 'JSXElement') return true;
      marks.push(inner.openingElement.name.end!);
      return false;
    });
  collect(child);
  return { path, kind: 'code', range, element, component: null, container: false, children: [], marks };
}

function elementInfo(element: JSXElement, source: string): ElementInfo {
  const opening = element.openingElement;
  const props: PropInfo[] = [];
  for (const attribute of opening.attributes) {
    if (attribute.type !== 'JSXAttribute') continue;
    const name = attribute.name.type === 'JSXIdentifier' ? attribute.name.name : `${attribute.name.namespace.name}:${attribute.name.name.name}`;
    props.push({ name, range: rangeOf(attribute), value: propValue(attribute, source) });
  }
  const last = opening.attributes.at(-1);
  const inner = element.closingElement ? { from: opening.end!, to: element.closingElement.start! } : null;
  const children = significant(element.children);
  const only = children.length === 1 ? children[0]! : null;
  const text = children.length === 0 && inner ? '' : only?.type === 'JSXText' ? only.value.trim() : stringContent(only);
  return {
    tag: tagOf(element),
    range: rangeOf(element),
    attributesEnd: (last ?? opening.name).end!,
    selfClosing: opening.selfClosing,
    props,
    inner,
    text,
  };
}

/** `{'文字'}` 写的内容 */
function stringContent(child: Child | null): string | null {
  return child?.type === 'JSXExpressionContainer' && child.expression.type === 'StringLiteral' ? child.expression.value : null;
}

function propValue(attribute: JSXAttribute, source: string): PropValue {
  const value = attribute.value;
  if (value === null || value === undefined) return { literal: true };
  if (value.type === 'StringLiteral') return { literal: value.value };
  if (value.type === 'JSXExpressionContainer' && value.expression.type !== 'JSXEmptyExpression') {
    const problems: ExtractProblem[] = [];
    const read = literal(value.expression, problems);
    return problems.length ? { code: source.slice(value.expression.start!, value.expression.end!) } : { literal: read };
  }
  return { code: source.slice(value.start!, value.end!) };
}

/** 有意义的子节点：去掉只有空白的文字与空的 {} */
function significant(children: readonly Child[]): Child[] {
  return children.filter(
    (c) => !(c.type === 'JSXText' && c.value.trim() === '') && !(c.type === 'JSXExpressionContainer' && c.expression.type === 'JSXEmptyExpression'),
  );
}

function tagOf(element: JSXElement): string {
  const name = element.openingElement.name;
  const text = (n: typeof name): string =>
    n.type === 'JSXIdentifier' ? n.name : n.type === 'JSXNamespacedName' ? `${n.namespace.name}:${n.name.name}` : `${text(n.object)}.${n.property.name}`;
  return text(name);
}

/** 写成字符串字面量的属性值 */
function stringProp(element: JSXElement, name: string): string | null {
  for (const attribute of element.openingElement.attributes) {
    if (attribute.type !== 'JSXAttribute' || attribute.name.type !== 'JSXIdentifier' || attribute.name.name !== name) continue;
    const value = attribute.value;
    if (value?.type === 'StringLiteral') return value.value;
    if (value?.type === 'JSXExpressionContainer' && value.expression.type === 'StringLiteral') return value.expression.value;
  }
  return null;
}

const rangeOf = (node: Node): SourceRange => ({ from: node.start!, to: node.end! });

/** 先序遍历 AST；visit 返回 false 时不进这个节点的子节点 */
function walk(node: Node, visit: (node: Node) => boolean | void): void {
  if (visit(node) === false) return;
  for (const key of Object.keys(node) as (keyof Node)[]) {
    if (key === 'loc' || key === 'leadingComments' || key === 'trailingComments' || key === 'innerComments') continue;
    const value = node[key] as unknown;
    if (Array.isArray(value)) {
      for (const item of value) if (isNode(item)) walk(item, visit);
    } else if (isNode(value)) {
      walk(value, visit);
    }
  }
}

const isNode = (value: unknown): value is Node =>
  typeof value === 'object' && value !== null && typeof (value as { type?: unknown }).type === 'string';

