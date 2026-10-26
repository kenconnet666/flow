/**
 * 处理之外的协作（0006 的 3.1–3.3，同 Java 的 CollaborationHandler）：转办、委派、加签与减签、退回收件箱、催办、留言、抄送与读抄送、
 * 收件箱负责人指定承办人。动待办的命令同处理一样：锁住实例，重读待办，只有它的处理人能做（还在收件箱里时候选人先认领），
 * 节点上 allow 关掉的动作不能做；最后重算正在处理的节点、写流转记录。委派、加签生成的待办怎样回到原来的待办见 sub-items。
 */
import { FLOW_EVENT, type Id, type TaskNode, type WorkItem } from '@zerodep-dev/flow-model';
import type {
  AddSignersCommand,
  AssignCommand,
  CcCommand,
  CommandResult,
  CommentCommand,
  HandOverCommand,
  ReadCcCommand,
  ReleaseCommand,
  RemindCommand,
  RemoveSignersCommand,
} from '../command/types.js';
import type { Actor } from '../identity.js';
import { instantOf, plusSeconds } from '../time.js';
import { inTransaction, type EngineContext } from './context.js';
import { forbidden, invalid, notCandidate, notFound, notOpen } from './errors.js';
import { Execution } from './execution.js';
import { claimItem, requireActive, resultOf } from './handlers.js';
import { resumeIfSettled } from './sub-items.js';

/** 同一个待办多久内只催一次（秒） */
const REMIND_INTERVAL = 3600;

/** 转办：原来的待办取消，交给别人一份新的（会签的序号、权重照抄），自己不再参与 */
export function transfer(context: EngineContext, actor: Actor, command: HandOverCommand): Promise<CommandResult> {
  return onMyItem(context, actor, command.workItemId, async (run, item, task) => {
    requireAllowed(task.allow?.transfer !== false, item, 'transfer-not-allowed');
    requireSomeoneElse(actor, command.userId);
    await context.stores.workItems.cancel(item.id, run.now);
    run.closed.push(item);
    run.addEvent(FLOW_EVENT.WORK_ITEM_TRANSFERRED, task, { workItemId: item.id, comment: command.reason ?? null, data: to(context, command.userId) });
    await run.handOver(item, command.userId, 'transfer', null, 'open');
  });
}

/** 委派：原来的待办等着（waiting），被委派的人办完后回到自己手里 */
export function delegate(context: EngineContext, actor: Actor, command: HandOverCommand): Promise<CommandResult> {
  return onMyItem(context, actor, command.workItemId, async (run, item, task) => {
    requireAllowed(task.allow?.delegate !== false, item, 'delegate-not-allowed');
    requireSomeoneElse(actor, command.userId);
    await context.stores.workItems.changeState(item.id, 'open', 'waiting');
    run.addEvent(FLOW_EVENT.WORK_ITEM_DELEGATED, task, { workItemId: item.id, comment: command.reason ?? null, data: to(context, command.userId) });
    await run.handOver(item, command.userId, 'delegate', 'delegate', 'open');
  });
}

/** 加签：前加签时自己的待办等着、加的人先办；后加签时加的人的待办先生成但等着，自己办完才打开；并加时一起办 */
export function addSigners(context: EngineContext, actor: Actor, command: AddSignersCommand): Promise<CommandResult> {
  return onMyItem(context, actor, command.workItemId, async (run, item, task) => {
    requireAllowed(task.allow?.addSign !== false, item, 'add-sign-not-allowed');
    const mode = command.mode;
    if (!['before', 'after', 'parallel'].includes(mode)) throw invalid('mode', 'unknown-add-mode');
    const users = [...new Set(command.users)];
    if (users.length === 0) throw invalid('users', 'required');
    for (const user of users) requireSomeoneElse(actor, user);
    if (mode === 'before') await context.stores.workItems.changeState(item.id, 'open', 'waiting');
    // 名字存快照：流转记录上显示"加签：张三、李四"，不用再查组织
    run.addEvent(FLOW_EVENT.WORK_ITEM_SIGNERS_ADDED, task, {
      workItemId: item.id,
      comment: command.reason ?? null,
      data: { users, userNames: users.map((u) => context.org.userName(u)), mode },
    });
    for (const user of users) await run.handOver(item, user, 'addSign', mode, mode === 'after' ? 'waiting' : 'open');
  });
}

