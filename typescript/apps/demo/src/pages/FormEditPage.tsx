/**
 * 表单的编辑：/admin/forms/new 新建，/admin/forms/:key 编辑已有的。字段清单或代码两种写法，右边是预览；发布后回到表单管理。
 */
import { FlowFormEditor } from '@zerodep-dev/flow-ui/admin';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Button, Flex } from '@zerodep-dev/vue-tsx-pro-ui';
import { useRoute, useRouter } from 'vue-router';
import { people, toaster } from '../session';

export const FormEditPage = _component(() => {
  const route = useRoute();
  const router = useRouter();
  const key = () => (route.params['key'] === 'new' ? undefined : String(route.params['key']));
  return () => (
    <Flex direction="column" gap="md">
      <Flex>
        <Button variant="ghost" onClick={() => void router.push('/admin/forms')}>
          返回表单管理
        </Button>
      </Flex>
      <FlowFormEditor
        key={String(route.params['key'])}
        {...(key() ? { formKey: key()! } : {})}
        people={people}
        onPublished={(result) => {
          toaster.success(`已发布 ${result.key} 第 ${result.version} 版`);
          void router.push('/admin/forms');
        }}
      />
    </Flex>
  );
});
