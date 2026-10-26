/**
 * 条件树：比较一个字段，或用 all / any / not 组合。与组件库的 `Condition` 同形（`{ field, op, value }`），连线条件、
 * 表单的显示与必填条件（`visibleWhen`、`requiredWhen`）都用它（0005 的 6.2、0004 的 1.3）。值怎样比见 values.ts。
 *
 *   evaluateCondition({ all: [{ field: 'form.amount', op: 'gt', value: '10000' }] }, (field) => scope.read(field));
 *
 * 字段的值是数组时（多选、人员字段的编号列表），比较对数组里的每一项做：等于、是其中之一、包含、大小这些是"有一项满足"，
 * 不等于、不在其中、不包含是"没有一项满足"；有值、没有值看数组是不是空的。
 */
import { compareValues, isBlank, sameValue, toText } from './values.js';

/** 比较的方式，同组件库的 14 种 */
export const CONDITION_OPS = [
  'eq',
  'ne',
  'gt',
  'gte',
  'lt',
  'lte',
  'between',
  'in',
  'notIn',
  'contains',
  'notContains',
  'startsWith',
  'exists',
  'empty',
] as const;

/** 比较的方式 */
export type ConditionOp = (typeof CONDITION_OPS)[number];

/** 比较一个字段 */
export interface ConditionRule {
  /** 字段名：`form.字段`、`sys.名字`、流程变量名、`fn.名字`（0005 的 1.1） */
  readonly field: string;
  /** 比较的方式 */
  readonly op: ConditionOp;
  /** 比较的值；`between` 是 `[下限, 上限]`，`in` / `notIn` 是数组，`exists` / `empty` 没有 */
  readonly value?: unknown;
}

/** 条件：一条比较，或 all（全部满足）/ any（任一满足）/ not（不满足）组合 */
export type Condition =
  | ConditionRule
  | { readonly all: readonly Condition[] }
  | { readonly any: readonly Condition[] }
  | { readonly not: Condition };

/** 按字段名取值 */
export type ConditionReader = (field: string) => unknown;

/** 这种比较要不要值 */
export const needsValue = (op: ConditionOp): boolean => op !== 'exists' && op !== 'empty';

/** 一条比较已经写完整：要值的有值，`between` 至少一端有值 */
export function isRuleComplete(rule: ConditionRule): boolean {
  if (!needsValue(rule.op)) return true;
  if (rule.op === 'between') return Array.isArray(rule.value) && rule.value.some((end) => !isBlank(end));
  return !isBlank(rule.value);
}

/** 整棵树都写完整：没有没写完的比较、没有空的组（流程里发布时要求这样，0005 的 6.2） */
export function isConditionComplete(condition: Condition): boolean {
  if ('not' in condition) return isConditionComplete(condition.not);
  if ('all' in condition || 'any' in condition) {
    const items = 'all' in condition ? condition.all : condition.any;
    return items.length > 0 && items.every(isConditionComplete);
  }
  return isRuleComplete(condition);
}

/** 条件里用到的字段名，按出现的先后、不重复 */
export function conditionFields(condition: Condition): string[] {
  const fields = new Set<string>();
  const visit = (node: Condition): void => {
    if ('not' in node) visit(node.not);
    else if ('all' in node) node.all.forEach(visit);
    else if ('any' in node) node.any.forEach(visit);
    else fields.add(node.field);
  };
  visit(condition);
  return [...fields];
}

/**
 * 条件成不成立。没写完的比较、空的组不加条件（同组件库与 query 的 conditionBy），整棵树都不加条件时成立；
 * 流程定义在发布时就拒绝这样的条件，所以这条规则只在筛选这类场合起作用。
 */
export function evaluateCondition(condition: Condition, read: ConditionReader): boolean {
  return evaluate(condition, read) ?? true;
}

function evaluate(condition: Condition, read: ConditionReader): boolean | undefined {
  if ('not' in condition) {
    const inner = evaluate(condition.not, read);
    return inner === undefined ? undefined : !inner;
  }
  if ('all' in condition || 'any' in condition) {
    const results = ('all' in condition ? condition.all : condition.any)
      .map((item) => evaluate(item, read))
      .filter((result): result is boolean => result !== undefined);
    if (results.length === 0) return undefined;
    return 'all' in condition ? results.every(Boolean) : results.some(Boolean);
  }
  if (!isRuleComplete(condition)) return undefined;
  return matchesRule(condition, read(condition.field));
}

const lower = (value: unknown) => toText(value).toLowerCase();

/** 对一个（不是数组的）值，这条比较成不成立 */
function matchesOne(op: ConditionOp, actual: unknown, value: unknown): boolean {
  switch (op) {
    case 'eq':
      return sameValue(actual, value);
    case 'ne':
      return !sameValue(actual, value);
    case 'gt':
      return actual != null && compareValues(actual, value) > 0;
    case 'gte':
      return actual != null && compareValues(actual, value) >= 0;
    case 'lt':
      return actual != null && compareValues(actual, value) < 0;
    case 'lte':
      return actual != null && compareValues(actual, value) <= 0;
    case 'between': {
      const [low, high] = Array.isArray(value) ? value : [];
      if (actual == null) return false;
      return (isBlank(low) || compareValues(actual, low) >= 0) && (isBlank(high) || compareValues(actual, high) <= 0);
    }
    case 'in':
      return Array.isArray(value) && value.some((item) => sameValue(actual, item));
    case 'notIn':
      return !Array.isArray(value) || !value.some((item) => sameValue(actual, item));
    case 'contains':
      return lower(actual).includes(lower(value));
    case 'notContains':
      return !lower(actual).includes(lower(value));
    case 'startsWith':
      return lower(actual).startsWith(lower(value));
    case 'exists':
      return !isBlank(actual);
    case 'empty':
      return isBlank(actual);
  }
}

/** "没有一项满足"的比较：数组时要求每一项都满足对应的肯定比较不成立 */
const NEGATIVE: ReadonlySet<ConditionOp> = new Set(['ne', 'notIn', 'notContains']);

function matchesRule(rule: ConditionRule, actual: unknown): boolean {
  if (rule.op === 'exists' || rule.op === 'empty' || !Array.isArray(actual)) return matchesOne(rule.op, actual, rule.value);
  return NEGATIVE.has(rule.op)
    ? actual.every((item) => matchesOne(rule.op, item, rule.value))
    : actual.some((item) => matchesOne(rule.op, item, rule.value));
}
