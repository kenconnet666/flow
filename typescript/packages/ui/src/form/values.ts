/**
 * 表单的值在两种样子之间转换：
 *
 *   线上（JSON，0004 的 1.3）        表单里（useForm 的 values，控件直接绑定）
 *   decimal："19.90"            ↔    Decimal（NumberField 按 Decimal 读写，不经浮点）
 *   其余：原样                  ↔    原样；没有值的字段是 null（people、file、list 是空数组）
 *
 * 提交、校验前转回线上的样子，再交给模型包的规则（mergeSubmission），与两个后端同一套。
 */
import type { FieldDefinition, FormManifest, SubmissionError } from '@zerodep-dev/flow-model';
import Decimal from 'decimal.js';

/** 表单里的一行值 */
export type FormState = Record<string, unknown>;

/** 一个字段没有值时在表单里是什么 */
export function emptyValue(field: FieldDefinition): unknown {
  if (field.type === 'people' || field.type === 'file' || field.type === 'list') return [];
  if (field.type === 'select' && field.multiple) return [];
  if (field.type === 'boolean') return false;
  return null;
}

/** 线上的值 → 表单里的值；没给值时用清单里的 default，再没有时是空值 */
export function toFormState(manifest: FormManifest, json: Readonly<Record<string, unknown>>): FormState {
  const state: FormState = {};
  for (const [name, field] of Object.entries(manifest)) {
    state[name] = toFormValue(field, json[name] ?? field.default ?? null);
  }
  return state;
}

function toFormValue(field: FieldDefinition, value: unknown): unknown {
  if (value == null) return emptyValue(field);
  if (field.type === 'decimal') return typeof value === 'string' || typeof value === 'number' ? new Decimal(value) : value;
  if (field.type === 'list' && field.fields && Array.isArray(value)) {
    const columns = field.fields;
    return value.map((row) => toFormState(columns, (row ?? {}) as Record<string, unknown>));
  }
  return value;
}

/** 表单里的值 → 线上的值（decimal 按清单的小数位写成文字） */
export function toJson(manifest: FormManifest, state: Readonly<FormState>): Record<string, unknown> {
  const json: Record<string, unknown> = {};
  for (const [name, field] of Object.entries(manifest)) json[name] = toJsonValue(field, state[name]);
  return json;
}

function toJsonValue(field: FieldDefinition, value: unknown): unknown {
  if (value == null) return null;
  if (field.type === 'decimal' && Decimal.isDecimal(value)) {
    return field.scale === undefined ? (value as Decimal).toString() : (value as Decimal).toFixed(field.scale);
  }
  if (field.type === 'list' && field.fields && Array.isArray(value)) {
    const columns = field.fields;
    return value.map((row) => toJson(columns, row as FormState));
  }
  return value;
}

/** 模型包的问题路径（`lines[0].price`）→ 表单控制器的路径（`lines.0.price`） */
export const formPath = (field: SubmissionError['field']) => field.replace(/\[(\d+)]/g, '.$1');
