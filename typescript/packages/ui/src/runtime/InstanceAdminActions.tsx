/**
 * 流程详情上的管理员操作（0006 的 3.4）：暂停或恢复、终止、跳转到某个节点、改数据、迁到流程的另一个版本、把打开着的待办改派给别人。
 * 谁能做由后端的 PermissionPolicy 定；这里只管按实例的状态显示哪几个。命令由父级发（同 CollaborationActions）。
 *
 *   <InstanceAdminActions detail={data} people={peopleSource} busy={busy} onRun={(key, action) => run(key, action)} />
 */
import type { Id, WorkItem } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import {
  Button,
  Dialog,
  Field,
  Flex,
  PeoplePicker,
  Popconfirm,
  Select,
  Text,
  Textarea,
  type PeopleEntry,
  type PeopleSource,
} from '@zerodep-dev/vue-tsx-pro-ui';
import { useFlowClient } from '../client/flow-client';
import type { CommandResult, InstanceDetail } from '../client/types';
import { useFlowMessages } from '../lang/messages';
import { holderName } from './parts/holder';
import { MigrateDialog } from './MigrateDialog';

/** InstanceAdminActions 的属性 */
export interface InstanceAdminActionsProps {
  /** 实例详情 */
  detail: InstanceDetail;
  /** 改派挑人的数据来源；不给时没有"改派" */
  people?: PeopleSource;
  /** 正在提交的命令：按钮都不可用 */
  busy?: string | null;
  /** 发命令；成功时返回 true（对话框才关） */
  onRun: (key: string, action: () => Promise<CommandResult>) => Promise<boolean>;
}

