/** 外部参与方看的进度：只有在哪个环节、谁在办、预计什么时候答复，不含表单与流转记录 */
import { FlowProgress } from '@zerodep-dev/flow-ui';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { useRoute } from 'vue-router';

export const ProgressPage = _component(() => {
  const route = useRoute();
  return () => <FlowProgress instanceId={BigInt(String(route.params.id))} />;
});
