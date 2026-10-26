/**
 * 流程监控（管理端）：全部实例，按状态、流程、标题筛；点标题打开带管理员操作的流程详情。
 * 示例后端的 PermissionPolicy 默认放行，所以谁都能看；真实应用只给管理员。
 */
import { FlowInstanceMonitor } from '@zerodep-dev/flow-ui';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { useRouter } from 'vue-router';

export const MonitorPage = _component(() => {
  const router = useRouter();
  return () => <FlowInstanceMonitor onOpen={(instance) => void router.push(`/instances/${instance.id}?admin=1`)} />;
});
