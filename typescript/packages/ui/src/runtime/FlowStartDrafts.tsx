/**
 * 我发起前存的草稿：每份一行（流程、最后存的时间），"接着填"回到发起页，"放弃"删掉。没有草稿时什么都不显示。
 *
 *   <FlowStartDrafts onOpen={(draft) => router.push(`/start/${draft.processKey}?draft=${draft.id}`)} />
 */
import type { ProcessInstance } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Button, Card, Flex, Popconfirm, Text, useErrorMessage, useFormat, useRequest } from '@zerodep-dev/vue-tsx-pro-ui';
import { useFlowClient } from '../client/flow-client';
import { useFlowMessages } from '../lang/messages';

/** FlowStartDrafts 的属性 */
export interface FlowStartDraftsProps {
  /** 接着填一份草稿 */
  onOpen: (draft: ProcessInstance) => void;
}

/** 我的草稿 */
export const FlowStartDrafts = _component(({ onOpen }: FlowStartDraftsProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const format = useFormat();
  const errorText = useErrorMessage();
  const drafts = useRequest((_: null, signal) => flow.startDrafts(signal), { params: () => null });
  let failure = _state<string | null>(null);

  async function discard(draft: ProcessInstance) {
    failure = null;
    try {
      await flow.discardStartDraft(draft.id);
      await drafts.refresh();
    } catch (error) {
      failure = errorText(error);
    }
  }

  return () => {
    if (!drafts.data?.length) return null;
    return (
      <Card header={messages.startDrafts}>
        <Flex direction="column" gap="sm">
          {failure && <Alert tone="danger" onClose={() => (failure = null)}>{failure}</Alert>}
          {drafts.data.map((draft) => (
            <Flex key={String(draft.id)} gap="sm" align="center" justify="between" wrap>
              <Flex direction="column">
                <Text weight="medium">{draft.title ?? draft.processKey}</Text>
                <Text tone="muted" size="sm">
                  {messages.draftSavedAt(format.date(draft.startedAt, { dateStyle: 'medium', timeStyle: 'short' }))}
                </Text>
              </Flex>
              <Flex gap="xs">
                <Button size="sm" onClick={() => onOpen(draft)}>
                  {messages.continueDraft}
                </Button>
                <Popconfirm
                  title={messages.discardDraft}
                  tone="danger"
                  confirmText={messages.confirm}
                  cancelText={messages.cancel}
                  onConfirm={() => discard(draft)}
                  trigger={(props) => (
                    <Button {...props} size="sm" variant="ghost" tone="danger">
                      {messages.discardDraft}
                    </Button>
                  )}
                />
              </Flex>
            </Flex>
          ))}
        </Flex>
      </Card>
    );
  };
});
