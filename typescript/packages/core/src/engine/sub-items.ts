/**
 * 委派、加签生成的待办（挂在原来的待办下面）怎样回到原来的待办（同 Java 的 SubItems）：
 * <pre>
 *   addMode    原来的待办在等什么                         子待办都办完了
 *   delegate   等着（waiting），被委派的人先办            原来的待办打开，回到原来的处理人手里
 *   before     等着（waiting），加的人先办                同上
 *   parallel   照常打开，一起办                           原来的人也办完了，节点按原来的人的结果往下走
 *   after      照常打开；加的人的待办先等着               原来的人办完时打开它们；都办完了按原来的人的结果往下走
 * </pre>
 * 加的人、被委派的人办的结果只是意见（记在流转记录里），节点按原来的人的结果往下走。
 */
import { FLOW_EVENT, type Outcome, type TaskNode, type WorkItem } from '@zerodep-dev/flow-model';
import type { EngineContext } from './context.js';
import type { Execution } from './execution.js';

/** 一个子待办办完了；settle 是节点按原来的待办的结果往下走 */
export async function childDone(
  context: EngineContext,
  run: Execution,
  task: TaskNode,
  child: WorkItem,
  settle: (parent: WorkItem, outcome: Outcome) => Promise<void>,
): Promise<void> {
  const parent = (await context.stores.workItems.find(child.parentItemId!))!;
  switch (child.addMode) {
    case 'delegate':
    case 'before':
      return resumeIfSettled(context, run, parent);
    case 'after':
    case 'parallel':
      if (parent.state === 'done' && !(await hasPending(context, parent))) await settle(parent, outcomeOf(task, parent));
      return;
    default:
      throw new Error(`unknown add mode ${child.addMode}`);
  }
}

/**
 * 原来的待办办完了：有还在办的并加的人、或等着的后加签的人时，节点先不往下走（返回 true）；后加签的人的待办这时打开。
 * 原来的人选了非同意的结果时不等加签的人，把它们取消、照常往下走。
 */
export async function holdsParent(context: EngineContext, run: Execution, task: TaskNode, parent: WorkItem, chosen: Outcome): Promise<boolean> {
  const pending = (await context.stores.workItems.children(parent.id)).filter((c) => c.state === 'open' || c.state === 'waiting');
  if (pending.length === 0) return false;
  if ((chosen.kind ?? 'approve') !== 'approve') {
    for (const child of pending) {
      await context.stores.workItems.cancel(child.id, run.now);
      run.closed.push(child);
      run.addEvent(FLOW_EVENT.WORK_ITEM_CANCELED, task, { workItemId: child.id });
    }
    return false;
  }
  for (const child of pending) {
    if (child.addMode === 'after') await context.stores.workItems.changeState(child.id, 'waiting', 'open');
  }
  return true;
}

/** 委派、前加签的人都办完了（或都减掉了）：原来的待办打开，回到原来的处理人手里 */
export async function resumeIfSettled(context: EngineContext, run: Execution, parent: WorkItem): Promise<void> {
  if (parent.state !== 'waiting' || (await hasPending(context, parent))) return;
  await context.stores.workItems.changeState(parent.id, 'waiting', 'open');
  run.addEvent(FLOW_EVENT.WORK_ITEM_RESUMED, run.graph.node(parent.node), { workItemId: parent.id });
}

/** 还有没办完的子待办（打开着的，或后加签还在等的） */
async function hasPending(context: EngineContext, parent: WorkItem): Promise<boolean> {
  return (await context.stores.workItems.children(parent.id)).some((c) => c.state === 'open' || c.state === 'waiting');
}

/** 原来的待办选的结果 */
function outcomeOf(task: TaskNode, parent: WorkItem): Outcome {
  return task.outcomes.find((o) => o.key === parent.outcome) ?? { key: parent.outcome!, label: parent.outcome! };
}
