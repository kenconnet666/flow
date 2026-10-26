/**
 * 待我指定：没人认领、等我这个收件箱负责人指定承办人的待办；点标题进办理页，在那里"指定承办人"。
 */
import { FlowInbox } from '@zerodep-dev/flow-ui';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { useRouter } from 'vue-router';

export const DesignationsPage = _component(() => {
  const router = useRouter();
  return () => <FlowInbox list="designations" onOpen={(item) => void router.push(`/work-items/${item.workItem.id}`)} />;
});
