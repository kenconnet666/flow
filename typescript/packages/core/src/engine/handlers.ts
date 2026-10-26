/**
 * 发起、认领、处理、撤回（0006 的 3.1–3.3，同 Java 的 StartHandler、ClaimHandler、CompleteHandler、WithdrawHandler），各在一个事务里：
 *   - 发起：找到流程最新发布的版本（停用了、不在发起人名单里不让发起）→ 写实例拿编号 → 收开始节点上的表单 → 从开始节点往前走 → 写回；
 *   - 认领：不推进实例，不锁实例行；先到先得靠带条件的更新（`state = 'open' AND assignee_id IS NULL`）；
 *   - 处理：锁住实例行 → 重读待办（重复提交报 work-item-not-open；还在收件箱里的，候选人处理就是先认领）→ 核对结果与意见 →
 *     收表单、问守卫 → 待办改成已处理 → 往下走（委派加签的回到原来的待办；会签先看结束了没有）→ 写回；
 *   - 撤回：发起人按定义的 withdraw 规则撤回整个实例，取消还开着的待办。
 */
import {
  FLOW_EVENT,
  FlowError,
  OUTCOME_LABEL,
  type ErrorDetail,
  type Id,
  type Outcome,
  type TaskNode,
  type WorkItem,
} from '@zerodep-dev/flow-model';
import type { ClaimCommand, CommandResult, CompleteCommand, StartCommand, WithdrawCommand } from '../command/types.js';
import { actorIdOf, actorName, actorNameOf, type Actor } from '../identity.js';
import { RunState } from '../run-state.js';
import { instanceView, STATUS, type InstanceRecord, type JsonRecord } from '../store/types.js';
import { instantOf } from '../time.js';
import { isCandidate, isStarter } from './assignees.js';
import { inTransaction, type EngineContext } from './context.js';
import { decide } from './countersign.js';
import type { LoadedDefinition } from './definitions.js';
import { alreadyClaimed, forbidden, invalid, notActive, notCandidate, notFound, notOpen } from './errors.js';
import { Execution } from './execution.js';
import { mergeForm } from './form-submission.js';
import { checkReturnTarget } from './return-targets.js';
import { childDone, holdsParent } from './sub-items.js';

/** 命令的结果：实例、新生成与结束的待办、流转记录（flush 之后） */
export const resultOf = (run: Execution): CommandResult => ({
  instance: instanceView(run.instance),
  created: run.created,
  closed: run.closed,
  events: run.events,
});

/** 选人的写法换成 Map */
const picksOf = (picks: Readonly<Record<string, readonly Id[]>> | undefined) => new Map(Object.entries(picks ?? {}));

/** 实例已结束、已暂停时不能再动它的待办 */
export function requireActive(instance: InstanceRecord): void {
  if (instance.status !== 'active') throw notActive(instance);
}

// ───────── 发起 ─────────

/** 发起；出错时抛带业务错误码的错误，整个事务回滚 */
export function start(context: EngineContext, actor: Actor, command: StartCommand): Promise<CommandResult> {
  return inTransaction(context, async () => {
    const definition = await latestActive(context, command.key);
    if (!isStarter(actor, definition.model.starters)) throw forbidden('process', command.key, 'not-a-starter');
    const run = await launch(context, definition, actor, {
      starterId: actor.id,
      starterName: actorName(actor),
      businessKey: command.businessKey ?? null,
      priority: command.priority ?? 0,
      variables: command.variables ?? {},
      form: command.form ?? {},
      picks: picksOf(command.picks),
    });
    await run.flush(command.title ?? null);
    return resultOf(run);
  });
}

/** 流程最新发布的版本；流程不存在、停用了时报错 */
export async function latestActive(context: EngineContext, key: string): Promise<LoadedDefinition> {
  const process = await context.stores.definitions.process(key);
  if (!process) throw notFound('process', key);
  if (process.status !== STATUS.ACTIVE) throw forbidden('process', key, 'disabled');
  const latest = await context.stores.definitions.latest(key);
  if (!latest) throw notFound('process', key);
  return context.definitions.of(latest);
}

/**
 * 发起时给的东西。
 *
 * @param fullForm 表单的值原样存（子流程按 input 给的值），否则只收开始节点上能改的字段
 * @param parent   子实例记下的父实例；不是子流程时不给
 */
export interface Launch {
  readonly starterId: Id | null;
  readonly starterName: string | null;
  readonly businessKey: string | null;
  readonly priority: number;
  readonly variables: JsonRecord;
  readonly form: JsonRecord;
  readonly picks: ReadonlyMap<string, readonly Id[]>;
  readonly fullForm?: boolean;
  readonly parent?: ChildOf;
}

