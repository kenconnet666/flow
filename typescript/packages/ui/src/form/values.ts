/**
 * 表单里的值（useForm 的 values，控件直接绑定）就是线上的强类型值：请求层按 typed JSON 还原（json-wire 的格式，
 * 接入方给 createHttpClient 配 `wire: { decimal: Decimal, dates: 'objects' }`），decimal 是 Decimal、date 是 CalendarDate、
 * datetime 是 ZonedDateTime，提交时照样写回。这里只补没有值的字段：清单里的 default（人写的文字，按字段类型读成强类型），
 * 再没有时是空值。
 */
import { typedValue, type FieldDefinition, type FormManifest, type SubmissionError } from '@zerodep-dev/flow-model';

/** 表单里的一行值 */
export type FormState = Record<string, unknown>;

/** 一个字段没有值时在表单里是什么 */
export function emptyValue(field: FieldDefinition): unknown {
  if (field.type === 'people' || field.type === 'file' || field.type === 'list') return [];
  if (field.type === 'select' && field.multiple) return [];
  if (field.type === 'boolean') return false;
  return null;
}

/** 下发的值 → 表单里的值：没给值时用清单里的 default，再没有时是空值；明细表逐行补 */
export function toFormState(manifest: FormManifest, values: Readonly<Record<string, unknown>>): FormState {
  const state: FormState = {};
  for (const [name, field] of Object.entries(manifest)) {
    const value = values[name] ?? (field.default == null ? null : typedValue(field, field.default));
    state[name] = toFormValue(field, value);
  }
  return state;
}

function toFormValue(field: FieldDefinition, value: unknown): unknown {
  if (value == null) return emptyValue(field);
  if (field.type === 'list' && field.fields && Array.isArray(value)) {
    const columns = field.fields;
    return value.map((row) => toFormState(columns, (row ?? {}) as Record<string, unknown>));
  }
  return value;
}

/** 模型包的问题路径（`lines[0].price`）→ 表单控制器的路径（`lines.0.price`） */
export const formPath = (field: SubmissionError['field']) => field.replace(/\[(\d+)]/g, '.$1');