/** 减签：取消自己加的、还没办的人；前加签的人都取消了，待办回到自己手里 */
export function removeSigners(context: EngineContext, actor: Actor, command: RemoveSignersCommand): Promise<CommandResult> {
  return inTransaction(context, async () => {
    const { item, run } = await lock(context, actor, command.workItemId);
    if (item.assigneeId !== actor.id) throw notCandidate(item.id);
    const task = run.graph.node(item.node) as TaskNode;
    for (const child of await context.stores.workItems.children(item.id)) {
      const pending = child.state === 'open' || child.state === 'waiting';
      if (!pending || child.assigneeId === null || !command.users.includes(child.assigneeId)) continue;
      await context.stores.workItems.cancel(child.id, run.now);
      run.closed.push(child);
      run.addEvent(FLOW_EVENT.WORK_ITEM_SIGNERS_REMOVED, task, { workItemId: child.id, data: { userId: child.assigneeId, userName: child.assigneeName } });
    }
    await resumeIfSettled(context, run, item);
    await run.flush(null);
    return resultOf(run);
  });
}

/** 退回收件箱：认领了又不办，放回去；自己以后不能再认领这一条 */
export function release(context: EngineContext, actor: Actor, command: ReleaseCommand): Promise<CommandResult> {
  return inTransaction(context, async () => {
    const { item, run } = await lock(context, actor, command.workItemId);
    if (item.assignment !== 'claimed' || item.assigneeId !== actor.id) throw forbidden('workItem', item.id, 'not-claimed-by-you');
    if (!(await context.stores.workItems.release(item.id, actor.id))) throw notOpen(item.id);
    run.addEvent(FLOW_EVENT.WORK_ITEM_RELEASED, run.graph.node(item.node), { workItemId: item.id, comment: command.reason ?? null });
    await run.flush(null);
    return resultOf(run);
  });
}

/** 催办：给待办的处理人一个提醒（通知由接入方监听流转记录去发）；同一个待办一小时内只记一次；allow.remind 关掉的不催。不锁实例 */
export function remind(context: EngineContext, actor: Actor, command: RemindCommand): Promise<CommandResult> {
  return inTransaction(context, async () => {
    const run = await onInstance(context, actor, command.instanceId, true);
    const notBefore = plusSeconds(run.now, -REMIND_INTERVAL);
    for (const item of await context.stores.workItems.ofInstance(run.instance.id)) {
      if (item.state !== 'open' || (command.workItemId != null && command.workItemId !== item.id)) continue;
      const task = run.graph.node(item.node) as TaskNode;
      if (task.allow?.remind === false || !(await context.stores.workItems.remind(item.id, run.now, notBefore))) continue;
      run.addEvent(FLOW_EVENT.WORK_ITEM_REMINDED, task, { workItemId: item.id, comment: command.message ?? null });
    }
    await run.publish();
    return resultOf(run);
  });
}

/** 留言：记进流转记录，实例结束后也能留 */
export function comment(context: EngineContext, actor: Actor, command: CommentCommand): Promise<CommandResult> {
  if (!command.text?.trim()) throw invalid('text', 'required');
  return inTransaction(context, async () => {
    const run = await onInstance(context, actor, command.instanceId, false);
    run.addEvent(FLOW_EVENT.INSTANCE_COMMENTED, null, { comment: command.text });
    await run.publish();
    return resultOf(run);
  });
}

/** 收件箱负责人指定承办人：只有等我指定的待办能指定；改成直接落到那个人 */
export function assign(context: EngineContext, actor: Actor, command: AssignCommand): Promise<CommandResult> {
  if (command.userId == null) throw invalid('userId', 'required');
  return inTransaction(context, async () => {
    const { item, run } = await lock(context, actor, command.workItemId);
    const name = context.org.userName(command.userId);
    if (!(await context.stores.workItems.designate(item.id, actor.id, command.userId, name))) {
      throw forbidden('workItem', item.id, 'not-awaiting-your-designation');
    }
    run.addEvent(FLOW_EVENT.WORK_ITEM_ASSIGNED, run.graph.node(item.node), { workItemId: item.id, data: to(context, command.userId) });
    await run.flush(null);
    return resultOf(run);
  });
}

