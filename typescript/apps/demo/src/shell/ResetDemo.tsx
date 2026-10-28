/**
 * 顶栏的"重置演示"：选一档（从头开始 / 教程做完），确认后清空示例后端里引擎的数据、重新发布种子（seed/demo-seed.ts，同 `pnpm seed`），
 * 再让当前页重新加载数据。部署出去的演示站点上谁都能点，所以先说清楚会清空所有人的数据。
 */
import { RotateCcw } from '@lucide/icons';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Button, Dialog, Field, Flex, IconButton, Radio, RadioGroup, useErrorMessage } from '@zerodep-dev/vue-tsx-pro-ui';
import { ref } from 'vue';
import purchaseForm from '../../../../../spec/scenarios/forms/purchase-form.json';
import purchase from '../../../../../spec/scenarios/processes/purchase.json';
import erpPush from '../../seed/erp-push.json';
import extDemo from '../../seed/ext-demo.json';
import leave from '../../seed/leave.json';
import leaveForm from '../../seed/leave-form.tsx?raw';
import tutorials from '../../seed/tutorials.json';
import { seedDemo, type DemoPreset } from '../../seed/demo-seed';
import { toaster } from '../session';

/** 重置过几次：外壳把它放进当前页的 key，重置后各页重新挂载、重新请求 */
export const resets = ref(0);

/** ResetDemo 的属性 */
export interface ResetDemoProps {
  /** 只显示图标（窄屏的顶栏） */
  compact?: boolean;
}

export const ResetDemo = _component(({ compact = false }: ResetDemoProps) => {
  const errorMessage = useErrorMessage();
  let open = _state(false);
  let preset = _state<DemoPreset>('tutorials');
  let error = _state<string | null>(null);

  /** Dialog 的确认：失败时留在对话框里、说明原因 */
  async function reset() {
    error = null;
    try {
      await seedDemo('', preset, { purchaseForm, purchase, leaveForm, leave, extDemo, erpPush, tutorials });
    } catch (e) {
      error = errorMessage(e);
      return false;
    }
    resets.value++;
    toaster.success(preset === 'basic' ? '已重置：只有示例流程' : '已重置：教程都做完了，还有几单在办');
    return true;
  }

  return () => (
    <>
      {compact ? (
        <IconButton icon={RotateCcw} aria-label="重置演示" onClick={() => (open = true)} />
      ) : (
        <Button variant="ghost" icon={RotateCcw} onClick={() => (open = true)}>
          重置演示
        </Button>
      )}
      <Dialog
        _open={open}
        title="重置演示"
        tone="danger"
        confirmText="清空并重置"
        onConfirm={reset}
      >
        <Flex direction="column" gap="md">
        <Field label="重置成">
          <RadioGroup _value={preset}>
            <Radio value="tutorials" description="六个搭建教程的流程与报销单都已发布，还有几单在办、一单办完、一条异常：直接试日常使用、协作与管理">
              教程做完
            </Radio>
            <Radio value="basic" description="只有示例流程（采购申请、请假、扩展演示）：照着搭建教程从零画">
              从头开始
            </Radio>
          </RadioGroup>
        </Field>
        <Alert tone="warning">所有人的数据都会清空：流程、表单、单据、待办、附件。</Alert>
        {error && <Alert tone="danger">{error}</Alert>}
        </Flex>
      </Dialog>
    </>
  );
});
