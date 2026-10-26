/**
 * 按字段清单生成一份代码表单（0004 第 1 节的写法）：只写了清单、想改排版或加联动时，从这份开始改。
 * 控件与自动排版（autoFlowForm）的挑法相同；明细表、附件生成成注释，按需要自己写。
 *
 *   const source = codeFromManifest(manifest);   // export const fields = defineFields({ … }); export default defineFlowForm(…)
 */
import type { FieldDefinition, FormManifest } from '@zerodep-dev/flow-model';

/** 生成代码表单的源码 */
export function codeFromManifest(manifest: FormManifest): string {
  const components = new Set<string>(['Flex']);
  const fields = Object.entries(manifest).map(([name, field]) => {
    const control = controlOf(field, components);
    const line = control ? `<form.Field name="${name}">${control}</form.Field>` : `{/* ${name}：${field.label}，按需要自己写 */}`;
    return `      ${line}`;
  });
  return [
    `import { defineFields, defineFlowForm } from '@zerodep-dev/flow-ui/form';`,
    `import { ${[...components].sort().join(', ')} } from '@zerodep-dev/vue-tsx-pro-ui';`,
    '',
    `export const fields = defineFields(${JSON.stringify(manifest, null, 2)});`,
    '',
    'export default defineFlowForm(fields, ({ form, context }) => () => (',
    '  <form.Form>',
    '    <Flex direction="column" gap="md">',
    ...fields,
    '    </Flex>',
    '  </form.Form>',
    '));',
    '',
  ].join('\n');
}

/** 按字段类型挑控件（同 autoFlowForm），用到的组件记进 components；明细表、附件返回 null */
function controlOf(field: FieldDefinition, components: Set<string>): string | null {
  const use = (component: string, props = '') => {
    components.add(component);
    return `<${component}${props} />`;
  };
  switch (field.type) {
    case 'number':
    case 'decimal':
      return use('NumberField');
    case 'boolean':
      return use('Switch');
    case 'date':
      return use('DatePicker');
    case 'datetime':
      return use('DatePicker', ' granularity="minute"');
    case 'select': {
      const options = Array.isArray(field.options) ? field.options : [];
      const items = JSON.stringify(options.map((option) => ({ value: String(option.value), label: option.label })));
      return use('Select', ` items={${items}}${field.multiple ? ' selectionMode="multiple"' : ''}`);
    }
    case 'people':
      return use('PeoplePicker', ' source={context.people!}');
    case 'list':
    case 'file':
      return null;
    default:
      return (field.maxLength ?? 0) > 100 ? use('Textarea') : use('TextField');
  }
}
