/**
 * 表单管理（管理端）：全部表单，点标题或"编辑"进表单的编辑，"新建表单"新建；每行能停用、启用。
 */
import { FlowFormList } from '@zerodep-dev/flow-ui/admin';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { useRouter } from 'vue-router';

export const FormsPage = _component(() => {
  const router = useRouter();
  return () => <FlowFormList onEdit={(key) => void router.push(key ? `/admin/forms/${encodeURIComponent(key)}` : '/admin/forms/new')} />;
});
