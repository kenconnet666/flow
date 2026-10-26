/**
 * 选下一步的处理人（0005 的 5.6）：提交时要选人的每个下一步一个选人框。能选的人由引擎给（PickView.candidates）；
 * 引擎说"谁都可以"时用接入方给的人员数据来源。每一步都选了才能确定。
 *
 *   <PickDialog _open={picking} picks={outcome.picks} people={peopleSource} onConfirm={(picks) => complete(outcome, picks)} />
 *
 * onConfirm 返回 false（或 resolve 成 false）时对话框不关：提交失败，留着让人改。
 */
import type { Id } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import {
  Dialog,
  Field,
  Flex,
  localPeopleSource,
  PeoplePicker,
  Text,
  type PeopleEntry,
  type PeopleSource,
} from '@zerodep-dev/vue-tsx-pro-ui';
import { watch } from 'vue';
import type { PickView } from '../client/types';
import { useFlowMessages } from '../lang/messages';

/** PickDialog 的属性 */
export interface PickDialogProps {
  /** 要选人的下一步 */
  picks: readonly PickView[];
  /** 引擎说"谁都可以选"时的人员数据来源 */
  people?: PeopleSource;
  /** 打开：父级写 _open={x} 双向绑定 */
  _open?: boolean;
  /** 确定：节点 → 选的人；返回 false 时不关 */
  onConfirm: (picks: Record<string, Id[]>) => unknown;
}

/** 引擎给的能选的人做成一个只有这些人的数据来源 */
const sourceOf = (pick: PickView): PeopleSource | undefined =>
  pick.candidates ? localPeopleSource(pick.candidates.map((c) => ({ id: String(c.id), type: 'user' as const, name: c.name }))) : undefined;

/** 选下一步的处理人 */
export const PickDialog = _component(({ picks, people, _open: open = _bindable(false), onConfirm }: PickDialogProps) => {
  const messages = useFlowMessages();
  let chosen = _state<Record<string, readonly PeopleEntry[]>>({});

  // 每次打开都从空的开始选
  watch(
    () => open,
    (now) => {
      if (now) chosen = {};
    },
  );

  const ready = () => picks.every((pick) => (chosen[pick.node]?.length ?? 0) > 0);

  function confirm() {
    if (!ready()) return false;
    const out: Record<string, Id[]> = {};
    for (const pick of picks) out[pick.node] = (chosen[pick.node] ?? []).map((entry) => BigInt(entry.id));
    return onConfirm(out);
  }

  return () => (
    <Dialog
      _open={open}
      title={messages.pickTitle}
      confirmText={messages.confirm}
      cancelText={messages.cancel}
      onConfirm={confirm}
      propsConfirm={{ disabled: !ready() }}
    >
      <Flex direction="column" gap="md">
        {picks.map((pick) => {
          const source = sourceOf(pick) ?? people;
          return (
            <Field key={pick.node} label={messages.pickFor(pick.nodeName ?? pick.node)} required>
              {source ? (
                <PeoplePicker
                  source={source}
                  _value={chosen[pick.node] ?? []}
                  onUpdate:_value={(value: readonly PeopleEntry[]) => (chosen = { ...chosen, [pick.node]: value })}
                  {...(pick.max != null ? { max: pick.max } : {})}
                  recentKey="flow-pick"
                />
              ) : (
                <Text tone="muted">{messages.noPeopleSource}</Text>
              )}
            </Field>
          );
        })}
      </Flex>
    </Dialog>
  );
});