/**
 * 子实例记下的父实例。
 *
 * @param node   父实例里调用它的节点
 * @param round  那个节点是第几轮
 * @param root   最外层的实例
 * @param caller 这个命令里调用它的父实例的推进：子实例马上就结束时直接回到它
 */
export interface ChildOf {
  readonly instanceId: Id;
  readonly node: string;
  readonly round: number;
  readonly root: Id;
  readonly caller: Execution;
}

/** 写实例、存表单、记"实例发起"、从开始节点往前走；返回还没写回的推进（调用方 flush）。at 给出时用它作这一步的时刻 */
export async function launch(context: EngineContext, definition: LoadedDefinition, actor: Actor, launch: Launch, at?: string): Promise<Execution> {
  const model = definition.model;
  const now = at ?? instantOf(context.clock.now());
  const parent = launch.parent;
  const variables: Record<string, unknown> = {};
  for (const v of model.variables ?? []) if (v.default !== undefined) variables[v.name] = v.default;
  // 先写实例拿到数据库给的编号，后面的待办、表单的值、流转记录都要用它
  const instance = await context.stores.instances.insert({
    processKey: model.key,
    definitionId: definition.stored.id,
    version: definition.stored.version,
    businessKey: launch.businessKey,
    title: model.name,
    priority: launch.priority,
    summary: {},
    status: 'active',
    result: null,
    current: [],
    variables: { ...variables, ...launch.variables },
    state: RunState.INITIAL,
    startedBy: launch.starterId,
    startedByName: launch.starterName,
    startedAt: now,
    endedAt: null,
    parentInstanceId: parent?.instanceId ?? null,
    parentNode: parent?.node ?? null,
    parentRound: parent?.round ?? null,
    rootInstanceId: parent?.root ?? null,
    rev: 0,
  });
  const run = new Execution(context, actor, now, definition, instance, {}, instance.rev);
  if (parent) run.caller = parent.caller;
  run.picks = launch.picks;
  const start = definition.graph.start!;
  if (definition.manifest) {
    run.form = launch.fullForm ? { ...launch.form } : mergeForm(definition.manifest, start.form, true, {}, launch.form, run.read);
    await context.stores.formData.insert(instance.id, definition.form!.id, run.form);
  }
  run.addEvent(FLOW_EVENT.INSTANCE_STARTED, start, { data: parent ? { parentInstanceId: parent.instanceId, parentNode: parent.node } : {} });
  await run.leave(start.id, null);
  return run;
}

// ───────── 认领 ─────────

/** 认领，记一条"认领"的流转记录 */
export function claim(context: EngineContext, actor: Actor, command: ClaimCommand): Promise<CommandResult> {
  return inTransaction(context, async () => {
    const item = await context.stores.workItems.find(command.workItemId);
    if (!item) throw notFound('workItem', command.workItemId);
    const instance = await context.stores.instances.find(item.instanceId);
    if (!instance) throw notFound('instance', item.instanceId);
    requireActive(instance);
    await claimItem(context, actor, item);
    const definition = await context.definitions.ofInstance(instance);
    const run = new Execution(context, actor, instantOf(context.clock.now()), definition, instance, {}, instance.rev);
    run.addEvent(FLOW_EVENT.WORK_ITEM_CLAIMED, run.graph.node(item.node), { workItemId: item.id });
    await run.publish();
    return { instance: instanceView(instance), created: [], closed: [], events: run.events };
  });
}

/** 把待办认领给操作人：已经是他的就什么也不做；别人认领了报 already-claimed；不是候选人报 not-candidate */
export async function claimItem(context: EngineContext, actor: Actor, item: WorkItem): Promise<void> {
  if (item.state !== 'open') throw notOpen(item.id);
  if (item.assigneeId !== null) {
    if (item.assigneeId === actor.id) return;
    throw alreadyClaimed(item.id);
  }
  if (!isCandidate(actor, await context.stores.workItems.candidates(item.id))) throw notCandidate(item.id);
  // 两个人同时认领：带条件的更新只有一个改到
  const claimed = await context.stores.workItems.claim(item.id, {
    assigneeId: actor.id,
    assigneeName: actorName(actor),
    claimedAt: instantOf(context.clock.now()),
  });
  if (!claimed) throw alreadyClaimed(item.id);
}

// ───────── 处理 ─────────

