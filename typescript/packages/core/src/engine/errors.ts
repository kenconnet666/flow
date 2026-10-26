/**
 * 处理类里常抛的带业务错误码的错误（0006 第 8 节、0008），抛错的地方一行写完；参数是给前端文案填的文字。同 Java 的 FlowErrors。
 */
import { FlowError, type Id } from '@zerodep-dev/flow-model';
import type { InstanceRecord } from '../store/types.js';

/** 实例、待办、定义、表单不存在：what 是哪一种，id 是编号或标识 */
export const notFound = (what: string, id: unknown): FlowError => new FlowError('flow.not-found', { params: { [what]: String(id) } });

/** 不让做：reason 说明为什么（停用了、不是发起人……） */
export const forbidden = (target: string, id: unknown, reason: string): FlowError =>
  new FlowError('flow.forbidden', { params: { [target]: String(id), reason } });

/** 某个参数不对：例如结果不认识、意见必填 */
export const invalid = (field: string, code: string): FlowError =>
  new FlowError('flow.validation-failed', { message: `${field}: ${code}`, errors: [{ code, field }] });

/** 定义、表单里写的用户、角色、部门编号不是整数（多半是把角色写成了名字） */
export const badOrgId = (value: unknown): FlowError => new FlowError('flow.definition-invalid', { params: { orgId: String(value) } });

/** 待办已处理、已取消（重复提交） */
export const notOpen = (workItemId: Id): FlowError => new FlowError('flow.work-item-not-open', { params: { workItem: String(workItemId) } });

/** 别人已认领 */
export const alreadyClaimed = (workItemId: Id): FlowError => new FlowError('flow.already-claimed', { params: { workItem: String(workItemId) } });

/** 不是这个待办的处理人或候选 */
export const notCandidate = (workItemId: Id): FlowError => new FlowError('flow.not-candidate', { params: { workItem: String(workItemId) } });

/** 实例已结束、已暂停 */
export const notActive = (instance: InstanceRecord): FlowError =>
  new FlowError('flow.instance-not-active', { params: { instance: String(instance.id), status: instance.status } });
