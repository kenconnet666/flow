/**
 * 发起页：流程开始节点上的表单；提交后回到"我的待办"，顶上提示发起成功。带 ?draft= 时接着那份草稿填。
 */
import { StartForm } from '@zerodep-dev/flow-ui';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Flex, H1 } from '@zerodep-dev/vue-tsx-pro-ui';
import { useRoute, useRouter } from 'vue-router';
import { people, toaster } from '../session';

export const StartPage = _component(() => {
  const route = useRoute();
  const router = useRouter();
  return () => (
    <Flex direction="column" gap="lg">
      <H1>发起</H1>
      <StartForm
        processKey={String(route.params.key)}
        draftId={route.query.draft ? BigInt(String(route.query.draft)) : null}
        people={people}
        onStarted={(result) => {
          toaster.success(`已发起：${result.instance.title ?? result.instance.processKey}`);
          void router.push('/app/inbox');
        }}
      />
    </Flex>
  );
});
