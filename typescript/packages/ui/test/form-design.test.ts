// 表单设计器的解析与写回：源码 → 字段列表与组件树；设计操作只改受影响的那一段，其余一个字符不动
import { describe, expect, it } from 'vitest';
import {
  addField,
  applyEdits,
  codeFromManifest,
  designSource,
  ensureImport,
  extractFields,
  findNode,
  formComponents,
  isRegisteredFieldType,
  registerFormComponent,
  insertNode,
  moveNode,
  parseDesign,
  printComponent,
  removeField,
  removeNode,
  renameField,
  replaceTag,
  setField,
  setProp,
  setText,
  type TextEdit,
} from '../src/form-tools';

const SOURCE = `import { defineFields, defineFlowForm } from '@zerodep-dev/flow-ui/form';
import { FormSection, NumberField, TextField as Input } from '@zerodep-dev/vue-tsx-pro-ui';

export const fields = defineFields({
  title: { label: '标题', type: 'text', required: true },
  amount: { label: '金额', type: 'decimal', scale: 2 },
  note: { label: '备注', type: 'text' },
});

export default defineFlowForm(fields, ({ form }) => {
  // 这里的逻辑设计器不碰
  return () => (
    <form.Form>
      <FormSection title="基本信息">
        <form.Field name="title"><Input placeholder="请输入" /></form.Field>
        <form.Field name="amount"><NumberField /></form.Field>
      </FormSection>
      {flag && <div>自己写的</div>}
    </form.Form>
  );
});
`;

const run = (source: string, edits: readonly TextEdit[]) => applyEdits(source, edits);
const component = (name: string) => formComponents().find((c) => c.name === name)!;

describe('parseDesign', () => {
  it('字段、组件树、路径与没摆的字段', () => {
    const design = parseDesign(SOURCE);
    expect(design.fields.map((f) => f.name)).toEqual(['title', 'amount', 'note']);
    expect(design.unplaced).toEqual(['note']);
    const root = design.root!;
    expect(root.kind).toBe('form');
    expect(root.children.map((c) => [c.path, c.kind])).toEqual([
      ['0.0', 'component'],
      ['0.1', 'code'],
    ]);
    const title = findNode(root, '0.0.0')!;
    expect(title.kind).toBe('field');
    expect(title.field?.name).toBe('title');
    // 改了名字导入的也按模块与导出名认
    expect(title.component?.name).toBe('TextField');
    expect(title.field?.control?.props).toEqual([expect.objectContaining({ name: 'placeholder', value: { literal: '请输入' } })]);
    expect(findNode(root, '0.0')!.element?.props[0]?.value).toEqual({ literal: '基本信息' });
  });

  it('设计时的源码给每个节点加上 data-form-node，代码块里的元素用代码块的路径', () => {
    const marked = designSource(SOURCE, parseDesign(SOURCE));
    expect(marked).toContain('<form.Form data-form-node="0">');
    expect(marked).toContain('<FormSection data-form-node="0.0" title="基本信息">');
    expect(marked).toContain('<form.Field data-form-node="0.0.1" name="amount">');
    expect(marked).toContain('<div data-form-node="0.1">自己写的</div>');
  });
});

