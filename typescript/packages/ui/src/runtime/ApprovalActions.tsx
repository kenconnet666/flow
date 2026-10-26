/**
 * 审批面板的按钮区：意见框加一排处理结果（0005 人工节点的 outcomes）。否决、终止是危险色；要确认的结果先弹确认。
 * 意见框只在有结果收意见时出现；选了要求意见的结果而意见是空的，标在意见框上，不发请求。
 * 选了的结果后面有要选处理人的下一步（OutcomeView.picks）时，先弹 PickDialog 选人，再交给 onChoose；
 * 退回时要选退回到哪一步的（OutcomeView.targets 不空），先弹框选节点。
 *
 *   <ApprovalActions outcomes={view.outcomes} _comment={comment} busy={busy} onChoose={(outcome) => complete(outcome)} />
 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import {
  Button,
  Dialog,
  Field,
  Flex,
  Popconfirm,
  Radio,
  RadioGroup,
  Textarea,
  type PeopleSource,
  type PopoverTriggerProps,
  type StatusTone,
} from '@zerodep-dev/vue-tsx-pro-ui';
import type { Id } from '@zerodep-dev/flow-model';
import type { OutcomeView } from '../client/types';
import { useFlowMessages } from '../lang/messages';
import { PickDialog } from './PickDialog';

/** ApprovalActions 的属性 */
export interface ApprovalActionsProps {
  /** 能选的处理结果 */
  outcomes: readonly OutcomeView[];
  /** 意见：父级写 _comment={x} 双向绑定 */
  _comment?: string;
  /** 意见框上的错误（引擎退回的） */
  commentError?: string;
  /** 正在提交：按钮都不可用，选中的那个转圈 */
  busy?: string | null;
  /** 选下一步处理人时的人员数据来源（引擎说"谁都可以选"时用） */
  people?: PeopleSource;
  /** 选了一个结果（意见的必填已经查过，要选的处理人、退回的节点已经选了）；返回 false（或 resolve 成 false）时弹框不关 */
  onChoose: (outcome: OutcomeView, picks: Record<string, Id[]>, target?: string) => unknown;
}

/** 处理结果的色调：否决、终止是危险色，同意类是强调色，其余中性 */
const toneOf = (outcome: OutcomeView): StatusTone =>
  outcome.kind === 'reject' || outcome.kind === 'terminate' ? 'danger' : outcome.kind === 'approve' ? 'accent' : 'neutral';

/** 审批面板的按钮区 */
export const ApprovalActions = _component(
  ({ outcomes, _comment: comment = _bindable(''), commentError, busy = null, people, onChoose }: ApprovalActionsProps) => {
    const messages = useFlowMessages();
    let missing = _state(false);
    let picking = _state<OutcomeView | null>(null);
    let pickOpen = _state(false);
    let returning = _state<OutcomeView | null>(null);
    let returnOpen = _state(false);
    let target = _state<string | null>(null);
    const takesComment = () => outcomes.some((o) => o.comment !== 'none');

    function choose(outcome: OutcomeView) {
      missing = outcome.comment === 'required' && !comment.trim();
      if (missing) return;
      if (outcome.targets.length > 0) {
        returning = outcome;
        // 默认选最近的一步（定义里排在最后的）
        target = outcome.targets[outcome.targets.length - 1]!.node;
        returnOpen = true;
        return;
      }
      if (outcome.picks.length === 0) {
        onChoose(outcome, {});
        return;
      }
      picking = outcome;
      pickOpen = true;
    }

    // 要确认的结果由 Popconfirm 的 trigger 渲染按钮，triggerProps 是它给的打开弹层的属性
    const button = (outcome: OutcomeView, triggerProps?: PopoverTriggerProps) => (
      <Button
        {...triggerProps}
        key={outcome.key}
        variant={outcome.kind === 'approve' ? 'solid' : 'outline'}
        tone={toneOf(outcome)}
        loading={busy === outcome.key}
        disabled={busy != null && busy !== outcome.key}
        {...(outcome.confirm ? {} : { onClick: () => choose(outcome) })}
      >
        {outcome.label}
      </Button>
    );

    return () => (
      <Flex direction="column" gap="md">
        {takesComment() && (
          <Field
            label={messages.comment}
            // 只有每个结果都要意见时才标必填；只有否决要时，选了否决再提示
            required={outcomes.every((o) => o.comment === 'required')}
            error={missing ? messages.commentRequired : commentError}
          >
            <Textarea _value={comment} onInput={() => (missing = false)} rows={3} />
          </Field>
        )}
        <Flex gap="sm" wrap>
          {outcomes.map((outcome) =>
            outcome.confirm ? (
              <Popconfirm
                key={outcome.key}
                title={messages.confirmTitle(outcome.label)}
                tone={toneOf(outcome)}
                confirmText={messages.confirm}
                cancelText={messages.cancel}
                onConfirm={() => choose(outcome)}
                trigger={(props) => button(outcome, props)}
              />
            ) : (
              button(outcome)
            ),
          )}
        </Flex>
        {returning && (
          <Dialog
            _open={returnOpen}
            title={messages.returnTargetTitle}
            confirmText={messages.confirm}
            cancelText={messages.cancel}
            onConfirm={() => (target ? onChoose(returning!, {}, target) : false)}
            propsConfirm={{ disabled: !target, loading: busy === returning.key }}
          >
            <RadioGroup _value={target} aria-label={messages.returnTargetTitle}>
              {returning.targets.map((t) => (
                <Radio key={t.node} value={t.node} {...(t.handlerName ? { description: messages.returnTargetTo(t.handlerName) } : {})}>
                  {t.nodeName ?? t.node}
                </Radio>
              ))}
            </RadioGroup>
          </Dialog>
        )}
        {picking && (
          <PickDialog
            _open={pickOpen}
            picks={picking.picks}
            {...(people ? { people } : {})}
            onConfirm={(picks) => onChoose(picking!, picks)}
          />
        )}
      </Flex>
    );
  },
);