/** 流程详情上的管理员操作 */
export const InstanceAdminActions = _component(({ detail, people, busy = null, onRun }: InstanceAdminActionsProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  let jumpOpen = _state(false);
  let jumpNode = _state<string | null>(null);
  let reason = _state('');
  let reassigning = _state<WorkItem | null>(null);
  let reassignOpen = _state(false);
  let reassignTo = _state<readonly PeopleEntry[]>([]);
  // 改数据：变量、表单字段各一段 JSON
  let changeOpen = _state(false);
  let changeVariables = _state('');
  let changeForm = _state('');
  let changeError = _state<string | null>(null);
  let migrateOpen = _state(false);

  const instanceId = (): Id => detail.instance.id;
  const note = () => (reason.trim() ? { reason: reason.trim() } : {});

  function openJump() {
    jumpNode = null;
    reason = '';
    jumpOpen = true;
  }

  function openReassign(item: WorkItem) {
    reassigning = item;
    reassignTo = [];
    reassignOpen = true;
  }

  /** 一段 JSON 对象；空的是不改 */
  const parseObject = (text: string): Record<string, unknown> | undefined => {
    if (!text.trim()) return undefined;
    const value: unknown = JSON.parse(text);
    if (value === null || typeof value !== 'object' || Array.isArray(value)) throw new Error('{ … }');
    return value as Record<string, unknown>;
  };

  async function changeData() {
    changeError = null;
    let variables: Record<string, unknown> | undefined;
    let form: Record<string, unknown> | undefined;
    try {
      variables = parseObject(changeVariables);
      form = parseObject(changeForm);
    } catch (error) {
      changeError = messages.jsonInvalid((error as Error).message);
      return false;
    }
    return onRun('changeData', () =>
      flow.changeData({ instanceId: instanceId(), ...(variables ? { variables } : {}), ...(form ? { form } : {}), reason: reason.trim() }),
    );
  }

  const jump = () => (jumpNode ? onRun('jump', () => flow.jump({ instanceId: instanceId(), node: jumpNode!, ...note() })) : false);
  const reassign = () =>
    reassigning && reassignTo[0]
      ? onRun('reassign', () => flow.reassign({ workItemId: reassigning!.id, userId: BigInt(reassignTo[0]!.id) }))
      : false;

  return () => {
    const { instance } = detail;
    const ended = instance.endedAt != null;
    const open = detail.workItems.filter((item) => item.state === 'open');
    if (ended) return null;
    return (
      <Flex direction="column" gap="sm">
        <Text weight="medium">{messages.adminActions}</Text>
        <Flex gap="sm" wrap>
          {instance.status === 'suspended' ? (
            <Button variant="outline" loading={busy === 'resume'} disabled={busy != null} onClick={() => onRun('resume', () => flow.resume(instanceId()))}>
              {messages.resume}
            </Button>
          ) : (
            <Button variant="outline" loading={busy === 'suspend'} disabled={busy != null} onClick={() => onRun('suspend', () => flow.suspend({ instanceId: instanceId() }))}>
              {messages.suspend}
            </Button>
          )}
          {instance.status === 'active' && (
            <Button variant="outline" disabled={busy != null} onClick={openJump}>
              {messages.jump}
            </Button>
          )}
          <Button
            variant="outline"
            disabled={busy != null}
            onClick={() => {
              changeVariables = '';
              changeForm = '';
              reason = '';
              changeError = null;
              changeOpen = true;
            }}
          >
            {messages.changeData}
          </Button>
          <Button variant="outline" disabled={busy != null} onClick={() => (migrateOpen = true)}>
            {messages.migrate}
          </Button>
          <Popconfirm
            title={messages.terminateConfirm}
            tone="danger"
            confirmText={messages.confirm}
            cancelText={messages.cancel}
            onConfirm={() => onRun('terminate', () => flow.terminate({ instanceId: instanceId() }))}
            trigger={(props) => (
              <Button {...props} variant="outline" tone="danger" loading={busy === 'terminate'} disabled={busy != null && busy !== 'terminate'}>
                {messages.terminate}
              </Button>
            )}
          />
        </Flex>
        {people && instance.status === 'active' && open.length > 0 && (
          <Flex direction="column" gap="xs">
            {open.map((item) => (
              <Flex key={String(item.id)} gap="sm" align="center">
                <Text>
                  {item.nodeName ?? item.node}：{holderName(item, messages)}
                </Text>
                <Button size="sm" variant="ghost" disabled={busy != null} onClick={() => openReassign(item)}>
                  {messages.reassign}
                </Button>
              </Flex>
            ))}
          </Flex>
        )}
        <Dialog
          _open={jumpOpen}
          title={messages.jump}
          confirmText={messages.confirm}
          cancelText={messages.cancel}
          onConfirm={jump}
          propsConfirm={{ disabled: !jumpNode }}
        >
          <Flex direction="column" gap="md">
            <Field label={messages.jumpTo} required>
              <Select items={detail.nodes.map((n) => ({ value: n.node, label: n.name ?? n.node }))} _value={jumpNode} />
            </Field>
            <Field label={messages.reason}>
              <Textarea _value={reason} rows={2} />
            </Field>
          </Flex>
        </Dialog>
        <Dialog
          _open={changeOpen}
          title={messages.changeData}
          confirmText={messages.confirm}
          cancelText={messages.cancel}
          onConfirm={changeData}
          propsConfirm={{ disabled: !reason.trim() || (!changeVariables.trim() && !changeForm.trim()) }}
        >
          <Flex direction="column" gap="md">
            <Text tone="muted">{messages.changeDataHint}</Text>
            <Field label={messages.changeVariables}>
              <Textarea _value={changeVariables} rows={3} placeholder='{ "round": 2 }' />
            </Field>
            <Field label={messages.changeForm}>
              <Textarea _value={changeForm} rows={3} placeholder='{ "amount": "1200.00" }' />
            </Field>
            <Field label={messages.reason} required {...(changeError ? { error: changeError } : {})}>
              <Textarea _value={reason} rows={2} />
            </Field>
          </Flex>
        </Dialog>
        <MigrateDialog _open={migrateOpen} detail={detail} onConfirm={(command) => onRun('migrate', () => flow.migrate(command))} />
        {people && (
          <Dialog
            _open={reassignOpen}
            title={messages.reassign}
            confirmText={messages.confirm}
            cancelText={messages.cancel}
            onConfirm={reassign}
            propsConfirm={{ disabled: reassignTo.length === 0 }}
          >
            <Field label={messages.reassignTo} required>
              <PeoplePicker source={people} _value={reassignTo} max={1} recentKey="flow-collaboration" />
            </Field>
          </Dialog>
        )}
      </Flex>
    );
  };
});
