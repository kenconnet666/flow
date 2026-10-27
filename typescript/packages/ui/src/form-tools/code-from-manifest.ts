/**
 * 按字段清单生成一份代码表单（0004 第 1 节的写法）：只写了清单、想改排版或加联动时，从这份开始改。
 * 每个字段摆它类型的默认控件（同设计器，design/registry.ts）；明细表、附件生成成注释，按需要自己写。
 *
 *   const source = codeFromManifest(manifest);   // export const fields = defineFields({ … }); export default defineFlowForm(…)
 */
import type { FormManifest } from '@zerodep-dev/flow-model';
import { printField, printLiteral } from './design/print';
import { controlsFor, FORM_MODULE, UI_MODULE } from './design/registry';

/** 生成代码表单的源码 */
export function codeFromManifest(manifest: FormManifest): string {
  const components = new Set<string>(['Flex']);
  const fields = Object.entries(manifest).map(([name, field]) => {
    const control = controlsFor(field).find((c) => c.module === UI_MODULE);
    if (!control) return `      {/* ${name}：${field.label}，按需要自己写 */}`;
    components.add(control.name);
    return `      ${printField(name, control)}`;
  });
  return [
    `import { defineFields, defineFlowForm } from '${FORM_MODULE}';`,
    `import { ${[...components].sort().join(', ')} } from '${UI_MODULE}';`,
    '',
    `export const fields = defineFields(${printLiteral(manifest)});`,
    '',
    'export default defineFlowForm(fields, ({ form }) => () => (',
    '  <form.Form>',
    '    <Flex direction="column" gap="md">',
    ...fields,
    '    </Flex>',
    '  </form.Form>',
    '));',
    '',
  ].join('\n');
}
