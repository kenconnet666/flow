/**
 * 调用流程（0005 的 4.2 call，同 Java 的 CallActivities）：父实例在调用节点上发起子实例，子实例结束时回到父实例。
 *   - 发起：forEach 时列表里每一项一个子实例（as 是那一项的名字），否则一个；input 的键是子实例那一侧（form. 开头写进子实例的表单，
 *     否则是流程变量），值是父实例这一侧的值或引用。子实例的发起人是父实例的发起人；
 *   - 同步（mode: "sync"）：父实例停在调用节点，等这一轮的子实例都结束；结果是子实例的结束结果（几个都一样才按它，否则走没写结果的出边），
 *     output 把子实例那一侧的值收进父实例的流程变量（forEach 时收成列表）；
 *   - 异步（默认）：发起就往下走，不等；
 *   - 父实例被终止、撤回时，没结束的子实例一起终止。
 * 子实例在同一个命令里马上就结束了（全是自动节点）时，直接回到这个命令里的父实例（caller），不从库里重读。
 */
import { FLOW_EVENT, isRef, type CallNode } from '@zerodep-dev/flow-model';
import type { InstanceRecord, JsonRecord } from '../store/types.js';
import { Execution } from './execution.js';
import { latestActive, launch } from './handlers.js';
import { readReference } from './values.js';

/** 到达调用节点（轮次已经加过）：发起子实例；同步的停下等，异步的接着走 */
export async function callProcess(run: Execution, call: CallNode): Promise<void> {
  const parent = run.instance;
  const round = parent.state.round(call.id);
  const items: unknown[] = call.forEach === undefined ? [null] : listOf(run.read(call.forEach.ref));
  const sync = call.mode === 'sync';
  if (sync && items.length > 0) run.waitForChildren(call.id, items.length);
  const child = await latestActive(run.context, call.process);
  const root = parent.rootInstanceId ?? parent.id;
  for (const item of items) {
    const read = (name: string) => scoped(name, call.as, item, run.read);
    const form: Record<string, unknown> = {};
    const variables: Record<string, unknown> = {};
    for (const [target, value] of Object.entries(call.input ?? {})) {
      const resolved = isRef(value) ? read(value.ref) : value;
      if (target.startsWith('form.')) form[target.slice('form.'.length)] = resolved;
      else variables[target] = resolved;
    }
    const childRun = await launch(
      run.context,
      child,
      run.actor,
      {
        starterId: parent.startedBy,
        starterName: parent.startedByName,
        businessKey: parent.businessKey,
        priority: parent.priority,
        variables,
        form,
        picks: new Map(),
        fullForm: true,
        parent: { instanceId: parent.id, node: call.id, round, root, caller: run },
      },
      run.now,
    );
    await childRun.flush(null);
    run.created.push(...childRun.created);
  }
  if (!sync || items.length === 0) await run.leave(call.id, null);
}

/**
 * 子实例结束了：父实例还停在调用它的节点的这一轮上（同步）时少等一个；都结束了收 output、按结果往下走。
 * 父实例是这个命令里调用它的那个时直接接着推进；否则锁住父实例重读，推进完写回。
 */
export async function childEnded(child: Execution): Promise<void> {
  const ended = child.instance;
  let parentRun = child.caller;
  const loaded = parentRun === null;
  if (parentRun === null) {
    parentRun = await load(child, ended.parentInstanceId!);
    if (parentRun === null) return;
  }
  const state = parentRun.instance.state;
  const node = ended.parentNode!;
  if (!(node in state.waits) || state.round(node) !== ended.parentRound) return; // 异步调用的，或父实例已经不等它了
  if (parentRun.childArrived(node)) {
    const call = parentRun.graph.node(node) as CallNode;
    const children = (await child.context.stores.instances.children(ended.parentInstanceId!, node, ended.parentRound!)).map((c) => (c.id === ended.id ? ended : c));
    await parentRun.callReturned(call, outcomeOf(children), await outputOf(call, children, child));
  }
  if (loaded) {
    await parentRun.flush(null);
    child.created.push(...parentRun.created);
    child.closed.push(...parentRun.closed);
  }
}

/** 父实例终止、撤回：没结束的子实例一起终止（子实例的子实例也一样），不再回到父实例 */
export async function terminateChildren(parentRun: Execution, reason: string | null): Promise<void> {
  const context = parentRun.context;
  for (const found of await context.stores.instances.activeChildren(parentRun.instance.id)) {
    const rev = (await context.stores.instances.lock(found.id))!;
    const instance = (await context.stores.instances.find(found.id))!;
    if (instance.endedAt !== null) continue;
    const childRun = new Execution(context, parentRun.actor, parentRun.now, await context.definitions.ofInstance(instance), instance, {}, rev);
    childRun.quiet = true;
    await childRun.finish('terminated', 'terminated', FLOW_EVENT.INSTANCE_TERMINATED, reason);
    await childRun.flush(null);
    parentRun.closed.push(...childRun.closed);
  }
}

/** 锁住父实例重读；结束了、暂停了的不推进（返回 null） */
async function load(child: Execution, parentId: bigint): Promise<Execution | null> {
  const context = child.context;
  const rev = await context.stores.instances.lock(parentId);
  if (rev === null) return null;
  const parent = (await context.stores.instances.find(parentId))!;
  if (parent.status !== 'active') return null;
  const form = (await context.stores.formData.read(parentId))?.values ?? {};
  return new Execution(context, child.actor, child.now, await context.definitions.ofInstance(parent), parent, form, rev);
}

/** 几个子实例都是同一个结束结果才按它，否则为 null（走没写结果的出边） */
function outcomeOf(children: readonly InstanceRecord[]): string | null {
  const first = children[0]?.result ?? null;
  return children.every((c) => c.result === first) ? first : null;
}

/** output：父实例的流程变量 ← 子实例那一侧的值；forEach 时收成列表（按发起的先后） */
async function outputOf(call: CallNode, children: readonly InstanceRecord[], child: Execution): Promise<JsonRecord> {
  const out: Record<string, unknown> = {};
  const output = Object.entries(call.output ?? {});
  if (output.length === 0) return out;
  const forms: JsonRecord[] = [];
  for (const c of children) forms.push(c.id === child.instance.id ? child.form : ((await child.context.stores.formData.read(c.id))?.values ?? {}));
  for (const [target, name] of output) {
    const values = children.map((c, i) => readReference(name, c, forms[i]!));
    out[target] = call.forEach === undefined ? (values[0] ?? null) : values;
  }
  return out;
}

/** 引用里 as 那一项（及它的属性 as.字段）取循环里的值，其余照常 */
function scoped(name: string, as: string | undefined, item: unknown, read: (name: string) => unknown): unknown {
  if (as === undefined) return read(name);
  if (name === as) return item;
  if (name.startsWith(`${as}.`) && typeof item === 'object' && item !== null) return (item as JsonRecord)[name.slice(as.length + 1)] ?? null;
  return read(name);
}

const listOf = (value: unknown): unknown[] => (Array.isArray(value) ? [...value] : value == null ? [] : [value]);
