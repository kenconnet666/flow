/**
 * 流程详情：进度、只读的表单、流转记录；发起人能撤回、催办，谁都能留言。
 */
import { FlowInstanceDetail } from '@zerodep-dev/flow-ui';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Flex, H1 } from '@zerodep-dev/vue-tsx-pro-ui';
import { useRoute } from 'vue-router';
import { people, toaster } from '../session';

export const InstancePage = _component(() => {
  const route = useRoute();
  return () => (
    <Flex direction="column" gap="lg">
      <H1>流程详情</H1>
      <FlowInstanceDetail
        id={BigInt(String(route.params.id))}
        people={people}
        onChange={(result) => result.instance.status === 'withdrawn' && toaster.success('已撤回')}
      />
    </Flex>
  );
});