/** 处理；出错时抛带业务错误码的错误，整个事务回滚 */
export function complete(context: EngineContext, actor: Actor, command: CompleteCommand): Promise<CommandResult> {
  return inTransaction(context, async () => {
    // 1. 锁实例
    const found = await context.stores.workItems.find(command.workItemId);
    if (!found) throw notFound('workItem', command.workItemId);
    const rev = await context.stores.instances.lock(found.instanceId);
    if (rev === null) throw notFound('instance', found.instanceId);
    const instance = (await context.stores.instances.find(found.instanceId))!;
    requireActive(instance);

    // 2. 锁住之后重读待办：并发的另一次处理可能刚刚改了它
    const item = (await context.stores.workItems.find(command.workItemId))!;
    if (item.state !== 'open') throw notOpen(item.id);
    if (item.assigneeId === null) await claimItem(context, actor, item);
    else if (item.assigneeId !== actor.id) throw notCandidate(item.id);

    // 3. 结果与意见
    const definition = await context.definitions.ofInstance(instance);
    const task = definition.graph.node(item.node) as TaskNode;
    const outcome = chosenOutcome(task, command.outcome ?? null);
    checkComment(outcome, command.comment ?? null);

    // 4. 表单
    const now = instantOf(context.clock.now());
    const stored = (await context.stores.formData.read(instance.id))?.values ?? {};
    const run = new Execution(context, actor, now, definition, instance, stored, rev);
    if (definition.manifest) {
      run.form = mergeForm(definition.manifest, task.form, false, stored, command.form ?? {}, run.read);
      await context.stores.formData.update(instance.id, run.form);
    }
    run.picks = picksOf(command.picks);
    if (outcome?.kind === 'return' && outcome.target === 'choose') {
      run.returnTarget = checkReturnTarget(definition, instance.state, item.node, command.returnTarget);
    }

    // 守卫：表单合并好之后、改成已处理之前问一遍，任一不通过整个处理不做
    await checkGuards(context, actor, run, item, outcome);

    // 5、6. 待办改成已处理，往下走
    await finishItem(context, run, task, item, outcome?.key ?? command.outcome ?? null, outcome, command.comment ?? null);

    // 记下处理之前的样子：下一步还没人动时处理人能撤回；结束了就不再能撤回
    if (run.instance.endedAt === null) {
      await context.stores.steps.save({ instanceId: instance.id, workItemId: item.id, rev, state: instance.state, variables: instance.variables, form: stored });
    } else {
      await context.stores.steps.remove(instance.id);
    }

    // 7. 写回
    await run.flush(null);
    return resultOf(run);
  });
}

/**
 * 待办按这个结果办完：改成已处理、记流转记录、往下走。处理命令与超时自动处理共用。
 *
 * @param outcome 选的结果；节点没写处理结果时为 null（按 outcomeKey 当作一个"同意"）
 */
export async function finishItem(
  context: EngineContext,
  run: Execution,
  task: TaskNode,
  item: WorkItem,
  outcomeKey: string | null,
  outcome: Outcome | null,
  comment: string | null,
): Promise<void> {
  const completion = { outcome: outcomeKey, comment, completedBy: actorIdOf(run.actor), completedByName: actorNameOf(run.actor), completedAt: run.now };
  if (!(await context.stores.workItems.complete(item.id, completion))) throw notOpen(item.id);
  run.closed.push(item);
  // 结果的名字存快照：流转记录上显示"通过"而不是 pass，以后改了定义也不变
  run.addEvent(FLOW_EVENT.WORK_ITEM_COMPLETED, task, {
    outcome: outcomeKey,
    workItemId: item.id,
    comment,
    data: outcome ? { [OUTCOME_LABEL]: outcome.label } : {},
  });
  const chosen = outcome ?? { key: outcomeKey!, label: outcomeKey! };
  if (item.parentItemId !== null) {
    // 委派、加签的人办完：回到原来的待办；后加签、并加的都办完了才按原来的人的结果往下
    await childDone(context, run, task, item, (parent, parentOutcome) => settle(context, run, task, parent, parentOutcome, comment));
  } else if (!(await holdsParent(context, run, task, item, chosen))) {
    await settle(context, run, task, item, chosen, comment);
  }
}

