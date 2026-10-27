/**
 * 设计操作 → 对源码的修改（0004 的 3.3）：每个操作算出要改的几段（TextEdit，按改之前的源码的位置），
 * 交给 CodeMirror 作为一次修改派发（撤销时是一步），或者用 applyEdits 直接得出新源码。
 *
 *   const edits = moveNode(source, design, '0.3', '0.1', 0);
 *   view.dispatch({ changes: edits });
 *
 * 插入取同一层兄弟元素的缩进；删除连同所在的整行；文件其余部分一个字符不动。做不了的操作（往代码块里放、挪进自己里面）返回空列表。
 */
import type { FieldDefinition } from '@zerodep-dev/flow-model';
import { findNode, type DesignNode, type ElementInfo, type FormDesign, type PropValue, type SourceRange } from './parse';
import { printKey, printLiteral, printProp, printText } from './print';

/** 一处修改：把 [from, to) 换成 insert */
export interface TextEdit {
  readonly from: number;
  readonly to: number;
  readonly insert: string;
}

/** 按改之前的位置应用几处修改（不能重叠） */
export function applyEdits(source: string, edits: readonly TextEdit[]): string {
  let out = source;
  for (const edit of [...edits].sort((a, b) => b.from - a.from || b.to - a.to)) {
    out = out.slice(0, edit.from) + edit.insert + out.slice(edit.to);
  }
  return out;
}

// ───────── 组件树 ─────────

/** 往容器的第 index 个子节点前面放一段代码（index 等于子节点数时放在最后） */
export function insertNode(source: string, design: FormDesign, parentPath: string, index: number, code: string): TextEdit[] {
  const parent = findNode(design.root, parentPath);
  if (!parent?.container || !parent.element) return [];
  return [insertion(source, parent, index, code)];
}

/** 删掉一个节点（连同它所在的整行）；根不能删 */
export function removeNode(source: string, design: FormDesign, path: string): TextEdit[] {
  const node = findNode(design.root, path);
  if (!node || node === design.root) return [];
  return [removal(source, node.range)];
}

/** 把节点挪到另一个容器的第 index 个子节点前面（index 按挪之前数）；挪进自己里面、挪回原处时什么都不改 */
export function moveNode(source: string, design: FormDesign, path: string, parentPath: string, index: number): TextEdit[] {
  const node = findNode(design.root, path);
  const parent = findNode(design.root, parentPath);
  if (!node || node === design.root || !parent?.container || !parent.element) return [];
  if (parentPath === path || parentPath.startsWith(`${path}.`)) return [];
  const own = parent.children.indexOf(node);
  if (own >= 0 && (index === own || index === own + 1)) return [];
  const code = dedent(source.slice(node.range.from, node.range.to), indentAt(source, node.range.from));
  return [removal(source, node.range), insertion(source, parent, index, code)];
}

function insertion(source: string, parent: DesignNode, index: number, code: string): TextEdit {
  const element = parent.element!;
  const children = parent.children;
  if (children.length > 0) {
    const indent = indentAt(source, children[0]!.range.from);
    const body = reindent(code, indent);
    if (index < children.length) {
      const before = children[index]!.range.from;
      const lineStart = startOfLine(source, before);
      return isBlank(source.slice(lineStart, before))
        ? { from: lineStart, to: lineStart, insert: `${indent}${body}\n` }
        : { from: before, to: before, insert: `${body}\n${indent}` };
    }
    const after = children.at(-1)!.range.to;
    return { from: after, to: after, insert: `\n${indent}${body}` };
  }
  const outer = indentAt(source, element.range.from);
  const indent = `${outer}  `;
  const body = reindent(code, indent);
  if (element.inner) return { from: element.inner.from, to: element.inner.to, insert: `\n${indent}${body}\n${outer}` };
  // 自闭合的容器改成成对的标签
  return { from: element.attributesEnd, to: element.range.to, insert: `>\n${indent}${body}\n${outer}</${element.tag}>` };
}

