/**
 * 办理：待办详情（表单、意见与处理结果、转办委派加签、流转记录）；这个待办结束了（处理完、转办走了）提示并回到"我的待办"。
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
          // 认领、委派、加签之后还在这一页；这个待办结束了（处理完、转办走了）才回收件箱
          if (result.closed.length === 0) return;
          if (result.events.some((e) => e.type === 'workItem.withdrawn')) {
            toaster.success('已撤回，可以重新处理');
            return;
          }
          toaster.success(result.events.some((e) => e.type === 'workItem.transferred') ? '已转办' : '已处理');
          void router.push('/app/inbox');
        }}
      />
    </Flex>
  );
});