describe('组件树的写回', () => {
  it('插入：取兄弟元素的缩进，放在中间或最后', () => {
    const design = parseDesign(SOURCE);
    const field = '<form.Field name="note"><Input /></form.Field>';
    const before = run(SOURCE, insertNode(SOURCE, design, '0.0', 1, field));
    expect(before).toContain(`<Input placeholder="请输入" /></form.Field>\n        ${field}\n        <form.Field name="amount">`);
    const after = run(SOURCE, insertNode(SOURCE, design, '0.0', 2, field));
    expect(after).toContain(`<NumberField /></form.Field>\n        ${field}\n      </FormSection>`);
    expect(insertNode(SOURCE, design, '0.1', 0, field)).toEqual([]);
  });

  it('插入到空容器、自闭合的容器里', () => {
    const empty = SOURCE.replace('{flag && <div>自己写的</div>}', '<FormSection title="空"></FormSection>');
    let design = parseDesign(empty);
    expect(run(empty, insertNode(empty, design, '0.1', 0, '<Input />'))).toContain(
      '      <FormSection title="空">\n        <Input />\n      </FormSection>',
    );
    const closed = SOURCE.replace('{flag && <div>自己写的</div>}', '<FormSection title="空" />');
    design = parseDesign(closed);
    expect(run(closed, insertNode(closed, design, '0.1', 0, '<Input />'))).toContain(
      '      <FormSection title="空">\n        <Input />\n      </FormSection>',
    );
  });

  it('删除连同整行；移动是删加插，多行的代码跟着换缩进', () => {
    const design = parseDesign(SOURCE);
    const removed = run(SOURCE, removeNode(SOURCE, design, '0.0.0'));
    expect(removed).not.toContain('name="title"');
    expect(removed).toContain('<FormSection title="基本信息">\n        <form.Field name="amount">');

    const moved = run(SOURCE, moveNode(SOURCE, design, '0.0.1', '0', 0));
    expect(moved).toContain('<form.Form>\n      <form.Field name="amount"><NumberField /></form.Field>\n      <FormSection');
    expect(parseDesign(moved).root!.children.map((c) => c.kind)).toEqual(['field', 'component', 'code']);

    const section = run(SOURCE, moveNode(SOURCE, design, '0.0', '0', 2));
    expect(section).toContain('{flag && <div>自己写的</div>}\n      <FormSection title="基本信息">\n        <form.Field name="title">');
    // 挪进自己里面、挪回原处：不改
    expect(moveNode(SOURCE, design, '0.0', '0.0', 0)).toEqual([]);
    expect(moveNode(SOURCE, design, '0.0.0', '0.0', 1)).toEqual([]);
  });

  it('属性：换值、加在最后、删掉；换控件留着属性；改文字内容', () => {
    const design = parseDesign(SOURCE);
    const control = findNode(design.root, '0.0.0')!.field!.control!;
    expect(run(SOURCE, setProp(SOURCE, control, 'placeholder', { literal: '标题' }))).toContain('<Input placeholder="标题" />');
    expect(run(SOURCE, setProp(SOURCE, control, 'maxlength', { literal: 20 }))).toContain('<Input placeholder="请输入" maxlength={20} />');
    expect(run(SOURCE, setProp(SOURCE, control, 'placeholder', undefined))).toContain('<Input />');
    expect(run(SOURCE, setProp(SOURCE, control, 'disabled', { code: 'busy.value' }))).toContain('disabled={busy.value} />');
    expect(run(SOURCE, replaceTag(SOURCE, control, 'Textarea'))).toContain('<Textarea placeholder="请输入" />');

    const text = '<Text tone="muted">旧的</Text>';
    const withText = SOURCE.replace('{flag && <div>自己写的</div>}', text).replace('FormSection, ', 'FormSection, Text, ');
    const node = findNode(parseDesign(withText).root, '0.1')!;
    expect(node.element?.text).toBe('旧的');
    expect(run(withText, setText(node.element!, '新的 {x}'))).toContain(`<Text tone="muted">{'新的 {x}'}</Text>`);
  });

  it('属性一行一个时，新属性另起一行', () => {
    const source = SOURCE.replace('<FormSection title="基本信息">', '<FormSection\n        title="基本信息"\n        columns={2}\n      >');
    const section = findNode(parseDesign(source).root, '0.0')!.element!;
    expect(run(source, setProp(source, section, 'description', { literal: '说明' }))).toContain('columns={2}\n        description="说明"\n      >');
  });
});

