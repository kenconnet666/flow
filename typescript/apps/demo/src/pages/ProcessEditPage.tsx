/**
 * 流程定义的编辑：改 JSON、校验、发布（设计器之前的写法）。路径里是 new 时新建。
 */
import { FlowDefinitionEditor } from '@zerodep-dev/flow-ui/admin';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { useRoute, useRouter } from 'vue-router';
import { toaster } from '../session';

export const ProcessEditPage = _component(() => {
  const route = useRoute();
  const router = useRouter();
  return () => {
    const key = String(route.params.key);
    return (
      <FlowDefinitionEditor
        key={key}
        {...(key === 'new' ? {} : { processKey: key })}
        onPublished={(result) => {
          toaster.success(`已发布 ${result.key} 第 ${result.version} 版`);
          if (key === 'new') void router.replace(`/admin/processes/${result.key}`);
        }}
      />
    );
  };
});
