/**
 * 把实例迁到流程的另一个版本（管理员）：挑版本；实例正停着的节点在那个版本里没有同名的，要逐个指定换成哪个节点。
 * 能不能迁由后端再判一遍（节点类型对不上、新版本里没有都会拒绝）。
 *
 *   <MigrateDialog _open={migrating} detail={data} onConfirm={(command) => onRun('migrate', () => flow.migrate(command))} />
 */
import type { ProcessDefinition } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Dialog, Field, Flex, Select, Text, Textarea, useRequest } from '@zerodep-dev/vue-tsx-pro-ui';
import { watch } from 'vue';
import { useFlowClient } from '../client/flow-client';
import type { InstanceDetail, MigrateCommand } from '../client/types';
import { useFlowMessages } from '../lang/messages';

/** MigrateDialog 的属性 */
export interface MigrateDialogProps {
  detail: InstanceDetail;
  /** 打开：父级写 _open={x} 双向绑定 */
  _open?: boolean;
  /** 确定；返回 false 时不关 */
  onConfirm: (command: MigrateCommand) => unknown;
}

/** 迁移对话框 */
export const MigrateDialog = _component(({ detail, _open: open = _bindable(false), onConfirm }: MigrateDialogProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  let version = _state<string | null>(null);
  let mapping = _state<Record<string, string | null>>({});
  let reason = _state('');

  const versions = useRequest((key: string | null, signal) => (key ? flow.versions(key, signal) : Promise.resolve([])), {
    params: () => (open ? detail.instance.processKey : null),
  });
  const target = useRequest(
    async (picked: number | null, signal): Promise<ProcessDefinition | null> =>
      picked == null ? null : JSON.parse((await flow.definitionJson(detail.instance.processKey, picked, signal)).json),
    { params: () => (version ? Number(version) : null) },
  );

  watch(
    () => open,
    (now) => {
      if (!now) return;
      version = null;
      mapping = {};
      reason = '';
    },
  );

  /** 正停着、目标版本里又没有同名节点的：要指定换成哪个 */
  const missing = () => {
    const ids = new Set(target.data?.nodes.map((n) => n.id) ?? []);
    return target.data ? detail.instance.current.filter((c) => !ids.has(c.node)) : [];
  };
  const ready = () => target.data != null && reason.trim() !== '' && missing().every((c) => mapping[c.node]);

  function confirm() {
    if (!ready()) return false;
    const nodes: Record<string, string> = {};
    for (const c of missing()) nodes[c.node] = mapping[c.node]!;
    return onConfirm({ instanceId: detail.instance.id, version: Number(version), nodes, reason: reason.trim() });
  }

  return () => (
    <Dialog
      _open={open}
      title={messages.migrate}
      confirmText={messages.confirm}
      cancelText={messages.cancel}
      onConfirm={confirm}
      propsConfirm={{ disabled: !ready() }}
    >
      <Flex direction="column" gap="md">
        <Text tone="muted">{messages.migrateHint}</Text>
        <Field label={messages.migrateVersion} required>
          <Select
            items={(versions.data ?? [])
              .filter((v) => v.version !== detail.instance.version)
              .map((v) => ({ value: String(v.version), label: `v${v.version}${v.note ? ` · ${v.note}` : ''}` }))}
            _value={version}
          />
        </Field>
        {missing().map((c) => (
          <Field key={c.node} label={messages.migrateNodeTo(c.name ?? c.node)} required>
            <Select
              items={(target.data?.nodes ?? []).map((n) => ({ value: n.id, label: n.name ?? n.id }))}
              _value={mapping[c.node] ?? null}
              onUpdate:_value={(picked: string | null) => (mapping = { ...mapping, [c.node]: picked })}
            />
          </Field>
        ))}
        <Field label={messages.reason} required>
          <Textarea _value={reason} rows={2} />
        </Field>
      </Flex>
    </Dialog>
  );
});
