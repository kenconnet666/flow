/**
 * 流程定义的编辑：设计器或 JSON、校验、发布。路径里是 new 时新建。
 */
import { FlowDefinitionEditor } from '@zerodep-dev/flow-ui/admin';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { useRoute, useRouter } from 'vue-router';
import { people, toaster } from '../session';

export const ProcessEditPage = _component(() => {
  const route = useRoute();
  const router = useRouter();
  return () => {
    const key = String(route.params.key);
    return (
      <FlowDefinitionEditor
        key={key}
        people={people}
        {...(key === 'new' ? {} : { processKey: key })}
        onPublished={(result) => {
          toaster.success(`已发布 ${result.key} 第 ${result.version} 版`);
          if (key === 'new') void router.replace(`/app/admin/processes/${result.key}`);
        }}
      />
    );
  };
});
