/**
 * 作业（同 Java 的 JobHandler、JobRunner、BuiltinJobs）：推进时排下的事（Execution），到点由 {@link JobRunner} 做。
 *   - JobRunner.runDue：读出一批到期的作业，逐个抢（带条件的更新），抢到的切到它的租户、在一个事务里交给它那一种的处理；
 *     失败的事务回滚、过一会儿重试（默认 1、4、9……分钟），重试用完标成失败（管理端的"异常"），再交给 gaveUp 善后；
 *   - 内置的：timer 定时节点到点、overdue 待办超时（按节点的 overdue 处理）、remind 提醒、unclaimed 没人认领、
 *     action 异步的自动节点做动作（onFailure: error 时重试用完整个实例暂停）、receiveTimeout 等待节点等超时。
 * 都先锁住实例再做，操作人是 SYSTEM_ACTOR。待办已经办完、实例已经结束的作业直接跳过；实例暂停时往后推一小时。
 */
import { hostname } from 'node:os';
import { FLOW_EVENT, type Outcome, type ReceiveNode, type ServiceNode, type TaskNode, type WorkItem } from '@zerodep-dev/flow-model';
import { SYSTEM_ACTOR } from '../identity.js';
import type { RunState } from '../run-state.js';
import { tenantScope } from '../store/query/database.js';
import type { Job, JsonRecord } from '../store/types.js';
import { instantOf, plusSeconds, type Instant } from '../time.js';
import { inTransaction, type EngineContext } from './context.js';
import { Execution } from './execution.js';
import { chosenOutcome, finishItem } from './handlers.js';

/** 内置作业的种类 */
export const JOB = {
  TIMER: 'timer',
  OVERDUE: 'overdue',
  REMIND: 'remind',
  UNCLAIMED: 'unclaimed',
  ACTION: 'action',
  RECEIVE_TIMEOUT: 'receiveTimeout',
} as const;

/** 内置作业最多试几次 */
export const MAX_ATTEMPTS = 3;

/** 实例暂停时往后推多久再看（秒） */
const POSTPONE = 3600;

/** 一次最多做多少个 */
const BATCH = 50;

/** 领到的作业多久没做完算这个进程挂了，别的进程可以再领（秒） */
const LEASE = 300;

/** 下次再做：每隔一段时间的提醒、实例暂停时往后推 */
export interface Next {
  readonly at: Instant;
  readonly payload: JsonRecord;
}

/** 一种作业怎么做；接入方注册新的种类，同名的换掉内置的 */
export interface JobHandler {
  readonly kind: string;
  /** 做一个作业；返回下次再做的时刻与数据，做完了返回 null（作业删掉） */
  run(job: Job, now: Instant): Promise<Next | null>;
  /** 第 attempts 次失败后隔多少秒重试；默认 1、4、9……分钟 */
  retryDelay?(job: Job, attempts: number): number;
  /** 重试用完了（作业已经标成失败）：在一个新的事务里调，记一笔 */
  gaveUp?(job: Job, error: string, now: Instant): Promise<void>;
}

/** 作业对应的节点（定时、自动节点、等待超时的作业）；待办上的作业为 null */
export const nodeOf = (job: Job): string | null => (job.payload['node'] as string | undefined) ?? null;

/** 实例还停在作业的那个节点的那一轮上（跳转、退回跳出区域后就对不上了，作业跳过） */
export function stillWaiting(job: Job, state: RunState): boolean {
  const node = nodeOf(job);
  const round = job.payload['round'];
  return node !== null && typeof round === 'number' && node in state.waits && state.round(node) === round;
}

/** 给管理员看的失败原因：最里层错误的话（动作抛的错常被包成 flow.extension-failed，外层的话没有用） */
export function describe(error: unknown): string {
  let root = error;
  while (root instanceof Error && root.cause !== undefined && root.cause !== root) root = root.cause;
  return root instanceof Error ? root.message || root.name : String(root);
}

/** 做到期的作业：几个进程同时跑也不会重复做 */
export class JobRunner {
  readonly #handlers = new Map<string, JobHandler>();
  /** 领取人记成"进程号@主机名"：排查卡住的作业时知道是哪个进程领的 */
  readonly #owner = `${process.pid}@${hostname()}`;

  /** @param handlers 各种作业的处理；同一种出现两次时后面的换掉前面的（接入方注册的放在内置的后面） */
  constructor(
    private readonly context: EngineContext,
    handlers: readonly JobHandler[],
  ) {
    for (const handler of handlers) this.#handlers.set(handler.kind, handler);
  }

