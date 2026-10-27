/**
 * 外部参与方看的进度（0002 §9）：现在在哪个环节、谁在处理、预计什么时候答复。不含表单与流转记录，给供应方这类外部账号看。
 *
 *   <FlowProgress instanceId={id} />
 */
import type { Id } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Badge, Card, Descriptions, Flex, Skeleton, Text, useErrorMessage, useFormat, useRequest } from '@zerodep-dev/vue-tsx-pro-ui';
import { useFlowClient } from '../client/flow-client';
import { useFlowMessages } from '../lang/messages';
import { statusTone } from './parts/status';

/** FlowProgress 的属性 */
export interface FlowProgressProps {
  instanceId: Id;
}

/** 进度 */
export const FlowProgress = _component(({ instanceId }: FlowProgressProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const format = useFormat();
  const errorText = useErrorMessage();
  const progress = useRequest((id: Id, signal) => flow.progress(id, signal), { params: () => instanceId });
  const time = (at: { toDate(): Date }) => format.date(at.toDate(), { dateStyle: 'medium', timeStyle: 'short' });

  return () => {
    const data = progress.data;
    if (progress.error && !data) return <Alert tone="danger">{errorText(progress.error)}</Alert>;
    if (!data) return <Skeleton />;
    return (
      <Card header={data.title ?? data.processKey} extra={<Badge tone={statusTone(data.status)}>{messages.status[data.status] ?? data.status}</Badge>}>
        <Flex direction="column" gap="md">
          <Descriptions
            columns="auto"
            items={[
              { key: 'startedAt', label: messages.startedAt, value: time(data.startedAt) },
              ...(data.endedAt ? [{ key: 'endedAt', label: messages.endedAt, value: time(data.endedAt) }] : []),
            ]}
          />
          {data.steps.length === 0 && !data.endedAt && <Text tone="muted">{messages.progressWaiting}</Text>}
          {data.steps.map((step, i) => (
            <Descriptions
              key={i}
              columns="auto"
              items={[
                { key: 'node', label: messages.columnNode, value: step.nodeName ?? '' },
                { key: 'handler', label: messages.columnAssignee, value: step.handlerName ?? messages.pool },
                { key: 'arrived', label: messages.columnArrivedAt, value: time(step.arrivedAt) },
                { key: 'due', label: messages.progressDue, value: step.dueAt ? time(step.dueAt) : messages.progressNoDue },
              ]}
            />
          ))}
        </Flex>
      </Card>
    );
  };
});
