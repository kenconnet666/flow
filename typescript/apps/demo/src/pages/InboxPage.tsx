/**
 * 我的待办：点标题进办理页。
 */
import { FlowInbox } from '@zerodep-dev/flow-ui';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { useRouter } from 'vue-router';

export const InboxPage = _component(() => {
  const router = useRouter();
  return () => <FlowInbox onOpen={(item) => void router.push(`/work-items/${item.workItem.id}`)} />;
});
