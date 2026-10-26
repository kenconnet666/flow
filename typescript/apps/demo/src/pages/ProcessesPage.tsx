/**
 * 流程管理（管理端）：全部流程，编辑定义、停用或启用、新建。
 */
import { FlowProcessList } from '@zerodep-dev/flow-ui/admin';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { useRouter } from 'vue-router';

export const ProcessesPage = _component(() => {
  const router = useRouter();
  return () => <FlowProcessList onEdit={(key) => void router.push(`/admin/processes/${key ?? 'new'}`)} />;
});
