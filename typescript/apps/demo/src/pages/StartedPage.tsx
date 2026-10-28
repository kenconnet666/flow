/**
 * 我发起的：点标题看流程详情（撤回、催办、留言在那里）。
 */
import { FlowStartedList } from '@zerodep-dev/flow-ui';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { useRouter } from 'vue-router';

export const StartedPage = _component(() => {
  const router = useRouter();
  return () => <FlowStartedList onOpen={(instance) => void router.push(`/app/instances/${instance.id}`)} />;
});
