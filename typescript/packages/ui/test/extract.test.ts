// 从源码取出字段清单：字面量照读（注释、尾逗号、不加引号的键、as const 都行），别的写法报问题并指出行列
import { describe, expect, it } from 'vitest';
import { codeFromManifest } from '../src/form-tools/code-from-manifest';
import { extractFields } from '../src/form-tools/extract';

describe('extractFields', () => {
  it('读出 defineFields 的字面量参数', () => {
    const { manifest, problems } = extractFields(`
import { defineFields, defineFlowForm } from '@zerodep-dev/flow-ui/form';
export const fields = defineFields({
  // 标题
  title: { label: '标题', type: 'text', required: true, },
  'amount': { label: "金额", type: 'decimal', scale: 2, min: -1 },
  lines: { label: '明细', type: 'list', fields: { item: { label: '物品', type: 'text' } } },
  kind: { label: '类型', type: 'select', options: [{ value: 1, label: '甲' }, { value: 'b', label: null }] },
} as const);
export default defineFlowForm(fields, () => () => null);
`);
    expect(problems).toEqual([]);
    expect(manifest).toEqual({
      title: { label: '标题', type: 'text', required: true },
      amount: { label: '金额', type: 'decimal', scale: 2, min: -1 },
      lines: { label: '明细', type: 'list', fields: { item: { label: '物品', type: 'text' } } },
      kind: { label: '类型', type: 'select', options: [{ value: 1, label: '甲' }, { value: 'b', label: null }] },
    });
  });

  it('变量、展开、计算的键、模板字符串都报问题，带行列', () => {
    const { manifest, problems } = extractFields(`const shared = { label: 'x', type: 'text' };
export const fields = defineFields({
  a: shared,
  ...shared,
  [name]: { label: 'y', type: 'text' },
  b: { label: \`z\`, type: 'text' },
});`);
    expect(manifest).toBeNull();
    expect(problems.map((p) => [p.code, p.line])).toEqual([
      ['not-literal', 3],
      ['not-literal', 4],
      ['not-literal', 5],
      ['not-literal', 6],
    ]);
  });

  it('没有导出 fields、或不是 defineFields(…) 时报问题', () => {
    expect(extractFields('export default 1;').problems[0]?.code).toBe('fields-missing');
    expect(extractFields('export const fields = { a: 1 };').problems[0]?.code).toBe('fields-not-define');
  });
});

describe('codeFromManifest', () => {
  it('按清单生成的代码再取出来还是那份清单，控件按字段类型挑', () => {
    const manifest = {
      title: { label: '标题', type: 'text', required: true },
      amount: { label: '金额', type: 'decimal', scale: 2 },
      urgent: { label: '加急', type: 'boolean' },
      kind: { label: '类型', type: 'select', options: [{ value: 'a', label: '甲' }] },
      lines: { label: '明细', type: 'list', fields: { item: { label: '物品', type: 'text' } } },
    } as const;
    const source = codeFromManifest(manifest);
    expect(extractFields(source)).toEqual({ manifest, problems: [] });
    expect(source).toContain(`import { Flex, NumberField, Select, Switch, TextField } from '@zerodep-dev/vue-tsx-pro-ui';`);
    expect(source).toContain('<form.Field name="amount"><NumberField /></form.Field>');
    expect(source).toContain('{/* lines：明细，按需要自己写 */}');
  });
});