/** 抄送（知会）：每人一条抄送、记一条流转记录；实例结束后也能抄送。不锁实例 */
export function cc(context: EngineContext, actor: Actor, command: CcCommand): Promise<CommandResult> {
  if (command.users.length === 0) throw invalid('users', 'required');
  return inTransaction(context, async () => {
    const run = await onInstance(context, actor, command.instanceId, false);
    await run.copyTo(null, command.users, command.message ?? null);
    await run.publish();
    return resultOf(run);
  });
}

/** 读了抄送：一个实例抄送给我的都标成已读，返回标了几条 */
export function readCc(context: EngineContext, actor: Actor, command: ReadCcCommand): Promise<number> {
  return context.stores.ccs.markRead(command.instanceId, actor.id, instantOf(context.clock.now()));
}

// ───────── 共用的步骤 ─────────

/** 锁住实例、重读待办；还在收件箱里时候选人先认领；只有它的处理人能做；做完重算、写回 */
function onMyItem(
  context: EngineContext,
  actor: Actor,
  workItemId: Id,
  action: (run: Execution, item: WorkItem, task: TaskNode) => Promise<void>,
): Promise<CommandResult> {
  return inTransaction(context, async () => {
    const { item, run } = await lock(context, actor, workItemId);
    if (item.assigneeId === null) await claimItem(context, actor, item);
    else if (item.assigneeId !== actor.id) throw notCandidate(item.id);
    await action(run, item, run.graph.node(item.node) as TaskNode);
    await run.flush(null);
    return resultOf(run);
  });
}

/** 锁住待办所在的实例（版本加一），重读实例与待办：待办要打开着、实例要进行中 */
async function lock(context: EngineContext, actor: Actor, workItemId: Id): Promise<{ item: WorkItem; run: Execution }> {
  const found = await context.stores.workItems.find(workItemId);
  if (!found) throw notFound('workItem', workItemId);
  const rev = await context.stores.instances.lock(found.instanceId);
  if (rev === null) throw notFound('instance', found.instanceId);
  const instance = (await context.stores.instances.find(found.instanceId))!;
  requireActive(instance);
  const item = (await context.stores.workItems.find(workItemId))!;
  if (item.state !== 'open') throw notOpen(item.id);
  const form = (await context.stores.formData.read(instance.id))?.values ?? {};
  const definition = await context.definitions.ofInstance(instance);
  return { item, run: new Execution(context, actor, instantOf(context.clock.now()), definition, instance, form, rev) };
}

/** 不改实例的命令（催办、留言、抄送）：不锁，按实例现在的版本记流转记录；mustBeActive 时结束、暂停的实例报错 */
async function onInstance(context: EngineContext, actor: Actor, instanceId: Id, mustBeActive: boolean): Promise<Execution> {
  const instance = await context.stores.instances.find(instanceId);
  if (!instance) throw notFound('instance', instanceId);
  if (mustBeActive) requireActive(instance);
  const definition = await context.definitions.ofInstance(instance);
  return new Execution(context, actor, instantOf(context.clock.now()), definition, instance, {}, instance.rev);
}

/** 节点上关掉了这个动作 */
function requireAllowed(allowed: boolean, item: WorkItem, reason: string) {
  if (!allowed) throw forbidden('workItem', item.id, reason);
}

/** 转办、委派、加签要交给别人，不能是自己 */
function requireSomeoneElse(actor: Actor, userId: Id | null | undefined) {
  if (userId == null) throw invalid('userId', 'required');
  if (userId === actor.id) throw invalid('userId', 'yourself');
}

/** 流转记录的附加数据：交给谁（名字存快照） */
const to = (context: EngineContext, userId: Id) => ({ toUserId: userId, toUserName: context.org.userName(userId) });