describe('字段清单的写回', () => {
  it('改、加、删、改名', () => {
    const design = parseDesign(SOURCE);
    const changed = run(SOURCE, setField(SOURCE, design, 'note', { label: '说明', type: 'text', maxLength: 200 }));
    expect(changed).toContain("  note: { label: '说明', type: 'text', maxLength: 200 },\n});");

    const added = run(SOURCE, addField(SOURCE, design, 'field1', { label: '字段 1', type: 'text' }));
    expect(added).toContain("  note: { label: '备注', type: 'text' },\n  field1: { label: '字段 1', type: 'text' },\n});");
    const noComma = SOURCE.replace("note: { label: '备注', type: 'text' },", "note: { label: '备注', type: 'text' }");
    expect(run(noComma, addField(noComma, parseDesign(noComma), 'field1', { label: 'x', type: 'text' }))).toContain(
      "note: { label: '备注', type: 'text' },\n  field1: { label: 'x', type: 'text' }\n});",
    );

    const removed = run(SOURCE, removeField(SOURCE, design, 'amount'));
    expect(removed).toContain("required: true },\n  note:");
    expect(extractFields(removed).manifest).not.toHaveProperty('amount');

    const renamed = run(SOURCE, renameField(SOURCE, design, 'title', 'subject'));
    expect(renamed).toContain("  subject: { label: '标题'");
    expect(renamed).toContain('<form.Field name="subject">');
    expect(renameField(SOURCE, design, 'title', 'amount')).toEqual([]);
  });

  it('长的定义按缩进换行', () => {
    const design = parseDesign(SOURCE);
    const options = Array.from({ length: 6 }, (_, i) => ({ value: `v${i}`, label: `选项${i}` }));
    const out = run(SOURCE, setField(SOURCE, design, 'note', { label: '等级', type: 'select', options }));
    expect(out).toContain("  note: {\n    label: '等级',\n    type: 'select',\n    options: [\n      { value: 'v0', label: '选项0' },");
    expect(extractFields(out).manifest?.note).toEqual({ label: '等级', type: 'select', options });
  });
});

describe('导入', () => {
  it('已经导入的用本地名；同一个模块补在后面；没有时加一行', () => {
    const design = parseDesign(SOURCE);
    expect(ensureImport(SOURCE, design, '@zerodep-dev/vue-tsx-pro-ui', 'TextField')).toEqual({ local: 'Input', edits: [] });
    const switched = ensureImport(SOURCE, design, '@zerodep-dev/vue-tsx-pro-ui', 'Switch');
    expect(run(SOURCE, switched.edits)).toContain("import { FormSection, NumberField, TextField as Input, Switch } from '@zerodep-dev/vue-tsx-pro-ui';");
    const host = ensureImport(SOURCE, design, '@host/supplier', 'SupplierPicker');
    expect(run(SOURCE, host.edits)).toContain("from '@zerodep-dev/vue-tsx-pro-ui';\nimport { SupplierPicker } from '@host/supplier';\n");
  });
});

describe('codeFromManifest', () => {
  it('生成的代码每个字段都摆好、清单原样取得回来', () => {
    const manifest = {
      title: { label: '标题', type: 'text', required: true },
      amount: { label: '金额', type: 'decimal', scale: 2 },
      at: { label: '时间', type: 'datetime' },
      level: { label: '等级', type: 'select', options: [{ value: 1, label: '一' }] },
      lines: { label: '明细', type: 'list', fields: { item: { label: '物品', type: 'text' } } },
    } as const;
    const source = codeFromManifest(manifest);
    expect(extractFields(source).manifest).toEqual(manifest);
    const design = parseDesign(source);
    expect(design.unplaced).toEqual(['lines']);
    expect(source).toContain('<form.Field name="at"><DatePicker /></form.Field>');
    expect(source).toContain('<form.Field name="level"><Select /></form.Field>');
  });

  it('新放进来的组件：默认属性、内容，容器是成对的标签', () => {
    expect(printComponent(component('FormSection'))).toBe('<FormSection title="分组"></FormSection>');
    expect(printComponent(component('Text'))).toBe('<Text>说明文字</Text>');
    expect(printComponent(component('Separator'))).toBe('<Separator />');
  });
});

describe('接入方的字段类型', () => {
  it('登记的组件声明了 field 的类型，清单校验就认得', async () => {
    const { validateManifest } = await import('@zerodep-dev/flow-model');
    const manifest = { cost: { label: '金额', type: 'money' } };
    expect(validateManifest(manifest, { isFieldType: isRegisteredFieldType }).map((p) => p.code)).toEqual(['field-type-unknown']);
    registerFormComponent({ name: 'MoneyField', module: '@host/money', label: '外币金额', group: '业务', field: ['money'] });
    expect(isRegisteredFieldType('money')).toBe(true);
    expect(validateManifest(manifest, { isFieldType: isRegisteredFieldType })).toEqual([]);
  });
});
