// 扩展的参数表单（设计器里解析器、动作、自定义节点的属性、节点业务信息共用）：按声明画控件，必填没填时提示，取自引用的只显示来源
import { describe, expect, it } from 'vitest';
import type { ExtensionParam, Incident } from '../src/client/types';
import { ParamsForm } from '../src/admin/process-designer/extensions';
import { incidentDoneText, isDelivery } from '../src/runtime/IncidentActions';
import { zhCN } from '../src/lang/zh-CN';
import { mount, settle } from './mount';

const param = (key: string, type: string, extra: Partial<ExtensionParam> = {}): ExtensionParam => ({
  key,
  label: key,
  type,
  required: false,
  description: null,
  options: [],
  ...extra,
});

describe('ParamsForm', () => {
  it('按类型画控件；必填没填时标出来', async () => {
    const host = mount(() => (
      <ParamsForm
        params={[param('level', 'number', { required: true }), param('note', 'text'), param('strict', 'boolean'), param('kind', 'select', { options: [{ value: 'a', label: '甲' }] })]}
        value={undefined}
        mergeKey="n"
        onChange={() => {}}
      />
    ));
    await settle();
    expect(host.querySelector('[role="spinbutton"], input[inputmode]')).not.toBeNull();
    expect(host.querySelector('[role="switch"]')).not.toBeNull();
    expect(host.querySelector('[role="combobox"]')).not.toBeNull();
    expect(host.textContent).toContain('必填');
  });

  it('改了一个参数写回整份；全空时写回 undefined（定义里不留空对象）', async () => {
    const changes: (Record<string, unknown> | undefined)[] = [];
    const host = mount(() => <ParamsForm params={[param('note', 'text')]} value={{ note: '旧' }} mergeKey="n" onChange={(next) => changes.push(next)} />);
    await settle();
    const input = host.querySelector('input') as HTMLInputElement;
    input.value = '新';
    input.dispatchEvent(new Event('input', { bubbles: true }));
    await settle();
    expect(changes.at(-1)).toEqual({ note: '新' });
    input.value = '';
    input.dispatchEvent(new Event('input', { bubbles: true }));
    await settle();
    expect(changes.at(-1)).toBeUndefined();
  });

  it('取自引用（{ ref }）的参数只显示来源，在 JSON 里改', async () => {
    const host = mount(() => <ParamsForm params={[param('owner', 'text')]} value={{ owner: { ref: 'form.buyer' } }} mergeKey="n" onChange={() => {}} />);
    await settle();
    expect(host.textContent).toContain('取自 form.buyer');
    expect(host.querySelector('input')).toBeNull();
  });
});

describe('异常处理', () => {
  it('发件箱的投递失败叫"放弃投递"，提示也不同', () => {
    const delivery = { kind: 'deliver' } as Incident;
    expect(isDelivery(delivery)).toBe(true);
    expect(isDelivery({ kind: 'action' } as Incident)).toBe(false);
    expect(incidentDoneText('discard', zhCN)).toBe('已放弃投递');
    expect(incidentDoneText('skip', zhCN)).toBe('已跳过，流程往下走了');
    expect(incidentDoneText('retry', zhCN)).toBe(zhCN.incidentRetried);
  });
});
