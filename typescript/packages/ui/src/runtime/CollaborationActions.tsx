/**
 * 待办上处理之外的协作：转办、委派、加签、退回收件箱（0006 的 3.1–3.2）。显示哪几个由引擎说（待办详情的 actions）；
 * 转办、委派、加签在对话框里挑人（组件库的 PeoplePicker），退回收件箱先确认。
 *
 *   <CollaborationActions actions={view.actions} people={peopleSource} busy={busy} onRun={(c) => run(c)} />
 *
 * 命令由父级发（同处理一样管出错）：onRun 返回 true 表示成功，对话框才关上；提交中对话框的确认按钮转圈。
 */
import type { Id } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import {
  Button,
  Dialog,
  Field,
  Flex,
  PeoplePicker,
  Popconfirm,
  Radio,
  RadioGroup,
  Textarea,
  type PeopleEntry,
  type PeopleSource,
} from '@zerodep-dev/vue-tsx-pro-ui';
import type { AddSignMode, WorkItemActions } from '../client/types';
import { useFlowMessages } from '../lang/messages';

/** 一次协作：交给谁、加哪些人、怎么加、说明 */
export type Collaboration =
  | { readonly kind: 'transfer' | 'delegate'; readonly userId: Id; readonly reason?: string }
  | { readonly kind: 'addSigners'; readonly users: readonly Id[]; readonly mode: AddSignMode; readonly reason?: string }
  | { readonly kind: 'release'; readonly reason?: string };

/** CollaborationActions 的属性 */
export interface CollaborationActionsProps {
  /** 现在能做哪几个（待办详情的 actions） */
  actions: WorkItemActions;
  /** 挑人的数据来源；不给时转办、委派、加签都不显示 */
  people?: PeopleSource;
  /** 正在提交的命令（同 ApprovalActions 的 busy）：按钮都不可用 */
  busy?: string | null;
  /** 发命令；成功时返回 true */
  onRun: (collaboration: Collaboration) => Promise<boolean>;
}

/** 要挑人的几种 */
type Picking = 'transfer' | 'delegate' | 'addSigners';

/** 选择器里的人 → 引擎的编号（组织表的自增主键） */
const idOf = (entry: PeopleEntry): Id => BigInt(entry.id);

/** 待办上的协作 */
export const CollaborationActions = _component(({ actions, people, busy = null, onRun }: CollaborationActionsProps) => {
  const messages = useFlowMessages();
  let picking = _state<Picking>('transfer');
  let opened = _state(false);
  let picked = _state<readonly PeopleEntry[]>([]);
  let mode = _state<AddSignMode>('before');
  let reason = _state('');

  function open(kind: Picking) {
    picked = [];
    mode = 'before';
    reason = '';
    picking = kind;
    opened = true;
  }

  /** 对话框的确认：返回 false 时对话框不关 */
  function submit(): Promise<boolean> | false {
    const kind = picking;
    if (picked.length === 0) return false;
    const note = reason.trim() ? { reason: reason.trim() } : {};
    return onRun(
      kind === 'addSigners' ? { kind, users: picked.map(idOf), mode, ...note } : { kind, userId: idOf(picked[0]!), ...note },
    );
  }

  const titles = (): Record<Picking, string> => ({
    transfer: messages.transfer,
    delegate: messages.delegate,
    addSigners: messages.addSigners,
  });

  const button = (kind: Picking) => (
    <Button key={kind} variant="ghost" disabled={busy != null} onClick={() => open(kind)}>
      {titles()[kind]}
    </Button>
  );

  return () => {
    const shown = (['transfer', 'delegate', 'addSigners'] as const).filter((kind) => people && actions[kind]);
    if (shown.length === 0 && !actions.release) return null;
    return (
      <Flex gap="sm" wrap>
        {shown.map(button)}
        {actions.release && (
          <Popconfirm
            title={messages.releaseConfirm}
            confirmText={messages.confirm}
            cancelText={messages.cancel}
            onConfirm={() => void onRun({ kind: 'release' })}
            trigger={(props) => (
              <Button {...props} variant="ghost" loading={busy === 'release'} disabled={busy != null && busy !== 'release'}>
                {messages.release}
              </Button>
            )}
          />
        )}
        {people && (
          <Dialog
            _open={opened}
            title={titles()[picking]}
            confirmText={messages.confirm}
            cancelText={messages.cancel}
            onConfirm={submit}
            propsConfirm={{ disabled: picked.length === 0 }}
          >
            <Flex direction="column" gap="md">
              <Field label={picking === 'addSigners' ? messages.pickUsers : messages.pickUser} required>
                <PeoplePicker source={people} _value={picked} {...(picking === 'addSigners' ? {} : { max: 1 })} recentKey="flow-collaboration" />
              </Field>
              {picking === 'addSigners' && (
                <Field label={messages.addMode}>
                  <RadioGroup _value={mode}>
                    {(['before', 'after', 'parallel'] as const).map((value) => (
                      <Radio key={value} value={value}>
                        {messages.addModes[value]}
                      </Radio>
                    ))}
                  </RadioGroup>
                </Field>
              )}
              <Field label={messages.reason}>
                <Textarea _value={reason} rows={2} />
              </Field>
            </Flex>
          </Dialog>
        )}
      </Flex>
    );
  };
});
