/**
 * 外部事件（0005 的 4.3，同 Java 的 SignalHandler）：按实例编号或业务单据编号找到在等这个事件的等待（flow_wait），逐个带条件地删掉、往下走
 * （删到 0 行就是并发送来的同一个事件已经处理过了）。都在一个事务里。
 *   - 实例在等：记一条 signal.received，output 写进表单或流程变量，按结果往下走；
 *   - 按实例编号找、实例不在等：记一条 signal.ignored，不报错；按单号找没有在等的：什么也不做；
 *   - 实例暂停了：报 flow.instance-not-active，业务系统过后重发。
 */
import { FLOW_EVENT, type Id, type ReceiveNode } from '@zerodep-dev/flow-model';
import type { CommandResult } from '../command/types.js';
import type { Actor } from '../identity.js';
import type { JsonRecord, Wait } from '../store/types.js';
import { instantOf } from '../time.js';
import { inTransaction, type EngineContext } from './context.js';
import { invalid, notFound } from './errors.js';
import { Execution } from './execution.js';
import { requireActive, resultOf } from './handlers.js';

/** 送来一个外部事件：按实例编号或业务单据编号找在等它的实例 */
export interface SignalCommand {
  readonly instanceId?: Id | null;
  readonly businessKey?: string | null;
  readonly event: string;
  /** 事件带回的结果（等待节点的 outcomes 之一） */
  readonly outcome?: string | null;
  readonly data?: JsonRecord;
}

/** 送来一个事件，返回每个往下走了（或记了一笔）的实例这一步的结果 */
export function signal(context: EngineContext, actor: Actor, command: SignalCommand): Promise<CommandResult[]> {
  if (!command.event?.trim()) throw invalid('event', 'required');
  if (command.instanceId == null && command.businessKey == null) throw invalid('instanceId', 'required');
  return inTransaction(context, async () => {
    const waits =
      command.instanceId != null
        ? await context.stores.waits.ofInstance(command.instanceId, command.event)
        : await context.stores.waits.ofBusinessKey(command.businessKey!, command.event);
    const results: CommandResult[] = [];
    if (waits.length === 0) {
      if (command.instanceId != null) results.push(await ignored(context, actor, command.instanceId, command));
    } else {
      for (const wait of waits) {
        const result = await deliver(context, actor, wait, command);
        if (result) results.push(result);
      }
    }
    return results;
  });
}

/** 送到一个等待：锁住实例、删掉这一行；实例还停在那个节点上时往下走，否则（跳转走了）记一笔 */
async function deliver(context: EngineContext, actor: Actor, wait: Wait, command: SignalCommand): Promise<CommandResult | null> {
  const rev = await context.stores.instances.lock(wait.instanceId);
  if (rev === null) throw notFound('instance', wait.instanceId);
  const instance = (await context.stores.instances.find(wait.instanceId))!;
  requireActive(instance);
  if (!(await context.stores.waits.remove(wait.id))) return null; // 并发的同一个事件先到了
  const form = (await context.stores.formData.read(instance.id))?.values ?? {};
  const run = new Execution(context, actor, instantOf(context.clock.now()), await context.definitions.ofInstance(instance), instance, form, rev);
  const receive = run.graph.node(wait.node) as ReceiveNode;
  if (!(wait.node in instance.state.waits)) {
    run.addEvent(FLOW_EVENT.SIGNAL_IGNORED, receive, { outcome: command.outcome ?? null, data: eventData(command) });
  } else {
    if ((receive.outcomes ?? []).length > 0 && !receive.outcomes!.includes(command.outcome ?? '')) throw invalid('outcome', 'unknown-outcome');
    run.addEvent(FLOW_EVENT.SIGNAL_RECEIVED, receive, { outcome: command.outcome ?? null, data: eventData(command) });
    await run.received(receive, command.outcome ?? null, command.data ?? {});
  }
  await run.flush(null);
  return resultOf(run);
}

/** 实例不在等这个事件：只记一笔（不锁实例，同留言） */
async function ignored(context: EngineContext, actor: Actor, instanceId: Id, command: SignalCommand): Promise<CommandResult> {
  const instance = await context.stores.instances.find(instanceId);
  if (!instance) throw notFound('instance', instanceId);
  const run = new Execution(context, actor, instantOf(context.clock.now()), await context.definitions.ofInstance(instance), instance, {}, instance.rev);
  run.addEvent(FLOW_EVENT.SIGNAL_IGNORED, null, { outcome: command.outcome ?? null, data: eventData(command) });
  await run.publish();
  return resultOf(run);
}

const eventData = (command: SignalCommand): JsonRecord => ({
  event: command.event,
  ...(command.data && Object.keys(command.data).length > 0 ? { data: command.data } : {}),
});
