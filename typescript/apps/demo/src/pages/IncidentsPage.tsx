/**
 * 异常处理（管理端）：自动节点重试用完的作业，每行能重试、跳过并按结果往下走；点标题打开带管理员操作的流程详情。
 * 示例后端注册了动作 demo.push（参数 fail 为 true 时总是失败），流程里用它就能看到异常。
 */
import { FlowIncidentList } from '@zerodep-dev/flow-ui/admin';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { useRouter } from 'vue-router';

export const IncidentsPage = _component(() => {
  const router = useRouter();
  return () => <FlowIncidentList onOpen={(incident) => void router.push(`/instances/${incident.instanceId}?admin=1`)} />;
});
