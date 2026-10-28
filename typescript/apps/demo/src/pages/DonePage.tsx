/**
 * 我已办：点标题打开那个待办（走到哪了、后面的人怎么处理的；下一步还没人动时能撤回这一步）。
 */
import { FlowInbox } from '@zerodep-dev/flow-ui';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { useRouter } from 'vue-router';

export const DonePage = _component(() => {
  const router = useRouter();
  return () => <FlowInbox list="done" onOpen={(item) => void router.push(`/app/work-items/${item.id}`)} />;
});
