/**
 * 我已办：点标题看流程详情（走到哪了、后面的人怎么处理的）。
 */
import { FlowInbox } from '@zerodep-dev/flow-ui';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { useRouter } from 'vue-router';

export const DonePage = _component(() => {
  const router = useRouter();
  return () => <FlowInbox list="done" onOpen={(item) => void router.push(`/instances/${item.workItem.instanceId}`)} />;
});