  /** 做一批到期的作业，返回做了几个（含失败的）；一批做满时调用方可以马上再调 */
  async runDue(): Promise<number> {
    const now = instantOf(this.context.clock.now());
    const jobs = this.context.stores.jobs;
    let ran = 0;
    for (const job of await jobs.due(now, BATCH)) {
      if (!(await jobs.lock(job.id, this.#owner, now, plusSeconds(now, LEASE)))) continue; // 别的进程先领了
      await tenantScope.run(job.tenantId ?? 0n, () => this.#runOne(job, now));
      ran++;
    }
    return ran;
  }

  async #runOne(job: Job, now: Instant) {
    const jobs = this.context.stores.jobs;
    const handler = this.#handlers.get(job.kind);
    try {
      if (!handler) throw new Error(`no JobHandler is registered for job kind '${job.kind}'`);
      await inTransaction(this.context, async () => {
        const next = await handler.run(job, now);
        if (next === null) await jobs.done(job.id);
        else await jobs.reschedule(job.id, next.at, next.payload);
      });
    } catch (error) {
      const attempts = job.attempts + 1;
      const reason = describe(error);
      console.warn(`flow job ${job.id} (${job.kind}) failed, attempt ${attempts}: ${reason}`);
      if (attempts < job.maxAttempts) {
        const delay = handler?.retryDelay?.(job, attempts) ?? attempts * attempts * 60;
        await jobs.retry(job.id, plusSeconds(now, delay), reason);
        return;
      }
      await jobs.fail(job.id, reason);
      // 善后再失败只记日志，作业已经是失败的了
      if (handler?.gaveUp) await inTransaction(this.context, () => handler.gaveUp!(job, reason, now)).catch((e) => console.error(`flow job ${job.id} gave up and its handler failed to record it`, e));
    }
  }
}

/** 对实例做一件事 */
type InstanceAction = (run: Execution) => Promise<Next | null>;

/** 内置作业的处理 */
export function builtinJobs(context: EngineContext): JobHandler[] {
  /** 锁住实例再做：实例没了、结束了跳过；暂停时往后推（数据不变）。做完写回 */
  const onInstance = async (job: Job, now: Instant, action: InstanceAction): Promise<Next | null> => {
    const rev = await context.stores.instances.lock(job.instanceId);
    if (rev === null) return null;
    const instance = (await context.stores.instances.find(job.instanceId))!;
    if (instance.endedAt !== null) return null;
    if (instance.status === 'suspended') return { at: plusSeconds(now, POSTPONE), payload: job.payload };
    const form = (await context.stores.formData.read(instance.id))?.values ?? {};
    const run = new Execution(context, SYSTEM_ACTOR, now, await context.definitions.ofInstance(instance), instance, form, rev);
    const next = await action(run);
    await run.flush(null);
    return next;
  };

  /** 待办还打开着才做：先不加锁看一眼（办完了的直接跳过），锁住实例后再重读一次 */
  const onOpenItem = async (job: Job, now: Instant, action: (run: Execution, item: WorkItem, task: TaskNode) => Promise<Next | null>) => {
    const first = job.workItemId === null ? null : await context.stores.workItems.find(job.workItemId);
    if (first?.state !== 'open') return null;
    return onInstance(job, now, async (run) => {
      const item = (await context.stores.workItems.find(job.workItemId!))!;
      if (item.state !== 'open') return null;
      return action(run, item, run.graph.node(item.node) as TaskNode);
    });
  };

  const remindEvent = (run: Execution, task: TaskNode, item: WorkItem, reason: string) =>
    run.addEvent(FLOW_EVENT.WORK_ITEM_REMINDED, task, { workItemId: item.id, data: { auto: true, reason } });

  const timer: JobHandler = {
    kind: JOB.TIMER,
    run: (job, now) =>
      onInstance(job, now, async (run) => {
        if (stillWaiting(job, run.instance.state)) await run.timerFired(nodeOf(job)!);
        return null;
      }),
  };

  /** 待办超时：记一条"超时"，再按节点的 overdue 处理（mark 只记、complete 按指定的结果自动处理、escalate 按提级的结果走） */
  const overdue: JobHandler = {
    kind: JOB.OVERDUE,
    run: (job, now) =>
      onOpenItem(job, now, async (run, item, task) => {
        run.addEvent(FLOW_EVENT.WORK_ITEM_OVERDUE, task, { workItemId: item.id, data: { dueAt: item.dueAt } });
        const outcome = overdueOutcome(task);
        if (outcome) await finishItem(context, run, task, item, outcome.key, outcome, null);
        return null;
      }),
  };

  /** 提醒：记一条催办；超时后每隔一段时间的，次数没用完时排下一次 */
  const remind: JobHandler = {
    kind: JOB.REMIND,
    run: (job, now) =>
      onOpenItem(job, now, async (run, item, task) => {
        const index = job.payload['reminder'] as number;
        const sent = job.payload['sent'] as number;
        const reminder = task.reminders?.[index];
        if (!reminder) return null; // 定义改了：按实例的版本不会走到这里，留个保险
        await context.stores.workItems.remind(item.id, now, now);
        remindEvent(run, task, item, 'before' in reminder ? 'dueSoon' : 'overdue');
        const more = 'every' in reminder && (reminder.times === undefined || sent + 1 < reminder.times);
        return more ? { at: run.deadlines.plus(reminder.every, now), payload: { reminder: index, sent: sent + 1 } } : null;
      }),
  };

  /** 没人认领：还在收件箱里时提醒，或标成等收件箱的负责人指定 */
  const unclaimed: JobHandler = {
    kind: JOB.UNCLAIMED,
    run: (job, now) =>
      onOpenItem(job, now, async (run, item, task) => {
        if (item.assigneeId !== null || !task.unclaimed) return null; // 已经有人认领了
        if (task.unclaimed.action === 'assignByManager') {
          const manager = item.inboxKind === null ? null : (context.org.inboxManager?.(item.inboxKind, item.inboxId!) ?? null);
          await context.stores.workItems.needDesignation(item.id, manager);
          run.addEvent(FLOW_EVENT.WORK_ITEM_UNCLAIMED, task, {
            workItemId: item.id,
            data: manager === null ? {} : { designatorId: manager, designatorName: context.org.userName(manager) },
          });
        } else {
          remindEvent(run, task, item, 'unclaimed');
        }
        return null;
      }),
  };

  /** 异步的自动节点：实例还停在这个节点的这一轮上时做动作、往下走；按节点的 retry.backoff 重试，重试用完记一笔 */
  const action: JobHandler = {
    kind: JOB.ACTION,
    run: (job, now) =>
      onInstance(job, now, async (run) => {
        if (stillWaiting(job, run.instance.state)) await run.performAction(run.graph.node(nodeOf(job)!) as ServiceNode);
        return null;
      }),
    retryDelay: (job, attempts) => (typeof job.payload['backoffSeconds'] === 'number' ? job.payload['backoffSeconds'] : attempts * attempts * 60),
    gaveUp: async (job, error, now) => {
      await onInstance(job, now, async (run) => {
        const node = run.graph.node(nodeOf(job)!);
        run.addEvent(FLOW_EVENT.JOB_FAILED, node, { data: { jobId: job.id, kind: job.kind, error } });
        // onFailure: error——整个实例停下，管理员恢复后重试或跳过；默认 manual 只停在这个节点
        if (node?.type === 'service' && node.onFailure === 'error') run.changeStatus('suspended', FLOW_EVENT.INSTANCE_SUSPENDED, null);
        return null;
      });
    },
  };

  /** 等待节点等超时：还在等这一轮时记一条，overdue 写了 complete 就当作事件按那个结果到了，否则接着等 */
  const receiveTimeout: JobHandler = {
    kind: JOB.RECEIVE_TIMEOUT,
    run: (job, now) =>
      onInstance(job, now, async (run) => {
        if (!stillWaiting(job, run.instance.state)) return null;
        const receive = run.graph.node(nodeOf(job)!) as ReceiveNode;
        run.addEvent(FLOW_EVENT.NODE_OVERDUE, receive, { data: { dueAt: job.dueAt } });
        if (receive.overdue?.action === 'complete') {
          for (const wait of await context.stores.waits.ofInstance(job.instanceId, receive.event)) {
            if (wait.node === receive.id) await context.stores.waits.remove(wait.id);
          }
          await run.received(receive, receive.overdue.outcome, {});
        }
        return null;
      }),
  };

  return [timer, overdue, remind, unclaimed, action, receiveTimeout];
}

/** 超时自动处理按哪个结果：complete 是写的那个，escalate 是提级的那个；mark（默认）不处理 */
function overdueOutcome(task: TaskNode): Outcome | null {
  const rule = task.overdue;
  if (!rule) return null;
  if (rule.action === 'complete') return chosenOutcome(task, rule.outcome);
  if (rule.action === 'escalate') return task.outcomes.find((o) => o.kind === 'escalate') ?? null;
  return null;
}
