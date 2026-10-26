/**
 * 流程详情（发起人看进度、从我已办里打开）：实例的概要、当前在谁手里、只读的表单、流转记录，
 * 以及撤回、催办（引擎说我能做时）、抄送（给了挑人的数据来源时）与留言；管理端打开时（admin）多一块管理员操作。
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
  Dialog,
  Field,
  Flex,
  PeoplePicker,
  Popconfirm,
  Skeleton,
  Textarea,
  useErrorMessage,
  useFormat,
  useRequest,
  type PeopleEntry,
  type PeopleSource,
} from '@zerodep-dev/vue-tsx-pro-ui';
import { useFlowClient } from '../client/flow-client';
import type { CommandResult } from '../client/types';
import { FlowForm } from '../form/FlowForm';
import { useFlowMessages } from '../lang/messages';
import { FlowTimeline } from './FlowTimeline';
import { InstanceAdminActions } from './InstanceAdminActions';
import { statusTone } from './status';

/** FlowInstanceDetail 的属性 */
export interface FlowInstanceDetailProps {
  /** 实例的编号 */
  id: Id;
  /** 人员字段、抄送挑人的数据来源；不给时没有"抄送" */
  people?: PeopleSource;
  /** 表单代码还能导入的模块（见 FlowForm） */
  modules?: Readonly<Record<string, unknown>>;
  /** 撤回、催办、抄送、留言、管理员操作之后 */
  onChange?: (result: CommandResult) => void;
  /** 显示管理员操作（暂停、恢复、终止、跳转、改派）：管理端打开时给；能不能做仍由后端的 PermissionPolicy 定 */
  admin?: boolean;
}

/** 打开着的待办的处理人：落到人的写名字，在收件箱里的写"待认领" */
const holder = (item: WorkItem, pool: string) => item.assigneeName ?? pool;

/** 流程详情 */
export const FlowInstanceDetail = _component(({ id, people, modules, onChange, admin = false }: FlowInstanceDetailProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const errorText = useErrorMessage();
  const format = useFormat();
  const detail = useRequest((instanceId: Id, signal) => flow.instance(instanceId, signal), { params: () => id });

  let busy = _state<string | null>(null);
  let failure = _state<string | null>(null);
  let notice = _state<string | null>(null);
  let comment = _state('');
  let ccOpen = _state(false);
  let ccPicked = _state<readonly PeopleEntry[]>([]);
  let ccMessage = _state('');

  /** 发一个命令：成功时刷新并告诉父级 */
  /** 发一个命令：成功时刷新并告诉父级，返回 true */
  async function run(key: string, action: () => Promise<CommandResult>, done?: (result: CommandResult) => void): Promise<boolean> {
    busy = key;
    failure = null;
    notice = null;
    try {
      const result = await action();
      done?.(result);
      await detail.refresh();
      onChange?.(result);
      return true;
    } catch (error) {
      failure = errorText(error);
      return false;
    } finally {
      busy = null;
    }
  }

  const withdraw = () => run('withdraw', () => flow.withdraw({ instanceId: id }));
  // 一小时内催过的引擎不再记：按这次记下的催办条数提示
  const remind = () => run('remind', () => flow.remind({ instanceId: id }), (result) => (notice = messages.reminded(result.events.length)));
  function openCc() {
    ccPicked = [];
    ccMessage = '';
    ccOpen = true;
  }

  /** 抄送对话框的确认：返回 false 时对话框不关 */
  async function sendCc(): Promise<boolean> {
    if (ccPicked.length === 0) return false;
    const users = ccPicked.map((entry) => BigInt(entry.id));
    const message = ccMessage.trim();
    return run('cc', () => flow.cc({ instanceId: id, users, ...(message ? { message } : {}) }), () => (notice = messages.ccDone(users.length)));
  }

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
            {(actions.withdraw || actions.remind || people) && (
              <Flex gap="sm" wrap>
                {people && (
                  <Button variant="outline" disabled={busy != null} onClick={openCc}>
                    {messages.cc}
                  </Button>
                )}
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
            {admin && <InstanceAdminActions detail={data} {...(people ? { people } : {})} busy={busy} onRun={(key, action) => run(key, action)} />}
          </Flex>
        </Card>
        {people && (
          <Dialog
            _open={ccOpen}
            title={messages.cc}
            confirmText={messages.confirm}
            cancelText={messages.cancel}
            onConfirm={sendCc}
            propsConfirm={{ disabled: ccPicked.length === 0 }}
          >
            <Flex direction="column" gap="md">
              <Field label={messages.pickCcUsers} required>
                <PeoplePicker source={people} _value={ccPicked} recentKey="flow-cc" />
              </Field>
              <Field label={messages.ccMessage}>
                <Textarea _value={ccMessage} rows={2} />
              </Field>
            </Flex>
          </Dialog>
        )}
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
