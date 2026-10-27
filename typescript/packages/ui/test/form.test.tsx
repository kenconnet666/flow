// 表单容器：没有代码时按清单自动排版；节点权限（隐藏、只读、能改）与显示条件随填写变；校验用模型包的规则；值是强类型（decimal 是 Decimal）
import Decimal from 'decimal.js';
import { describe, expect, it } from 'vitest';
import type { FormView } from '../src/client/types';
import type { FlowFormHandle } from '../src/form/context';
import { FlowForm } from '../src/form/FlowForm';
import { mount, settle } from './mount';

const view: FormView = {
  versionId: 1n,
  key: 'purchase-form',
  version: 1,
  manifest: {
    title: { label: '标题', type: 'text', required: true },
    amount: { label: '金额', type: 'decimal', scale: 2, required: true },
    quantity: { label: '数量', type: 'number', integer: true, min: 1 },
    urgent: { label: '加急', type: 'boolean' },
    reason: { label: '加急原因', type: 'text', visibleWhen: { field: 'form.urgent', op: 'eq', value: true } },
    note: { label: '财务意见', type: 'text' },
  },
  source: null,
  compiled: null,
  compilerVersion: null,
  access: { default: 'editable', fields: { note: 'hidden' } },
  start: true,
  values: { amount: new Decimal('12.50') },
};

/** 挂一张表单，拿到它交回的操作 */
async function mountForm(overrides: Partial<FormView> = {}, mode: 'fill' | 'view' = 'fill') {
  let handle: FlowFormHandle | undefined;
  const host = mount(() => <FlowForm view={{ ...view, ...overrides }} mode={mode} onReady={(form) => (handle = form)} />);
  await settle();
  await settle();
  return { host, handle: handle! };
}

const labels = (host: HTMLElement) => [...host.querySelectorAll('label')].map((label) => label.textContent?.replace('*', '').trim());

describe('FlowForm', () => {
  it('没有代码时按清单排版，节点上隐藏的字段不出现，显示条件不成立的也不出现', async () => {
    const { host } = await mountForm();
    expect(labels(host)).toEqual(expect.arrayContaining(['标题', '金额', '数量', '加急']));
    expect(labels(host)).not.toContain('财务意见');
    expect(labels(host)).not.toContain('加急原因');
  });

  it('在控件里输入的值写回表单（Field 认出只有一个控件，自动绑定）', async () => {
    const { host, handle } = await mountForm();
    const input = host.querySelector('input[type="text"]') as HTMLInputElement;
    input.value = '办公桌';
    input.dispatchEvent(new Event('input'));
    await settle();
    expect(handle.values()).toMatchObject({ title: '办公桌' });
  });

  it('取值：值原样是强类型（decimal 是 Decimal），没填的是 null', async () => {
    const { handle } = await mountForm();
    expect(handle.values()).toMatchObject({ title: null, amount: new Decimal('12.50'), quantity: null, urgent: false });
  });

  it('校验：必填没填、数字不合范围时不通过，并标到字段上', async () => {
    const { host, handle } = await mountForm({ values: { amount: new Decimal('1.00'), quantity: 0 } });
    expect(await handle.validate()).toBe(false);
    await settle();
    expect(host.textContent).toContain('必填');
    expect(host.textContent).toContain('太小');
  });

  it('引擎退回的按字段的问题标到字段上', async () => {
    const { host, handle } = await mountForm({ values: { title: '桌子', amount: new Decimal('1.00') } });
    expect(await handle.validate()).toBe(true);
    handle.showErrors([{ field: 'title', code: 'maxLength' }]);
    await settle();
    expect(host.textContent).toContain('太长');
  });

  it('查看时全部只读', async () => {
    const { host } = await mountForm({ values: { title: '桌子' } }, 'view');
    const input = host.querySelector('input[type="text"], input:not([type])') as HTMLInputElement | null;
    expect(input?.readOnly).toBe(true);
  });
});

describe('明细表（form.List）', () => {
  const lines: Partial<FormView> = {
    manifest: {
      lines: { label: '明细', type: 'list', fields: { item: { label: '品名', type: 'text', required: true }, price: { label: '单价', type: 'decimal', scale: 2 } } },
    },
    access: { default: 'editable' },
    values: { lines: [{ item: '显示器', price: new Decimal('1200.00') }] },
  };

  it('每行按列的类型放控件；加一行、删一行写回表单的值', async () => {
    const { host, handle } = await mountForm(lines);
    expect(labels(host)).toEqual(['品名', '单价']);
    const add = [...host.querySelectorAll('button')].find((b) => b.textContent?.includes('明细'))!;
    add.click();
    await settle();
    expect(labels(host)).toEqual(['品名', '单价', '品名', '单价']);
    expect(handle.values()['lines']).toEqual([{ item: '显示器', price: new Decimal('1200.00') }, { item: null, price: null }]);
    host.querySelector<HTMLButtonElement>('button[aria-label="删除第 1 行"]')!.click();
    await settle();
    expect((handle.values()['lines'] as unknown[]).length).toBe(1);
  });

  it('列的必填按行校验；查看时不能加行、删行', async () => {
    const { host, handle } = await mountForm({ ...lines, values: { lines: [{ item: null, price: null }] } });
    expect(await handle.validate()).toBe(false);
    const viewing = await mountForm(lines, 'view');
    expect(viewing.host.querySelectorAll('button').length).toBe(0);
    host.remove();
  });
});

describe('FlowForm 用哪份代码', () => {
  const source = `import { defineFields, defineFlowForm } from '@zerodep-dev/flow-ui/form';
import { TextField } from '@zerodep-dev/vue-tsx-pro-ui';
export const fields = defineFields({ title: { label: '源码里的标题', type: 'text' } });
export default defineFlowForm(fields, ({ form }) => () => (
  <form.Form><form.Field name="title"><TextField /></form.Field></form.Form>
));`;
  // 执行时没有默认导出：用了它就报错
  const broken = '__exports.fields = {};';
  const manifest = { title: { label: '源码里的标题', type: 'text' } } as const;
  /** 等表单加载完（第一次要加载编译器与表单写法的模块，较慢） */
  const loaded = async (host: HTMLElement) => {
    for (let waited = 0; !host.textContent && waited < 20_000; waited += 50) await new Promise((resolve) => setTimeout(resolve, 50));
  };

  it('编译结果是别的编译器版本编的：按源码重新编译', async () => {
    const { host } = await mountForm({ manifest, source, compiled: broken, compilerVersion: 'old/0', values: {} });
    await loaded(host);
    expect(labels(host)).toContain('源码里的标题');
  }, 30_000);

  it('编译器版本一样：直接执行编译结果', async () => {
    const { COMPILER_VERSION } = await import('@zerodep-dev/vue-tsx-pro-tsx/browser-compile');
    const { host } = await mountForm({ manifest, source, compiled: broken, compilerVersion: COMPILER_VERSION, values: {} });
    await loaded(host);
    expect(host.textContent).toContain('no default export');
  }, 30_000);
});
