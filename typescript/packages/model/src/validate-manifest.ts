/**
 * 字段清单自身的校验（docs/decisions/0004 的 1.2、1.3）：表单保存、发布时跑，与 Java 的 `ManifestValidator` 同一套规则，
 * 两边跑 spec/manifests 的样例。问题的形状同流程定义的校验（validate.ts 的 Problem），位置写在 `field` 上。
 *
 *   validateManifest(manifest);
 *   // → [{ code: 'field-name-invalid', severity: 'error', field: 'Amount' }]
 */
import { conditionFields, isConditionComplete, type Condition } from './condition.js';
import { checkFieldValue } from './field-values.js';
import { manifestField, type FieldDefinition, type FormManifest } from './manifest.js';
import type { Problem } from './validate.js';
import { compareValues } from './values.js';

/** 内置的字段类型 */
export const BUILTIN_FIELD_TYPES: ReadonlySet<string> = new Set(['text', 'number', 'decimal', 'boolean', 'date', 'datetime', 'select', 'people', 'file', 'list']);

/** 字段名的写法：小写字母开头，只有字母和数字，最长 64 */
const FIELD_NAME = /^[a-z][A-Za-z0-9]{0,63}$/;

/** 校验字段清单时能用到的外部信息 */
export interface ManifestContext {
  /** 接入方注册的字段类型；不给时不认内置以外的类型 */
  readonly isFieldType?: (type: string) => boolean;
}

/** 表单条件里能用的 `sys.` 名字（表单的条件在前端算，流程变量不发给浏览器，0004 的 5.5） */
const SYSTEM_NAMES: ReadonlySet<string> = new Set(['initiator', 'startedAt', 'instanceId', 'businessKey']);

/** 必须是非负整数的属性 */
const COUNTS = ['minLength', 'maxLength', 'scale', 'maxCount', 'maxSize', 'minItems', 'maxItems'] as const;

/** 校验字段清单 */
export function validateManifest(manifest: FormManifest, context: ManifestContext = {}): Problem[] {
  const problems: Problem[] = [];
  const report = (code: string, field: string, params?: Record<string, string>) =>
    problems.push({ code, severity: 'error', field, ...(params ? { params } : {}) });
  const visit = (fields: FormManifest, prefix: string, inList: boolean) => {
    for (const [name, field] of Object.entries(fields)) {
      const path = prefix + name;
      checkField(manifest, field, path, name, context, report);
      if (field.type === 'list') {
        if (inList) report('list-nested', path);
        else if (!field.fields || Object.keys(field.fields).length === 0) report('list-no-columns', path);
        else visit(field.fields, `${path}.`, true);
      }
    }
  };
  visit(manifest, '', false);
  return problems;
}

type Report = (code: string, field: string, params?: Record<string, string>) => void;

function checkField(
  manifest: FormManifest,
  field: FieldDefinition,
  path: string,
  name: string,
  context: ManifestContext,
  report: Report,
): void {
  if (!FIELD_NAME.test(name)) report('field-name-invalid', path);
  if (typeof field.label !== 'string' || field.label.trim() === '') report('field-label-missing', path);
  if (!BUILTIN_FIELD_TYPES.has(field.type) && context.isFieldType?.(field.type) !== true) report('field-type-unknown', path, { type: field.type });

  for (const attribute of COUNTS) {
    const value = field[attribute];
    if (value !== undefined && !(typeof value === 'number' && Number.isInteger(value) && value >= 0)) report('attribute-invalid', path, { attribute });
  }
  if (field.type === 'people' && field.max !== undefined && !(typeof field.max === 'number' && Number.isInteger(field.max) && field.max >= 1)) {
    report('attribute-invalid', path, { attribute: 'max' });
  }
  if (field.pattern !== undefined && !compiles(field.pattern)) report('pattern-invalid', path);
  checkRanges(field, path, report);

  if (field.type === 'select' && Array.isArray(field.options)) {
    if (field.options.length === 0) report('options-empty', path);
    const values = field.options.map((o) => o.value);
    for (const value of new Set(values.filter((v, i) => values.indexOf(v) !== i))) report('option-duplicate', path, { value: String(value) });
  }

  for (const which of ['visibleWhen', 'requiredWhen'] as const) {
    const condition = field[which];
    if (condition) checkCondition(manifest, condition, path, which, report);
  }
  if (field.default !== undefined && field.default !== null && checkFieldValue(field, field.default).length > 0) {
    report('default-invalid', path);
  }
}

function compiles(pattern: string): boolean {
  try {
    new RegExp(pattern, 'u');
    return true;
  } catch {
    return false;
  }
}

/** 范围的属性：类型对、下限不大于上限 */
function checkRanges(field: FieldDefinition, path: string, report: Report): void {
  if (field.minLength !== undefined && field.maxLength !== undefined && field.minLength > field.maxLength) report('range-invalid', path, { attribute: 'length' });
  if (field.minItems !== undefined && field.maxItems !== undefined && field.minItems > field.maxItems) report('range-invalid', path, { attribute: 'items' });
  if (!['number', 'decimal', 'date', 'datetime'].includes(field.type)) return;
  // 范围本身要是这种类型的合法值（decimal 的上下限是文字，date 的是日期）
  const plain: FieldDefinition = { label: field.label, type: field.type };
  for (const attribute of ['min', 'max'] as const) {
    const bound = field[attribute];
    if (bound !== undefined && checkFieldValue(plain, bound).length > 0) report('attribute-invalid', path, { attribute });
  }
  if (field.min !== undefined && field.max !== undefined && compareValues(field.min, field.max) > 0) report('range-invalid', path, { attribute: 'value' });
}

/** 表单条件：写完整、不用 fn.、只引用表单字段与 sys. 名字 */
function checkCondition(manifest: FormManifest, condition: Condition, path: string, which: string, report: Report): void {
  if (!isConditionComplete(condition)) report('condition-incomplete', path, { condition: which });
  for (const ref of conditionFields(condition)) {
    if (ref.startsWith('fn.')) report('condition-fn-in-form', path, { condition: which, ref });
    else if (ref.startsWith('sys.')) {
      if (!SYSTEM_NAMES.has(ref.slice(4))) report('ref-unknown', path, { condition: which, ref });
    } else if (!ref.startsWith('form.') || !manifestField(manifest, ref.slice(5))) {
      report('ref-unknown', path, { condition: which, ref });
    }
  }
}
