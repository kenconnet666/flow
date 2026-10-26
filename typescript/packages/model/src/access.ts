/**
 * 表单字段在一个节点上的最终状态（docs/decisions/0004 的 4.2），以及提交时怎样合并（4.5）。前端渲染、后端下发与收提交都按它，
 * 与 Java 的 `FormAccess` 同一套规则，两边跑 spec/access 的样例。
 *
 * 合算的顺序：
 * 1. 节点上是 `hidden` → 隐藏；
 * 2. 字段的 `visibleWhen` 不成立 → 隐藏（值丢掉）；
 * 3. 节点上是 `readonly` → 只读，不算必填（这一步填不了）；
 * 4. 节点上是 `required` → 必填；是 `editable` → 按清单的 `required`、`requiredWhen`。
 * 明细表的列没单独写时跟着明细表；明细表隐藏时列都隐藏；明细表是 `editable` / `required` 时才能增删行。
 */
import { evaluateCondition, type ConditionReader } from './condition.js';
import type { FieldAccess, NodeFormAccess } from './definition.js';
import { checkFieldValue, type ValueProblem } from './field-values.js';
import type { FieldDefinition, FormManifest } from './manifest.js';
import { isBlank } from './values.js';

/** 一个字段在这个节点上的状态 */
export interface FieldState {
  /** 显示 */
  readonly visible: boolean;
  /** 能改；明细表能改时才能增删行 */
  readonly editable: boolean;
  /** 必填 */
  readonly required: boolean;
  /**
   * 不显示的原因：`access` 是节点上的权限（值照旧存着，只是这个人看不到），`condition` 是显示条件不成立（值丢掉）
   */
  readonly hiddenBy?: 'access' | 'condition';
}

/** 合算时的输入 */
export interface AccessInput {
  /** 节点上的权限；不在待办里看表单的人传流程级的 formView（0004 的 4.3） */
  readonly access?: NodeFormAccess | undefined;
  /** 是不是发起节点：没写 default 时发起节点 editable、其他节点 readonly */
  readonly isStart?: boolean | undefined;
  /** 条件里按字段名取值（`form.字段` 等） */
  readonly read: ConditionReader;
}

const BY_ACCESS: FieldState = { visible: false, editable: false, required: false, hiddenBy: 'access' };
const BY_CONDITION: FieldState = { visible: false, editable: false, required: false, hiddenBy: 'condition' };

/** 每个字段（明细表的列写成 `列表.列`）在这个节点上的状态，按清单里的先后 */
export function fieldStates(manifest: FormManifest, input: AccessInput): Map<string, FieldState> {
  const states = new Map<string, FieldState>();
  const fallback: FieldAccess = input.access?.default ?? (input.isStart ? 'editable' : 'readonly');
  const own = (path: string) => input.access?.fields?.[path];
  for (const [name, field] of Object.entries(manifest)) {
    const access = own(name) ?? fallback;
    const state = stateOf(field, access, input.read);
    states.set(name, state);
    if (field.type !== 'list' || !field.fields) continue;
    for (const [column, definition] of Object.entries(field.fields)) {
      const path = `${name}.${column}`;
      states.set(path, state.visible ? stateOf(definition, own(path) ?? access, input.read) : state);
    }
  }
  return states;
}

function stateOf(field: FieldDefinition, access: FieldAccess, read: ConditionReader): FieldState {
  if (access === 'hidden') return BY_ACCESS;
  if (field.visibleWhen && !evaluateCondition(field.visibleWhen, read)) return BY_CONDITION;
  if (access === 'readonly') return { visible: true, editable: false, required: false };
  const required =
    access === 'required' || field.required === true || (field.requiredWhen !== undefined && evaluateCondition(field.requiredWhen, read));
  return { visible: true, editable: true, required };
}

/** 提交时一个字段的问题 */
export interface SubmissionError {
  /** 字段路径；明细表的列带行号：`lines[0].price` */
  readonly field: string;
  /** `required`：必填没填 */
  readonly code: 'required' | ValueProblem;
}

/** 合并的结果 */
export interface Submission {
  /** 合并后要存下的表单的值 */
  readonly values: Record<string, unknown>;
  readonly errors: readonly SubmissionError[];
}

type Row = Readonly<Record<string, unknown>>;

/** 一个字段合并后的值：能改的取提交的，看得到但不能改、或节点上隐藏的用存着的，显示条件不成立的丢掉 */
function pick(state: FieldState, stored: unknown, submitted: unknown): unknown {
  if (state.hiddenBy === 'condition') return null;
  return (state.editable ? submitted : stored) ?? null;
}

/**
 * 收提交（0004 的 4.5）：只取能改的字段，其余用存着的值；被显示条件藏起来的字段值丢掉（存成 null）；再查必填。
 * 能改的字段没有提交（键不在）时也用存着的值：只提交改了的字段不会把别的清掉；要清空就明写 null。
 * 明细表能增删行时取提交上来的行，不能改的列按行号用存着的值；不能增删行时行数用存着的，只取能改的列。
 */
export function mergeSubmission(manifest: FormManifest, states: ReadonlyMap<string, FieldState>, stored: Row, submitted: Row): Submission {
  const values: Record<string, unknown> = {};
  const errors: SubmissionError[] = [];
  for (const [name, field] of Object.entries(manifest)) {
    const state = states.get(name) ?? BY_ACCESS;
    // 能改的字段没有提交（键不在）时用存着的值：只提交改了的字段不会把别的清掉；要清空就明写 null
    const given = name in submitted ? submitted[name] : stored[name];
    if (field.type === 'list' && field.fields && state.visible) {
      values[name] = mergeRows(name, field.fields, state, states, stored[name], given, errors);
    } else {
      values[name] = pick(state, stored[name], given);
    }
    if (state.required && isBlank(values[name])) errors.push({ field: name, code: 'required' });
    else if (state.editable) {
      // 明细表在这里只查行数，各个格子在 mergeRows 里按列的权限查
      const { fields: _columns, ...shape } = field;
      for (const e of checkFieldValue(field.type === 'list' ? shape : field, values[name])) errors.push({ field: name + e.path, code: e.code });
    }
  }
  return { values, errors };
}

function mergeRows(
  name: string,
  columns: FormManifest,
  state: FieldState,
  states: ReadonlyMap<string, FieldState>,
  stored: unknown,
  submitted: unknown,
  errors: SubmissionError[],
): Row[] {
  const storedRows: readonly Row[] = Array.isArray(stored) ? stored : [];
  const submittedRows: readonly Row[] = Array.isArray(submitted) ? submitted : [];
  const count = state.editable ? submittedRows.length : storedRows.length;
  return Array.from({ length: count }, (_, i) => {
    const row: Record<string, unknown> = {};
    for (const column of Object.keys(columns)) {
      const columnState = states.get(`${name}.${column}`) ?? BY_ACCESS;
      row[column] = pick(columnState, storedRows[i]?.[column], submittedRows[i]?.[column]);
      if (columnState.required && isBlank(row[column])) errors.push({ field: `${name}[${i}].${column}`, code: 'required' });
      else if (columnState.editable) {
        for (const e of checkFieldValue(columns[column]!, row[column])) errors.push({ field: `${name}[${i}].${column}${e.path}`, code: e.code });
      }
    }
    return row;
  });
}
