/**
 * 复制一个流程或表单：填新的标识和名字。标识不能和已有的重复，由后端发布时判。
 *
 *   <CopyDialog _open={copying} source={{ key, name }} onConfirm={(rename) => copy(rename)} />
 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Dialog, Field, Flex, TextField } from '@zerodep-dev/vue-tsx-pro-ui';
import { watch } from 'vue';
import { useFlowMessages } from '../lang/messages';
import type { Rename } from './transfer';

/** CopyDialog 的属性 */
export interface CopyDialogProps {
  /** 被复制的那个 */
  source: Rename | null;
  /** 打开：父级写 _open={x} 双向绑定 */
  _open?: boolean;
  /** 确定；返回 false 时不关 */
  onConfirm: (rename: Rename) => unknown;
}

/** 复制对话框 */
export const CopyDialog = _component(({ source, _open: open = _bindable(false), onConfirm }: CopyDialogProps) => {
  const messages = useFlowMessages();
  let key = _state('');
  let name = _state('');

  watch(
    () => open,
    (now) => {
      if (!now || !source) return;
      key = `${source.key}-copy`;
      name = messages.copyName(source.name);
    },
  );

  const ready = () => key.trim() !== '' && name.trim() !== '' && key.trim() !== source?.key;

  return () => (
    <Dialog
      _open={open}
      title={messages.copy}
      confirmText={messages.confirm}
      cancelText={messages.cancel}
      onConfirm={() => (ready() ? onConfirm({ key: key.trim(), name: name.trim() }) : false)}
      propsConfirm={{ disabled: !ready() }}
    >
      <Flex direction="column" gap="md">
        <Field label={messages.columnKey} required>
          <TextField _value={key} />
        </Field>
        <Field label={messages.columnTitle} required>
          <TextField _value={name} />
        </Field>
      </Flex>
    </Dialog>
  );
});
