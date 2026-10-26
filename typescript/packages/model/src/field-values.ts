/**
 * 一个字段的值合不合字段清单（docs/decisions/0004 的 1.3）：提交时（access.ts 的 mergeSubmission）与字段清单校验默认值时
 * （validate-manifest.ts）都用它。与 Java 的 `FieldValues` 同一套规则，两边跑 spec/access 与 spec/manifests 的样例。
 *
 * 只查形状与范围；`options: { source }` 的字典、接入方注册的字段类型由后端另查。
 */
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

const DECIMAL = /^[+-]?(\d+(\.\d*)?|\.\d+)$/;
const DATE = /^(\d{4})-(\d{2})-(\d{2})$/;
const DATETIME = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(:(\d{2})(\.\d{1,3})?)?Z$/;

/** 公历日期是否存在（2026-02-30 不存在） */
function validDate(year: number, month: number, day: number): boolean {
  if (month < 1 || month > 12 || day < 1) return false;
  const leap = (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0;
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][month - 1]!;
  return day <= days;
}

function isDate(value: unknown): boolean {
  const m = typeof value === 'string' ? DATE.exec(value) : null;
  return m !== null && validDate(Number(m[1]), Number(m[2]), Number(m[3]));
}

function isDateTime(value: unknown): boolean {
  const m = typeof value === 'string' ? DATETIME.exec(value) : null;
  if (m === null || !validDate(Number(m[1]), Number(m[2]), Number(m[3]))) return false;
  return Number(m[4]) < 24 && Number(m[5]) < 60 && (m[7] === undefined || Number(m[7]) < 60);
}

const isObject = (value: unknown): value is Readonly<Record<string, unknown>> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

/** 小数位数：末尾的零不算 */
const scaleOf = (text: string) => (text.split('.')[1] ?? '').replace(/0+$/, '').length;

/** 不小于下限、不大于上限；没写限的不查 */
function range(value: unknown, field: FieldDefinition, errors: ValueError[], path: string): void {
  if (field.min !== undefined && compareValues(value, field.min) < 0) errors.push({ path, code: 'min' });
  if (field.max !== undefined && compareValues(value, field.max) > 0) errors.push({ path, code: 'max' });
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
      if (typeof value !== 'string' || !DECIMAL.test(value.trim())) return [{ path: self, code: 'type' }];
      if (field.scale !== undefined && scaleOf(value.trim()) > field.scale) errors.push({ path: self, code: 'scale' });
      range(value, field, errors, self);
      return errors;
    }
    case 'boolean':
      return typeof value === 'boolean' ? errors : [{ path: self, code: 'type' }];
    case 'date':
    case 'datetime': {
      if (!(field.type === 'date' ? isDate(value) : isDateTime(value))) return [{ path: self, code: 'type' }];
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