/** 一个待办（会签时是其中一份）办完了，节点怎样往下：不是会签时直接往下；会签时先看还要等别人、轮到下一个人、或按某个结果结束 */
async function settle(context: EngineContext, run: Execution, task: TaskNode, item: WorkItem, chosen: Outcome, comment: string | null) {
  if (!task.multi) return goOn(run, task, chosen, comment);
  // 这一轮的会签待办：不含转办取消掉的、委派加签挂在下面的
  const round = (await context.stores.workItems.ofInstance(item.instanceId)).filter(
    (i) => i.node === task.id && i.round === item.round && i.parentItemId === null && i.state !== 'canceled',
  );
  const decision = decide(task.multi, chosen, round, run.instance.state.queue(task.id).length);
  if (decision.kind === 'next') await run.nextSigner(task, round.length + 1);
  else if (decision.kind === 'finish') {
    await run.closeCountersign(task);
    await goOn(run, task, chosenOutcome(task, decision.outcome)!, comment);
  }
  // wait：还要等别人，这一步只记下这个人的处理
}

/** 节点按这个结果往下走：同意 → proceed；退回（选节点的退回到选的节点）、否决、提级按结果选线；终止直接结束实例 */
async function goOn(run: Execution, task: TaskNode, outcome: Outcome, comment: string | null) {
  switch (outcome.kind ?? 'approve') {
    case 'approve':
      return run.proceed(task.id, outcome.key);
    case 'return':
      return outcome.target === 'choose' ? run.returnTo(task.id, outcome.key) : run.leave(task.id, outcome.key);
    case 'reject':
    case 'escalate':
      return run.leave(task.id, outcome.key);
    case 'terminate':
      return run.finish('terminated', outcome.result ?? null, FLOW_EVENT.INSTANCE_TERMINATED, comment);
    default:
      throw new Error(`outcome kind '${outcome.kind}' is not supported`);
  }
}

/** 选的结果：节点写了结果时必须是其中一个；节点没写结果时为 null */
export function chosenOutcome(task: TaskNode, key: string | null): Outcome | null {
  if (task.outcomes.length === 0) return null;
  const outcome = task.outcomes.find((o) => o.key === key);
  if (!outcome) throw invalid('outcome', 'unknown-outcome');
  return outcome;
}

/** 结果上写的守卫都要通过；不通过的报 `flow.guard-rejected`，明细里是守卫的名字与原因 */
async function checkGuards(context: EngineContext, actor: Actor, run: Execution, item: WorkItem, outcome: Outcome | null) {
  if (!outcome?.guards?.length) return;
  const refused: ErrorDetail[] = [];
  const guardContext = { actor, instance: instanceView(run.instance), workItem: item, outcome: outcome.key, form: run.form };
  for (const name of outcome.guards) {
    const reason = await context.extensions.guard(name).check(guardContext);
    if (reason !== null) refused.push({ code: name, node: item.node, params: { reason } });
  }
  if (refused.length > 0) throw new FlowError('flow.guard-rejected', { message: `guards refused outcome ${outcome.key}`, errors: refused });
}

/** 结果要求写意见时不能空 */
function checkComment(outcome: Outcome | null, comment: string | null) {
  if (outcome?.comment === 'required' && !comment?.trim()) throw invalid('comment', 'required');
}

// ───────── 撤回 ─────────

/** 发起人撤回；不是发起人、规则不允许时报 `flow.forbidden` */
export function withdraw(context: EngineContext, actor: Actor, command: WithdrawCommand): Promise<CommandResult> {
  return inTransaction(context, async () => {
    const rev = await context.stores.instances.lock(command.instanceId);
    if (rev === null) throw notFound('instance', command.instanceId);
    const instance = (await context.stores.instances.find(command.instanceId))!;
    requireActive(instance);
    if (actor.id !== instance.startedBy) throw forbidden('instance', instance.id, 'not-the-initiator');
    const definition = await context.definitions.ofInstance(instance);
    if (!withdrawAllowed(definition, await context.stores.workItems.ofInstance(instance.id))) {
      throw forbidden('instance', instance.id, `withdraw-${definition.model.settings?.withdraw ?? 'beforeAction'}`);
    }
    const run = new Execution(context, actor, instantOf(context.clock.now()), definition, instance, {}, rev);
    await run.finish('withdrawn', 'withdrawn', FLOW_EVENT.INSTANCE_WITHDRAWN, command.reason ?? null);
    await run.flush(instance.title);
    return resultOf(run);
  });
}

/** 发起人现在还能不能撤回：beforeAction（默认）还没有人处理过；beforeClaim 还没有人认领、处理过；never 不能 */
export function withdrawAllowed(definition: LoadedDefinition, items: readonly WorkItem[]): boolean {
  const handled = items.some((w) => w.state === 'done');
  const claimed = items.some((w) => w.assignment === 'claimed');
  switch (definition.model.settings?.withdraw ?? 'beforeAction') {
    case 'never':
      return false;
    case 'beforeClaim':
      return !handled && !claimed;
    default:
      return !handled;
  }
}