/** 删掉一段：它独占的行整行删掉，否则只删它（和它前面的空白） */
function removal(source: string, range: SourceRange): TextEdit {
  const lineStart = startOfLine(source, range.from);
  const lineEnd = endOfLine(source, range.to);
  if (isBlank(source.slice(lineStart, range.from)) && isBlank(source.slice(range.to, lineEnd))) {
    return { from: lineStart, to: Math.min(lineEnd + 1, source.length), insert: '' };
  }
  let from = range.from;
  while (from > lineStart && /[ \t]/.test(source[from - 1]!)) from--;
  return { from, to: range.to, insert: '' };
}

// ───────── 属性与内容 ─────────

/** 设一个属性：已有的只换这个属性；没有的加在最后一个属性后面；value 为 undefined 时删掉 */
export function setProp(source: string, element: ElementInfo, name: string, value: PropValue | undefined): TextEdit[] {
  const existing = element.props.find((p) => p.name === name);
  if (existing) {
    if (value === undefined) {
      let from = existing.range.from;
      while (from > 0 && /\s/.test(source[from - 1]!)) from--;
      return [{ from, to: existing.range.to, insert: '' }];
    }
    return [{ ...existing.range, insert: printProp(name, value) }];
  }
  if (value === undefined) return [];
  const last = element.props.at(-1);
  // 属性一行一个时，新属性也另起一行
  const separator = last && source.slice(startOfLine(source, last.range.from), last.range.from).trim() === '' && lineOf(source, last.range.from) !== lineOf(source, element.range.from)
    ? `\n${indentAt(source, last.range.from)}`
    : ' ';
  return [{ from: element.attributesEnd, to: element.attributesEnd, insert: `${separator}${printProp(name, value)}` }];
}

/** 改文字内容（Text、Alert） */
export function setText(element: ElementInfo, text: string): TextEdit[] {
  if (!element.inner) return [{ from: element.attributesEnd, to: element.range.to, insert: `>${printText(text)}</${element.tag}>` }];
  return [{ ...element.inner, insert: printText(text) }];
}

/** 换控件：标签名换掉，属性留着（同一字段类型能用的控件之间切换） */
export function replaceTag(source: string, element: ElementInfo, tag: string): TextEdit[] {
  const nameStart = element.range.from + 1;
  const edits: TextEdit[] = [{ from: nameStart, to: nameStart + element.tag.length, insert: tag }];
  if (element.inner) {
    const close = source.lastIndexOf(element.tag, element.range.to);
    edits.push({ from: close, to: close + element.tag.length, insert: tag });
  }
  return edits;
}

// ───────── 字段清单 ─────────

/** 改一个字段的定义 */
export function setField(source: string, design: FormDesign, name: string, definition: FieldDefinition): TextEdit[] {
  const entry = design.fields.find((f) => f.name === name);
  if (!entry) return addField(source, design, name, definition);
  return [{ ...entry.value, insert: printLiteral(definition, indentAt(source, entry.range.from)) }];
}

/** 加一个字段，放在清单的最后 */
export function addField(source: string, design: FormDesign, name: string, definition: FieldDefinition): TextEdit[] {
  const object = design.fieldsObject;
  if (!object) return [];
  const last = design.fields.at(-1);
  if (!last) {
    const outer = indentAt(source, object.from);
    return [{ ...object, insert: `{\n${outer}  ${printKey(name)}: ${printLiteral(definition, `${outer}  `)},\n${outer}}` }];
  }
  const indent = indentAt(source, last.range.from);
  const entry = `${printKey(name)}: ${printLiteral(definition, indent)}`;
  const comma = source.indexOf(',', last.range.to);
  const trailing = comma >= 0 && comma < object.to - 1 && isBlank(source.slice(last.range.to, comma));
  return trailing
    ? [{ from: comma + 1, to: comma + 1, insert: `\n${indent}${entry},` }]
    : [{ from: last.range.to, to: last.range.to, insert: `,\n${indent}${entry}` }];
}

