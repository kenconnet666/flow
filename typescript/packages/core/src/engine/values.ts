/**
 * 取值的几样小规则（同 Java 的 OrgIds、References、RefValues）：
 *   - orgIds：定义、表单里写的用户、角色、部门编号换成 bigint（`1001`、`"1001"`、`{ id: 1001, … }`、列表）；
 *   - readReference：按引用的名字取值——`form.amount` 表单字段、`sys.initiator` 引擎给的、`sys.round.节点` 轮次，其余是流程变量；
 *   - resolveArgs：定义里写的参数（解析器、动作的 args）中的 `{ ref }` 换成值。
 */
import { isBlank, isRef, toText, type Id } from '@zerodep-dev/flow-model';
import type { InstanceRecord, JsonRecord } from '../store/types.js';
import { badOrgId } from './errors.js';

/** 一个编号，或一组编号；空的值不算 */
export function orgIds(value: unknown): Id[] {
  if (Array.isArray(value)) return value.map(orgId).filter((id): id is Id => id !== null);
  const id = orgId(value);
  return id === null ? [] : [id];
}

/** 一个编号；空的值是 null；写的不是整数时报 `flow.definition-invalid` */
export function orgId(value: unknown): Id | null {
  if (isBlank(value)) return null;
  if (typeof value === 'object' && value !== null && !Array.isArray(value)) return orgId((value as { id?: unknown }).id);
  if (typeof value === 'bigint') return value;
  if (typeof value === 'number' && Number.isInteger(value)) return BigInt(value);
  const text = toText(value).trim();
  if (/^-?\d+$/.test(text)) return BigInt(text);
  if (/^-?\d+\.0*$/.test(text)) return BigInt(text.slice(0, text.indexOf('.')));
  throw badOrgId(value);
}

/** 在这个实例、这份表单的值上取 name 的值；没有时为 null */
export function readReference(name: string, instance: InstanceRecord, form: JsonRecord): unknown {
  if (name.startsWith('form.')) return pathOf(form, name.slice('form.'.length));
  if (name.startsWith('sys.round.')) return instance.state.round(name.slice('sys.round.'.length));
  if (name.startsWith('sys.')) return system(name.slice('sys.'.length), instance);
  if (name.startsWith('fn.')) throw new Error(`computed values (${name}) are not supported yet`);
  return pathOf(instance.variables, name);
}

/** 带点的名字按路径往对象里取（`buyer.name`） */
function pathOf(fields: JsonRecord, path: string): unknown {
  let value: unknown = fields;
  for (const step of path.split('.')) {
    if (typeof value !== 'object' || value === null || Array.isArray(value)) return null;
    value = (value as JsonRecord)[step];
  }
  return value ?? null;
}

/** `sys.` 下引擎给的只读值 */
function system(name: string, instance: InstanceRecord): unknown {
  switch (name) {
    case 'initiator':
      return instance.startedBy;
    case 'startedAt':
      return instance.startedAt;
    case 'instanceId':
      return instance.id;
    case 'businessKey':
      return instance.businessKey;
    default:
      return null;
  }
}

/** 换好引用的参数：对象、列表里的也换 */
export function resolveArgs(args: JsonRecord | undefined, read: (name: string) => unknown): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(args ?? {})) out[key] = resolveValue(value, read);
  return out;
}

function resolveValue(value: unknown, read: (name: string) => unknown): unknown {
  if (isRef(value)) return read(value.ref);
  if (Array.isArray(value)) return value.map((item) => resolveValue(item, read));
  if (typeof value === 'object' && value !== null) return resolveArgs(value as JsonRecord, read);
  return value;
}

/** 用户的 JSON（流转记录的附加数据）里的编号写成数：存进库、读出来、交给前端都是同一个值（同 Java 的 JsonNumbers） */
export function plainJson(value: unknown): unknown {
  if (typeof value === 'bigint') return Number(value);
  if (Array.isArray(value)) return value.map(plainJson);
  if (typeof value === 'object' && value !== null) {
    const out: Record<string, unknown> = {};
    for (const [key, item] of Object.entries(value)) out[key] = plainJson(item);
    return out;
  }
  return value;
}
