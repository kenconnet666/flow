/**
 * 表单的字段清单（docs/decisions/0004 的 1.3）：表单源码里 `defineFields({...})` 的那个 JSON5 字面量，保存时提取成 JSON 存下。
 * 引擎、流程设计器、表单设计器都只看这份清单；字段权限的合算见 access.ts。
 */
import type { Condition } from './condition.js';

/** 内置的字段类型；接入方可以注册新的（0004 的"可替换的部分"） */
export type BuiltinFieldType = 'text' | 'number' | 'decimal' | 'boolean' | 'date' | 'datetime' | 'select' | 'people' | 'file' | 'list';

/** 字段类型 */
export type FieldType = BuiltinFieldType | (string & {});

/** 选项型字段的一个选项 */
export interface FieldOption {
  readonly value: string | number;
  readonly label: string;
}

/** 一个字段 */
export interface FieldDefinition {
  readonly label: string;
  readonly type: FieldType;
  readonly description?: string;
  readonly required?: boolean;
  readonly default?: unknown;
  /** 显示条件：不成立时隐藏、值丢掉 */
  readonly visibleWhen?: Condition;
  /** 必填条件 */
  readonly requiredWhen?: Condition;
  // 各类型的专有属性
  /** text */
  readonly minLength?: number;
  readonly maxLength?: number;
  readonly pattern?: string;
  /** number、decimal（decimal 时是文字）、date、datetime；people 时是最多几个人 */
  readonly min?: number | string;
  readonly max?: number | string;
  readonly integer?: boolean;
  /** decimal 的小数位 */
  readonly scale?: number;
  /** select：字面量列表，或接入方登记的字典 */
  readonly options?: readonly FieldOption[] | { readonly source: string };
  readonly multiple?: boolean;
  /** people：能选的类型 */
  readonly types?: readonly string[];
  /** file */
  readonly accept?: string;
  readonly maxCount?: number;
  readonly maxSize?: number;
  /** list：明细行的列（一层，里面不能再有 list） */
  readonly fields?: FormManifest;
  readonly minItems?: number;
  readonly maxItems?: number;
}

/** 字段清单：字段名 → 字段 */
export type FormManifest = Readonly<Record<string, FieldDefinition>>;

/** 按路径找字段：`amount`，或明细表的列 `lines.price` */
export function manifestField(manifest: FormManifest, path: string): FieldDefinition | undefined {
  const dot = path.indexOf('.');
  if (dot < 0) return Object.hasOwn(manifest, path) ? manifest[path] : undefined;
  const list = manifestField(manifest, path.slice(0, dot));
  return list?.type === 'list' && list.fields ? manifestField(list.fields, path.slice(dot + 1)) : undefined;
}

/** 全部字段的路径，明细表的列写成 `列表.列`，按清单里的先后 */
export function manifestPaths(manifest: FormManifest): string[] {
  const paths: string[] = [];
  for (const [name, field] of Object.entries(manifest)) {
    paths.push(name);
    if (field.type === 'list' && field.fields) for (const column of Object.keys(field.fields)) paths.push(`${name}.${column}`);
  }
  return paths;
}
