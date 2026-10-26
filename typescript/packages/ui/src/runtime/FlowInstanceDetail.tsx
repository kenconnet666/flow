/**
 * 流程详情（发起人看进度、从我已办里打开）：实例的概要、当前在谁手里、只读的表单、流转记录，
 * 以及撤回、催办（引擎说我能做时）与留言。
 *
 *   <FlowInstanceDetail id={instanceId} people={peopleSource} onChange={(result) => …} />
 *
 * 表单按流程级的 formView 去掉了隐藏的字段（引擎做）；在待办里办理走 WorkItemDetail。
 */
import type { Id, WorkItem } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import {
  Alert,
  Badge,
  Button,
  Card,
  Descriptions,
  Flex,
  Popconfirm,
  Skeleton,
  Textarea,
  useErrorMessage,
  useFormat,
  useRequest,
  type PeopleSource,
} from '@zerodep-dev/vue-tsx-pro-ui';
import { useFlowClient } from '../client/flow-client';
import type { CommandResult } from '../client/types';
import { FlowForm } from '../form/FlowForm';
import { useFlowMessages } from '../lang/messages';
import { FlowTimeline } from './FlowTimeline';
import { statusTone } from './status';

/** FlowInstanceDetail 的属性 */
export interface FlowInstanceDetailProps {
  /** 实例的编号 */
  id: Id;
  /** 人员字段的数据来源 */
  people?: PeopleSource;
  /** 表单代码还能导入的模块（见 FlowForm） */
  modules?: Readonly<Record<string, unknown>>;
  /** 撤回、催办、留言之后 */
  onChange?: (result: CommandResult) => void;
}

/** 打开着的待办的处理人：落到人的写名字，在收件箱里的写"待认领" */
const holder = (item: WorkItem, pool: string) => item.assigneeName ?? pool;

/** 流程详情 */
export const FlowInstanceDetail = _component(({ id, people, modules, onChange }: FlowInstanceDetailProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const errorText = useErrorMessage();
  const format = useFormat();
  const detail = useRequest((instanceId: Id, signal) => flow.instance(instanceId, signal), { params: () => id });

  let busy = _state<string | null>(null);
  let failure = _state<string | null>(null);
  let notice = _state<string | null>(null);
  let comment = _state('');

  /** 发一个命令：成功时刷新并告诉父级 */
  async function run(key: string, action: () => Promise<CommandResult>, done?: (result: CommandResult) => void) {
    busy = key;
    failure = null;
    notice = null;
    try {
      const result = await action();
      done?.(result);
      await detail.refresh();
      onChange?.(result);
    } catch (error) {
      failure = errorText(error);
    } finally {
      busy = null;
    }
  }

  const withdraw = () => run('withdraw', () => flow.withdraw({ instanceId: id }));
  // 一小时内催过的引擎不再记：按这次记下的催办条数提示
  const remind = () => run('remind', () => flow.remind({ instanceId: id }), (result) => (notice = messages.reminded(result.events.length)));
  const leaveComment = () =>
    run('comment', () => flow.comment({ instanceId: id, text: comment.trim() }), () => (comment = ''));

  return () => {
    const data = detail.data;
    if (detail.error && !data) return <Alert tone="danger">{errorText(detail.error)}</Alert>;
    if (!data) return <Skeleton />;
    const { instance, actions } = data;
    const open = data.workItems.filter((item) => item.state === 'open');
    return (
      <Flex direction="column" gap="lg">
        {failure && <Alert tone="danger" onClose={() => (failure = null)}>{failure}</Alert>}
        {notice && <Alert tone="success" onClose={() => (notice = null)}>{notice}</Alert>}
        <Card
          header={instance.title ?? instance.processKey}
          extra={<Badge tone={statusTone(instance.status)}>{messages.status[instance.status] ?? instance.status}</Badge>}
        >
          <Flex direction="column" gap="md">
            <Descriptions
              columns="auto"
              items={[
                { key: 'startedBy', label: messages.startedBy, value: instance.startedByName ?? '' },
                { key: 'startedAt', label: messages.startedAt, value: format.date(instance.startedAt, { dateStyle: 'medium', timeStyle: 'short' }) },
                ...(instance.current.length
                  ? [
                      { key: 'current', label: messages.columnCurrent, value: instance.current.map((c) => c.name ?? c.node).join('、') },
                      { key: 'handlers', label: messages.handlers, value: [...new Set(open.map((item) => holder(item, messages.pool)))].join('、') },
                    ]
                  : []),
              ]}
            />
            {(actions.withdraw || actions.remind) && (
              <Flex gap="sm" wrap>
                {actions.remind && (
                  <Button variant="outline" loading={busy === 'remind'} disabled={busy != null && busy !== 'remind'} onClick={remind}>
                    {messages.remind}
                  </Button>
                )}
                {actions.withdraw && (
                  <Popconfirm
                    title={messages.withdrawConfirm}
                    tone="danger"
                    confirmText={messages.confirm}
                    cancelText={messages.cancel}
                    onConfirm={withdraw}
                    trigger={(props) => (
                      <Button {...props} variant="outline" tone="danger" loading={busy === 'withdraw'} disabled={busy != null && busy !== 'withdraw'}>
                        {messages.withdraw}
                      </Button>
                    )}
                  />
                )}
              </Flex>
            )}
          </Flex>
        </Card>
        {data.form && (
          <Card>
            <FlowForm view={data.form} mode="view" {...(people ? { people } : {})} {...(modules ? { modules } : {})} />
          </Card>
        )}
        <Card header={messages.timeline}>
          <Flex direction="column" gap="lg">
            <Flex direction="column" gap="sm">
              <Textarea _value={comment} rows={2} placeholder={messages.commentPlaceholder} aria-label={messages.leaveComment} />
              <Flex justify="end">
                <Button variant="outline" loading={busy === 'comment'} disabled={!comment.trim() || (busy != null && busy !== 'comment')} onClick={leaveComment}>
                  {messages.leaveComment}
                </Button>
              </Flex>
            </Flex>
            <FlowTimeline events={data.timeline} />
          </Flex>
        </Card>
      </Flex>
    );
  };
});
