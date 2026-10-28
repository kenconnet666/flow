/**
 * 抄送我的：点标题看流程详情（打开时标成已读）。
 */
import { FlowCcList } from '@zerodep-dev/flow-ui';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { useRouter } from 'vue-router';

export const CcPage = _component(() => {
  const router = useRouter();
  return () => <FlowCcList onOpen={(item) => void router.push(`/app/instances/${item.instanceId}`)} />;
});
