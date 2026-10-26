// 请假单：代码表单的样子（0004 第 1 节）。示例前端在浏览器里编译它；字段清单要与这里的 fields 一致（seed.mjs 发布时从这里抄）
import { defineFields, defineFlowForm } from '@zerodep-dev/flow-ui/form';
import { Flex, FormSection, NumberField, Select, Textarea } from '@zerodep-dev/vue-tsx-pro-ui';

export const fields = defineFields({
  kind: {
    label: '假别',
    type: 'select',
    required: true,
    options: [
      { value: 'annual', label: '年假' },
      { value: 'sick', label: '病假' },
    ],
  },
  days: { label: '天数', type: 'number', required: true, min: 1, max: 30 },
  reason: { label: '事由', type: 'text', maxLength: 200 },
});

export default defineFlowForm(fields, ({ form }) => () => (
  <form.Form>
    <FormSection title="请假信息">
      <Flex direction="column" gap="md">
        <form.Field name="kind">
          <Select items={[{ value: 'annual', label: '年假' }, { value: 'sick', label: '病假' }]} />
        </form.Field>
        <form.Field name="days">
          <NumberField />
        </form.Field>
        <form.Field name="reason" description="病假请写明情况">
          <Textarea rows={3} />
        </form.Field>
      </Flex>
    </FormSection>
  </form.Form>
));
