/**
 * 设计器新写进源码的代码由这里生成（0004 的 3.3）：字面量按 JSON5 写（键不加引号、单引号），属性、元素的写法同 Prettier 的风格。
 * 只生成新的那一段，源码的其余部分一个字符不动。
 */
import type { PropValue } from './parse';
import type { FormComponent } from './registry';

/** 一行放不下时换行的宽度 */
const WIDTH = 100;

/** 字面量 → JSON5 源码；放不下一行的对象、数组按 indent 换行 */
export function printLiteral(value: unknown, indent = ''): string {
  const flat = printFlat(value);
  if (flat.length + indent.length <= WIDTH || typeof value !== 'object' || value === null) return flat;
  const inner = `${indent}  `;
  if (Array.isArray(value)) return `[\n${value.map((item) => `${inner}${printLiteral(item, inner)},`).join('\n')}\n${indent}]`;
  const entries = Object.entries(value as Record<string, unknown>).filter(([, v]) => v !== undefined);
  return `{\n${entries.map(([key, item]) => `${inner}${printKey(key)}: ${printLiteral(item, inner)},`).join('\n')}\n${indent}}`;
}

function printFlat(value: unknown): string {
  if (typeof value === 'string') return printString(value);
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  if (value === null || value === undefined) return 'null';
  if (Array.isArray(value)) return `[${value.map(printFlat).join(', ')}]`;
  const entries = Object.entries(value as Record<string, unknown>).filter(([, v]) => v !== undefined);
  return entries.length ? `{ ${entries.map(([key, item]) => `${printKey(key)}: ${printFlat(item)}`).join(', ')} }` : '{}';
}

const printString = (text: string) => `'${text.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/\n/g, '\\n')}'`;
export const printKey = (key: string) => (/^[A-Za-z_$][\w$]*$/.test(key) ? key : printString(key));

/** 一个 JSX 属性：`title="分组"`、`wrap`、`columns={2}`、`disabled={amount > 100}` */
export function printProp(name: string, value: PropValue): string {
  if ('code' in value) return `${name}={${value.code}}`;
  const v = value.literal;
  if (v === true) return name;
  if (typeof v === 'string' && !/["\n]/.test(v)) return `${name}="${v}"`;
  return `${name}={${printFlat(v)}}`;
}

/** JSX 的文字内容：有 { } < > 时写成 {'…'} */
export function printText(text: string): string {
  return /[{}<>\n]/.test(text) ? `{${printString(text)}}` : text;
}

/** 新放进来的组件：默认属性与内容；容器写成成对的空标签 */
export function printComponent(component: FormComponent, local = component.name): string {
  const props = Object.entries(component.defaults ?? {}).map(([name, value]) => ` ${printProp(name, { literal: value })}`).join('');
  if (component.content !== undefined) return `<${local}${props}>${printText(component.content)}</${local}>`;
  return component.container ? `<${local}${props}></${local}>` : `<${local}${props} />`;
}

/** 摆一个字段：form.Field 包着控件 */
export function printField(name: string, control: FormComponent, local = control.name): string {
  return `<form.Field name="${name}">${printComponent(control, local)}</form.Field>`;
}
