/**
 * 一个字段的值合不合字段清单（docs/decisions/0004 的 1.3）：提交时（access.ts 的 mergeSubmission）与字段清单校验默认值时
 * （validate-manifest.ts）都用它。与 Java 的 `FieldValues` 同一套规则，两边跑 spec/access 与 spec/manifests 的样例。
 *
 * 只查形状与范围；`options: { source }` 的字典、接入方注册的字段类型由后端另查。
 */
import { parseWire, wireTypeOf, type WireType } from '@zerodep-dev/json-wire';
import { isOrgId } from './definition.js';
import type { FieldDefinition } from './manifest.js';
import { compareValues, isBlank } from './values.js';

/** 值不合的原因 */
export type ValueProblem =
  | 'type'
  | 'minLength'
  | 'maxLength'
  | 'pattern'
  | 'min'
  | 'max'
  | 'integer'
  | 'scale'
  | 'option'
  | 'peopleType'
  | 'maxCount'
  | 'maxSize'
  | 'minItems'
  | 'maxItems';

/** 值的一个问题 */
export interface ValueError {
  /** 相对这个字段的路径：字段自己是 ''，明细表的格子是 `[0].price` */
  readonly path: string;
  readonly code: ValueProblem;
}

/**
 * 值是强类型的字段类型在线上（json-wire）是哪一种：提交、下发的值由 wire 还原（decimal 是 Decimal、date 是 CalendarDate、
 * datetime 是 ZonedDateTime），清单里人写的 default、min、max 是文字，用 {@link typedValue} 读成同样的类型。同 Java 的 FormManifest.VALUE_KINDS。
 */
export const VALUE_KINDS: Readonly<Partial<Record<string, WireType>>> = { decimal: 'decimal', date: 'date', datetime: 'timestamptz' };

/**
 * 人写的值（清单的 default、min、max，定义里写进表单的值）按字段类型读成强类型；不是强类型的字段、读不出的文字原样
 * （提交时报 type）。明细表逐行逐列读。同 Java 的 FormManifest.typed。
 */
export function typedValue(field: FieldDefinition, value: unknown): unknown {
  if (field.type === 'list' && field.fields && Array.isArray(value)) {
    const columns = field.fields;
    return value.map((row) =>
      isObject(row) ? Object.fromEntries(Object.entries(row).map(([k, v]) => [k, columns[k] ? typedValue(columns[k], v) : v])) : row,
    );
  }
  const kind = VALUE_KINDS[field.type];
  if (kind === undefined || typeof value !== 'string') return value;
  try {
    return parseWire(kind, value);
  } catch {
    return value;
  }
}

const isObject = (value: unknown): value is Readonly<Record<string, unknown>> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);


/** 不小于下限、不大于上限；没写限的不查 */
function range(value: unknown, field: FieldDefinition, errors: ValueError[], path: string): void {
  if (field.min !== undefined && compareValues(value, typedValue(field, field.min)) < 0) errors.push({ path, code: 'min' });
  if (field.max !== undefined && compareValues(value, typedValue(field, field.max)) > 0) errors.push({ path, code: 'max' });
}

/** 一个值合不合这个字段；空值（null、空文字、空数组）不查，必填由调用方另查 */
export function checkFieldValue(field: FieldDefinition, value: unknown): ValueError[] {
  const errors: ValueError[] = [];
  if (isBlank(value)) return errors;
  const self = '';
  switch (field.type) {
    case 'text': {
      if (typeof value !== 'string') return [{ path: self, code: 'type' }];
      if (field.minLength !== undefined && value.length < field.minLength) errors.push({ path: self, code: 'minLength' });
      if (field.maxLength !== undefined && value.length > field.maxLength) errors.push({ path: self, code: 'maxLength' });
      if (field.pattern !== undefined && !new RegExp(`^(?:${field.pattern})$`, 'u').test(value)) errors.push({ path: self, code: 'pattern' });
      return errors;
    }
    case 'number': {
      if (typeof value !== 'number' || !Number.isFinite(value)) return [{ path: self, code: 'type' }];
      if (field.integer && !Number.isInteger(value)) errors.push({ path: self, code: 'integer' });
      range(value, field, errors, self);
      return errors;
    }
    case 'decimal': {
      if (wireTypeOf(value) !== 'decimal') return [{ path: self, code: 'type' }];
      // Decimal 不留末尾的零：小数位就是有效的位数
      if (field.scale !== undefined && (value as { decimalPlaces(): number }).decimalPlaces() > field.scale) errors.push({ path: self, code: 'scale' });
      range(value, field, errors, self);
      return errors;
    }
    case 'boolean':
      return typeof value === 'boolean' ? errors : [{ path: self, code: 'type' }];
    case 'date':
    case 'datetime': {
      if (wireTypeOf(value) !== VALUE_KINDS[field.type]) return [{ path: self, code: 'type' }];
      range(value, field, errors, self);
      return errors;
    }
    case 'select': {
      const items = field.multiple ? value : [value];
      if (!Array.isArray(items) || items.some((item) => typeof item !== 'string' && typeof item !== 'number')) return [{ path: self, code: 'type' }];
      const options = Array.isArray(field.options) ? field.options : undefined;
      if (options && items.some((item) => !options.some((option) => option.value === item))) errors.push({ path: self, code: 'option' });
      return errors;
    }
    case 'people': {
      if (!Array.isArray(value) || value.some((p) => !isObject(p) || !isOrgId(p.id) || typeof p.type !== 'string')) {
        return [{ path: self, code: 'type' }];
      }
      if (field.types && value.some((p) => !field.types!.includes((p as { type: string }).type))) errors.push({ path: self, code: 'peopleType' });
      if (typeof field.max === 'number' && value.length > field.max) errors.push({ path: self, code: 'maxCount' });
      return errors;
    }
    case 'file': {
      if (!Array.isArray(value) || value.some((f) => !isObject(f) || typeof f.id !== 'string' || typeof f.name !== 'string' || typeof f.size !== 'number')) {
        return [{ path: self, code: 'type' }];
      }
      if (field.maxCount !== undefined && value.length > field.maxCount) errors.push({ path: self, code: 'maxCount' });
      if (field.maxSize !== undefined && value.some((f) => (f as { size: number }).size > field.maxSize!)) errors.push({ path: self, code: 'maxSize' });
      return errors;
    }
    case 'list': {
      if (!Array.isArray(value) || value.some((row) => !isObject(row))) return [{ path: self, code: 'type' }];
      if (field.minItems !== undefined && value.length < field.minItems) errors.push({ path: self, code: 'minItems' });
      if (field.maxItems !== undefined && value.length > field.maxItems) errors.push({ path: self, code: 'maxItems' });
      value.forEach((row, i) => {
        for (const [column, definition] of Object.entries(field.fields ?? {})) {
          for (const error of checkFieldValue(definition, (row as Record<string, unknown>)[column])) {
            errors.push({ path: `[${i}].${column}${error.path}`, code: error.code });
          }
        }
      });
      return errors;
    }
    default:
      // 接入方注册的字段类型：由注册的实现查
      return errors;
  }
}
