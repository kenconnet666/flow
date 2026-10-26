/**
 * 办理：待办详情（表单、意见与处理结果、流转记录）；处理完提示并回到"我的待办"。
 */
import { WorkItemDetail } from '@zerodep-dev/flow-ui';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Flex, H1 } from '@zerodep-dev/vue-tsx-pro-ui';
import { useRoute, useRouter } from 'vue-router';
import { people, toaster } from '../session';

export const WorkItemPage = _component(() => {
  const route = useRoute();
  const router = useRouter();
  return () => (
    <Flex direction="column" gap="lg">
      <H1>办理</H1>
      <WorkItemDetail
        id={BigInt(String(route.params.id))}
        people={people}
        onDone={(result) => {
          // 认领之后还在这一页办理；处理完（这个待办结束了）才回收件箱
          if (result.closed.length === 0) return;
          toaster.success('已处理');
          void router.push('/inbox');
        }}
      />
    </Flex>
  );
});
