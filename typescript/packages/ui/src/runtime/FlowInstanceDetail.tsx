/**
 * 流程详情（发起人看进度、从我已办里打开）：实例的概要、当前在谁手里、只读的表单、流转记录，
 * 以及撤回、催办（引擎说我能做时）、抄送（给了挑人的数据来源时）与留言；管理端打开时（admin）多一块管理员操作、这个实例的异常，
 * 以及流程变量（引擎只给流程管理员）。
 *
 *   <FlowInstanceDetail id={instanceId} people={peopleSource} onChange={(result) => …} />
 *
 * 表单按流程级的 formView 去掉了隐藏的字段（引擎做）；在待办里办理走 WorkItemDetail。
 */
import type { Id } from '@zerodep-dev/flow-model';
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
  Text,
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
import { FlowDiagram } from './FlowDiagram';
import { FlowTimeline } from './FlowTimeline';
import { holderName } from './parts/holder';
import { IncidentActions, incidentDoneText, isDelivery, type IncidentAction } from './IncidentActions';
import { InstanceAdminActions } from './InstanceAdminActions';
import { statusTone } from './parts/status';

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
  /** 显示管理员操作（暂停、恢复、终止、跳转、改派）与异常（重试、跳过）：管理端打开时给；能不能做仍由后端的 PermissionPolicy 定 */
  admin?: boolean;
}

/** 流程详情 */
export const FlowInstanceDetail = _component(({ id, people, modules, onChange, admin = false }: FlowInstanceDetailProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const errorText = useErrorMessage();
  const format = useFormat();
  const detail = useRequest((instanceId: Id, signal) => flow.instance(instanceId, signal), { params: () => id });
  // 异常只在管理端看：一个实例同时卡住的地方不多，取第一页就够
  const incidents = useRequest(
    (instanceId: Id, signal) => (admin ? flow.incidents({ instanceId, page: 1, pageSize: 20 }, signal) : Promise.resolve(null)),
    { params: () => id },
  );

  let busy = _state<string | null>(null);
  let failure = _state<string | null>(null);
  let notice = _state<string | null>(null);
  let comment = _state('');
  let ccOpen = _state(false);
  let ccPicked = _state<readonly PeopleEntry[]>([]);
  let mentioned = _state<readonly PeopleEntry[]>([]);
  let ccMessage = _state('');

  /** 发一个命令：成功时刷新并告诉父级，返回 true */
  async function run(key: string, action: () => Promise<CommandResult>, done?: (result: CommandResult) => void): Promise<boolean> {
    busy = key;
    failure = null;
    notice = null;
    try {
      const result = await action();
      done?.(result);
      await Promise.all([detail.refresh(), admin ? incidents.refresh() : null]);
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

  /** 异常重试、跳过之后：提示，刷新详情与异常 */
  async function incidentDone(kind: IncidentAction) {
    failure = null;
    notice = incidentDoneText(kind, messages);
    await Promise.all([detail.refresh(), incidents.refresh()]);
  }

  const leaveComment = () =>
    run(
      'comment',
      () => flow.comment({ instanceId: id, text: comment.trim(), mentions: mentioned.map((entry) => BigInt(entry.id)) }),
      () => ((comment = ''), (mentioned = [])),
    );

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
                  ? [{ key: 'current', label: messages.columnCurrent, value: instance.current.map((c) => c.name ?? c.node).join('、') }]
                  : []),
                // 停在自动节点、定时、等事件上时没有人在办
                ...(open.length
                  ? [{ key: 'handlers', label: messages.handlers, value: [...new Set(open.map((item) => holderName(item, messages)))].join('、') }]
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
            {admin && (incidents.data?.rows.length ?? 0) > 0 && (
              <Alert tone="danger" title={messages.incidents} description={instance.status === 'suspended' ? messages.incidentResumeFirst : undefined}>
                <Flex direction="column" gap="xs">
                  {incidents.data!.rows.map((row) => (
                    <Flex key={String(row.jobId)} gap="sm" align="center" wrap>
                      <Text weight="medium">{row.nodeName ?? row.node ?? (isDelivery(row) ? messages.incidentDelivery : row.kind)}</Text>
                      <Text tone="muted">{row.error ?? ''}</Text>
                      {/* 暂停时自动节点的重试、跳过都做不成：先恢复；发件箱的投递与实例的状态无关（结束了也要投） */}
                      {(instance.status === 'active' || isDelivery(row)) && (
                        <IncidentActions incident={row} onDone={incidentDone} onError={(message) => ((failure = message), (notice = null))} />
                      )}
                    </Flex>
                  ))}
                </Flex>
              </Alert>
            )}
            {admin && (data.variables?.length ?? 0) > 0 && (
              <Flex direction="column" gap="xs">
                <Text weight="medium">{messages.variables}</Text>
                <Descriptions
                  columns="auto"
                  items={data.variables!.map((v) => ({ key: v.name, label: v.label ?? v.name, value: displayValue(v.value) }))}
                />
              </Flex>
            )}
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
            <FlowForm view={data.form} instanceId={data.instance.id} mode="view" {...(people ? { people } : {})} {...(modules ? { modules } : {})} />
          </Card>
        )}
        <Card header={messages.diagram}>
          <FlowDiagram diagram={data.diagram} />
        </Card>
        <Card header={messages.timeline}>
          <Flex direction="column" gap="lg">
            <Flex direction="column" gap="sm">
              <Textarea _value={comment} rows={2} placeholder={messages.commentPlaceholder} aria-label={messages.leaveComment} />
              <Flex justify="end" gap="sm" align="center" wrap>
                {people && (
                  <PeoplePicker
                    source={people}
                    _value={mentioned}
                    types={['user']}
                    recentKey="flow-mention"
                    placeholder={messages.mentionPlaceholder}
                    aria-label={messages.mentionPlaceholder}
                    style={{ minWidth: '14rem' }}
                  />
                )}
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

/** 流程变量的值给人看：强类型的值（Decimal、日期）有自己的 toString，列表与对象写成 JSON */
function displayValue(value: unknown): string {
  if (value == null) return '—';
  if (typeof value !== 'object') return String(value);
  if (Array.isArray(value) || Object.getPrototypeOf(value) === Object.prototype) {
    return JSON.stringify(value, (_key, inner: unknown) => (typeof inner === 'bigint' ? inner.toString() : inner));
  }
  return String(value);
}
