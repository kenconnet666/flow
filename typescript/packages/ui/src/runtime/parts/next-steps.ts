/**
 * 预测的下一步写成一句给人看的话：审批面板上每个结果后面、发起页的提交按钮上面用。
 *
 *   describeNext(outcome.next, messages)   // → "总经理审批（总经理）"、"财务复核（进收件箱）"、"结束：否决"
 */
import type { NextStep } from '../client/types';
import type { FlowMessages } from '../lang/messages';

/** 一步：人工节点带上交给谁，结束节点写成"结束：结果名" */
function describeStep(step: NextStep, messages: FlowMessages): string {
  const name = step.nodeName ?? step.node;
  if (step.type === 'end') return messages.nextEnd(name);
  if (step.type !== 'task') return name;
  const who = step.handlers.length ? step.handlers.join('、') : step.pick ? messages.nextPick : step.inbox ? messages.nextInbox : null;
  return who ? `${name}（${who}）` : name;
}

/** 几步并列（并行分支）时用顿号连起来；空的时为 null */
export function describeNext(steps: readonly NextStep[], messages: FlowMessages): string | null {
  return steps.length ? steps.map((step) => describeStep(step, messages)).join('、') : null;
}
