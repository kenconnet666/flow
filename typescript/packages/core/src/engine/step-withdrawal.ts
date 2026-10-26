/**
 * 处理人撤回刚处理的一步（0006 的 3.3 withdrawStep，同 Java 的 StepWithdrawal）。处理时记下了处理之前的快照，撤回要同时满足：
 *   - 这个待办是我处理的、节点上 allow.withdraw 没关、实例进行中；
 *   - 处理之后实例再没被动过（版本没变）；
 *   - 那一步只走过人工节点、网关、抄送节点；
 *   - 那一步生成的待办都还没人认领、没人处理。
 * 撤回时取消那一步生成的待办，运行状态、流程变量、表单的值恢复成快照，待办回到我手里重新处理。
 */
import { FLOW_EVENT, type Id, type ProcessGraph, type WorkItem } from '@zerodep-dev/flow-model';
import type { CommandResult } from '../command/types.js';
import type { Actor } from '../identity.js';
import type { InstanceRecord, StepSnapshot } from '../store/types.js';
import { instantOf } from '../time.js';
import { inTransaction, type EngineContext } from './context.js';
import { forbidden, notFound } from './errors.js';
import { Execution } from './execution.js';
import { resultOf } from './handlers.js';

/** 那一步只走过这些节点时才能撤回 */
const UNDOABLE = new Set(['task', 'exclusive', 'parallel', 'inclusive', 'cc', 'end']);

/** 撤回刚处理的一步的命令 */
export interface WithdrawStepCommand {
  readonly workItemId: Id;
  readonly reason?: string | null;
}

/** 撤回；不能撤回时报 `flow.forbidden` */
export function withdrawStep(context: EngineContext, actor: Actor, command: WithdrawStepCommand): Promise<CommandResult> {
  return inTransaction(context, async () => {
    const found = await context.stores.workItems.find(command.workItemId);
    if (!found) throw notFound('workItem', command.workItemId);
    const rev = (await context.stores.instances.lock(found.instanceId))!;
    const instance = (await context.stores.instances.find(found.instanceId))!;
    const item = (await context.stores.workItems.find(found.id))!;
    const definition = await context.definitions.ofInstance(instance);
    // 锁住实例时版本加了一：快照要是上一个版本的
    const snapshot = await check(context, actor, item, definition.graph, instance, rev - 1);
    if (!snapshot) throw forbidden('workItem', item.id, 'cannot-withdraw-step');
    const run = new Execution(context, actor, instantOf(context.clock.now()), definition, instance, snapshot.form, rev);
    for (const next of await createdAt(context, instance.id, snapshot.rev)) {
      await context.stores.workItems.cancel(next.id, run.now);
      run.closed.push(next);
      run.addEvent(FLOW_EVENT.WORK_ITEM_CANCELED, run.graph.node(next.node), { workItemId: next.id });
    }
    run.restore(snapshot.state, snapshot.variables);
    if (definition.manifest) await context.stores.formData.update(instance.id, snapshot.form);
    await context.stores.workItems.reopen(item.id);
    await context.stores.steps.remove(instance.id);
    run.addEvent(FLOW_EVENT.WORK_ITEM_WITHDRAWN, run.graph.node(item.node), { outcome: item.outcome, workItemId: item.id, comment: command.reason ?? null });
    await run.flush(null);
    return resultOf(run);
  });
}

/** 我现在能不能撤回这一步（待办详情上的"撤回"按钮）：同撤回的条件，实例的版本按现在的 */
export async function canWithdrawStep(context: EngineContext, actor: Actor, item: WorkItem, graph: ProcessGraph, instance: InstanceRecord): Promise<boolean> {
  return (await check(context, actor, item, graph, instance, instance.rev)) !== null;
}

/** 能撤回时返回快照；rev 是处理那一步时实例应有的版本 */
async function check(context: EngineContext, actor: Actor, item: WorkItem, graph: ProcessGraph, instance: InstanceRecord, rev: number): Promise<StepSnapshot | null> {
  if (item.state !== 'done' || item.completedBy !== actor.id) return null;
  if (instance.status !== 'active') return null;
  const task = graph.node(item.node);
  if (task?.type !== 'task' || task.allow?.withdraw === false) return null;
  const snapshot = await context.stores.steps.find(instance.id);
  if (!snapshot || snapshot.workItemId !== item.id || snapshot.rev !== rev) return null;
  const step = (await context.stores.events.timeline(instance.id)).filter((e) => e.instanceRev === rev);
  const simple = step
    .filter((e) => e.type === FLOW_EVENT.NODE_ENTERED)
    .every((e) => e.node !== null && UNDOABLE.has(graph.node(e.node)?.type ?? ''));
  const untouched = (await createdAt(context, instance.id, rev)).every((w) => (w.state === 'open' || w.state === 'waiting') && w.assignment !== 'claimed');
  return simple && untouched ? snapshot : null;
}

/** 那一步生成的待办 */
async function createdAt(context: EngineContext, instanceId: Id, rev: number): Promise<WorkItem[]> {
  const ids = new Set(
    (await context.stores.events.timeline(instanceId))
      .filter((e) => e.instanceRev === rev && e.type === FLOW_EVENT.WORK_ITEM_CREATED && e.workItemId !== null)
      .map((e) => e.workItemId!),
  );
  return (await context.stores.workItems.ofInstance(instanceId)).filter((w) => ids.has(w.id));
}
