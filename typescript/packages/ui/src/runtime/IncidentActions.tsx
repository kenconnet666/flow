/**
 * 一个异常（重试用完的作业）上的管理员操作：重试（修好外部系统后让它再做一次），或跳过、按一个结果往下走（只有自动节点的动作能跳过）。
 * 异常列表 FlowIncidentList 的每一行、管理端流程详情的"异常"一块共用。终止实例在流程详情的管理员操作里。
 *
 *   <IncidentActions incident={row} onDone={(kind) => refresh()} onError={(message) => (failure = message)} />
 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Button, Dialog, Field, Flex, Select, Textarea, useErrorMessage } from '@zerodep-dev/vue-tsx-pro-ui';
import { useFlowClient } from '../client/flow-client';
import type { Incident } from '../client/types';
import { useFlowMessages, type FlowMessages } from '../lang/messages';

/** IncidentActions 的属性 */
export interface IncidentActionsProps {
  /** 异常 */
  incident: Incident;
  /** 做完了：retry 重新排队了（稍后自动执行），skip 已按结果往下走 */
  onDone: (kind: IncidentAction) => void;
  /** 出错了（文案已按错误码翻好） */
  onError: (message: string) => void;
}

/** 异常上的管理员操作 */
/** 对一个异常做了什么：重试、跳过（自动节点按结果往下走）、放弃投递（发件箱的投递） */
export type IncidentAction = 'retry' | 'skip' | 'discard';

/** 发件箱的投递失败（后端的 DeliveryJob）：没有节点，"跳过"就是放弃投递 */
export const isDelivery = (incident: Incident) => incident.kind === 'deliver';

/** 做完之后的提示 */
export function incidentDoneText(kind: IncidentAction, messages: FlowMessages): string {
  return kind === 'retry' ? messages.incidentRetried : kind === 'discard' ? messages.incidentDiscarded : messages.incidentSkipped;
}

export const IncidentActions = _component(({ incident, onDone, onError }: IncidentActionsProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const errorText = useErrorMessage();
  let busy = _state<IncidentAction | null>(null);
  let skipOpen = _state(false);
  let outcome = _state<string | null>(null);
  let reason = _state('');

  async function run(kind: IncidentAction, action: () => Promise<unknown>): Promise<boolean> {
    busy = kind;
    try {
      await action();
      onDone(kind);
      return true;
    } catch (error) {
      onError(errorText(error));
      return false;
    } finally {
      busy = null;
    }
  }

  function openSkip() {
    outcome = null;
    reason = '';
    skipOpen = true;
  }

  // 出边上写了结果时要选一个；只有一条没写结果的出边时不用选
  const needsOutcome = () => incident.outcomes.length > 0;
  const skip = () =>
    run(isDelivery(incident) ? 'discard' : 'skip', () =>
      flow.skipJob({
        jobId: incident.jobId,
        ...(outcome ? { outcome } : {}),
        ...(reason.trim() ? { reason: reason.trim() } : {}),
      }),
    );

  return () => (
    <Flex gap="xs">
      <Button size="sm" variant="ghost" loading={busy === 'retry'} disabled={busy != null} onClick={() => run('retry', () => flow.retryJob(incident.jobId))}>
        {messages.incidentRetry}
      </Button>
      {incident.skippable && (
        <Button size="sm" variant="ghost" disabled={busy != null} onClick={openSkip}>
          {isDelivery(incident) ? messages.incidentDiscard : messages.incidentSkip}
        </Button>
      )}
      <Dialog
        _open={skipOpen}
        title={isDelivery(incident) ? messages.incidentDiscardTitle : messages.incidentSkipTitle(incident.nodeName ?? incident.node ?? '')}
        confirmText={messages.confirm}
        cancelText={messages.cancel}
        onConfirm={skip}
        propsConfirm={{ disabled: needsOutcome() && !outcome, loading: busy === 'skip' || busy === 'discard' }}
      >
        <Flex direction="column" gap="md">
          {needsOutcome() && (
            <Field label={messages.incidentOutcome} required>
              <Select items={incident.outcomes.map((value) => ({ value, label: value }))} _value={outcome} />
            </Field>
          )}
          <Field label={messages.reason}>
            <Textarea _value={reason} rows={2} />
          </Field>
        </Flex>
      </Dialog>
    </Flex>
  );
});
