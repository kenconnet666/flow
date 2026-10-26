/**
 * 管理员命令（0006 的 3.4，同 Java 的 AdminHandler）：终止、暂停与恢复、跳转、改派、失败的作业重试或跳过。谁能做由 permissionPolicy 定；
 * 引擎只管实例的状态对不对：已结束的实例什么也不能做，暂停的实例只能恢复或终止。都先锁住实例，做完写回、写流转记录。
 */
import { FLOW_EVENT, type ProcessGraph, type ServiceNode } from '@zerodep-dev/flow-model';
import type { CommandResult, InstanceCommand, JumpCommand, HandOverCommand, RetryJobCommand, SkipJobCommand } from '../command/types.js';
import type { Actor } from '../identity.js';
import { JOB_STATE, type InstanceRecord } from '../store/types.js';
import { instantOf } from '../time.js';
import { inTransaction, type EngineContext } from './context.js';
import { forbidden, invalid, notActive, notFound, notOpen } from './errors.js';
import { Execution } from './execution.js';
import { resultOf } from './handlers.js';
import { JOB, nodeOf, stillWaiting } from './jobs.js';

/** 终止：打开着的待办都取消，实例结束，结果是 terminated（进行中、暂停的都能终止） */
export function terminate(context: EngineContext, actor: Actor, command: InstanceCommand): Promise<CommandResult> {
  return onInstance(context, actor, command.instanceId, false, (run) => run.finish('terminated', 'terminated', FLOW_EVENT.INSTANCE_TERMINATED, command.reason ?? null));
}

/** 暂停：实例停在原地，待办都不能处理 */
export function suspend(context: EngineContext, actor: Actor, command: InstanceCommand): Promise<CommandResult> {
  return onInstance(context, actor, command.instanceId, true, async (run) => run.changeStatus('suspended', FLOW_EVENT.INSTANCE_SUSPENDED, command.reason ?? null));
}

/** 恢复：暂停的实例接着走 */
export function resume(context: EngineContext, actor: Actor, command: InstanceCommand): Promise<CommandResult> {
  return onInstance(context, actor, command.instanceId, false, async (run, instance) => {
    if (instance.status !== 'suspended') throw forbidden('instance', instance.id, 'not-suspended');
    run.changeStatus('active', FLOW_EVENT.INSTANCE_RESUMED, null);
  });
}

/** 跳转：原来还没办完的待办都取消，从指定的节点重新走（开始节点不行） */
export function jump(context: EngineContext, actor: Actor, command: JumpCommand): Promise<CommandResult> {
  return onInstance(context, actor, command.instanceId, true, async (run) => {
    const node = run.graph.node(command.node);
    if (!node || node.type === 'start') throw invalid('node', 'unknown-node');
    await run.jumpTo(node.id, command.reason ?? null);
  });
}

/** 改派：待办直接落到这个人，候选删掉 */
export async function reassign(context: EngineContext, actor: Actor, command: HandOverCommand): Promise<CommandResult> {
  if (command.userId == null) throw invalid('userId', 'required');
  const found = await context.stores.workItems.find(command.workItemId);
  if (!found) throw notFound('workItem', command.workItemId);
  return onInstance(context, actor, found.instanceId, true, async (run) => {
    const item = (await context.stores.workItems.find(found.id))!;
    if (item.state !== 'open') throw notOpen(item.id);
    const name = context.org.userName(command.userId);
    if (!(await context.stores.workItems.reassign(item.id, command.userId, name))) throw notOpen(item.id);
    run.addEvent(FLOW_EVENT.WORK_ITEM_REASSIGNED, run.graph.node(item.node), {
      workItemId: item.id,
      comment: command.reason ?? null,
      data: { toUserId: command.userId, toUserName: name },
    });
  });
}

/** 失败的作业重新来过：改回等着做、马上到期；不是失败的作业时报 `flow.not-found` */
export async function retryJob(context: EngineContext, _actor: Actor, command: RetryJobCommand): Promise<boolean> {
  if (!(await context.stores.jobs.revive(command.jobId, instantOf(context.clock.now())))) throw notFound('job', command.jobId);
  return true;
}

/**
 * 跳过失败的作业：异步的自动节点不再做动作，按指定的结果往下走，作业删掉。只有自动节点的作业能跳过，
 * 实例要进行中（暂停的先恢复）、还停在那个节点的那一轮上；结果要是节点出边上写的，不填时要有一条没写结果的出边。
 */
export async function skipJob(context: EngineContext, actor: Actor, command: SkipJobCommand): Promise<CommandResult> {
  const job = await context.stores.jobs.find(command.jobId);
  if (!job || job.state !== JOB_STATE.FAILED) throw notFound('job', command.jobId);
  if (job.kind !== JOB.ACTION) throw invalid('jobId', 'not-skippable');
  const outcome = command.outcome?.trim() ? command.outcome : null;
  return onInstance(context, actor, job.instanceId, true, async (run, instance) => {
    if (!stillWaiting(job, instance.state)) throw forbidden('job', job.id, 'not-waiting');
    const service = run.graph.node(nodeOf(job)!) as ServiceNode;
    if (outcome === null ? !hasPlainEdge(run.graph, service.id) : !skipOutcomes(run.graph, service.id).includes(outcome)) {
      throw invalid('outcome', outcome === null ? 'required' : 'unknown-outcome');
    }
    await context.stores.jobs.done(job.id);
    await run.skipAction(service, job.id, outcome, command.reason ?? null);
  });
}

/** 跳过时能选的结果：节点出边上写的，按出边的先后、去重 */
export function skipOutcomes(graph: ProcessGraph, node: string): string[] {
  return [...new Set(graph.outgoing(node).flatMap((edge) => (edge.outcome === undefined ? [] : [edge.outcome])))];
}

/** 有没写结果的出边（跳过时可以不选结果） */
const hasPlainEdge = (graph: ProcessGraph, node: string) => graph.outgoing(node).some((edge) => edge.outcome === undefined);

/** 锁住实例（版本加一）再做：已结束的实例报 instance-not-active；mustBeActive 时暂停的也报。做完写回 */
function onInstance(
  context: EngineContext,
  actor: Actor,
  instanceId: bigint,
  mustBeActive: boolean,
  action: (run: Execution, instance: InstanceRecord) => Promise<void>,
): Promise<CommandResult> {
  return inTransaction(context, async () => {
    const rev = await context.stores.instances.lock(instanceId);
    if (rev === null) throw notFound('instance', instanceId);
    const instance = (await context.stores.instances.find(instanceId))!;
    const active = instance.status === 'active';
    const suspended = instance.status === 'suspended';
    if (mustBeActive ? !active : !(active || suspended)) throw notActive(instance);
    const form = (await context.stores.formData.read(instanceId))?.values ?? {};
    const definition = await context.definitions.ofInstance(instance);
    const run = new Execution(context, actor, instantOf(context.clock.now()), definition, instance, form, rev);
    await action(run, instance);
    await run.flush(null);
    return resultOf(run);
  });
}