/** 删掉一个字段（连同后面的逗号与整行）；画布上摆着的不动，由调用方另删 */
export function removeField(source: string, design: FormDesign, name: string): TextEdit[] {
  const entry = design.fields.find((f) => f.name === name);
  if (!entry) return [];
  const comma = source.indexOf(',', entry.range.to);
  const end = comma >= 0 && isBlank(source.slice(entry.range.to, comma)) ? comma + 1 : entry.range.to;
  return [removal(source, { from: entry.range.from, to: end })];
}

/** 字段改名：清单里的键，加上画布上摆着它的 form.Field 的 name（条件里引用的 form.旧名 不改，校验会指出） */
export function renameField(source: string, design: FormDesign, from: string, to: string): TextEdit[] {
  const entry = design.fields.find((f) => f.name === from);
  if (!entry || design.fields.some((f) => f.name === to)) return [];
  const keyEnd = source.indexOf(':', entry.range.from);
  const edits: TextEdit[] = [{ from: entry.range.from, to: keyEnd, insert: printKey(to) }];
  const visit = (node: DesignNode) => {
    if (node.field?.name === from) {
      const prop = node.element?.props.find((p) => p.name === 'name');
      if (prop) edits.push({ ...prop.range, insert: printProp('name', { literal: to }) });
    }
    node.children.forEach(visit);
  };
  if (design.root) visit(design.root);
  return edits;
}

// ───────── 导入 ─────────

/** 确保从 module 导入了 name；返回本地名与要做的修改 */
export function ensureImport(source: string, design: FormDesign, module: string, name: string): { local: string; edits: TextEdit[] } {
  for (const [local, imported] of design.imports) {
    if (imported.module === module && imported.name === name) return { local, edits: [] };
    if (imported.module === module && imported.name === '*') return { local: `${local}.${name}`, edits: [] };
  }
  const site = design.importSites.get(module);
  if (site?.lastSpecifierEnd != null) return { local: name, edits: [{ from: site.lastSpecifierEnd, to: site.lastSpecifierEnd, insert: `, ${name}` }] };
  const at = design.importsEnd;
  return { local: name, edits: [{ from: at, to: at, insert: `${at > 0 ? '\n' : ''}import { ${name} } from '${module}';${at > 0 ? '' : '\n'}` }] };
}

// ───────── 设计时的源码 ─────────

/** 设计时编译用的源码：每个节点的元素加上 data-form-node="路径"，画布上点中、放下时据此找回节点 */
export function designSource(source: string, design: FormDesign): string {
  const marks: TextEdit[] = [];
  const visit = (node: DesignNode) => {
    for (const at of node.marks) marks.push({ from: at, to: at, insert: ` data-form-node="${node.path}"` });
    node.children.forEach(visit);
  };
  if (design.root) visit(design.root);
  return applyEdits(source, marks);
}

// ───────── 位置与缩进 ─────────

const isBlank = (text: string) => text.trim() === '';
const startOfLine = (source: string, at: number) => source.lastIndexOf('\n', at - 1) + 1;
function endOfLine(source: string, at: number): number {
  const end = source.indexOf('\n', at);
  return end < 0 ? source.length : end;
}
const lineOf = (source: string, at: number) => source.slice(0, at).split('\n').length;

/** at 所在行开头的空白 */
function indentAt(source: string, at: number): string {
  return /^[ \t]*/.exec(source.slice(startOfLine(source, at)))![0];
}

/** 多行代码的第二行起加上缩进（第一行由插入的位置决定） */
const reindent = (code: string, indent: string) => code.split('\n').join(`\n${indent}`);

/** 从源码里截出的多行代码：第二行起去掉原来的缩进 */
function dedent(code: string, indent: string): string {
  return code
    .split('\n')
    .map((line, i) => (i > 0 && line.startsWith(indent) ? line.slice(indent.length) : line))
    .join('\n');
}
