// 表单的标签页：每个分组一页，分组的标题是标签；没切到的页也渲染着（字段照样取值、校验）；设计器里各页摊开
import { FormSection } from '@zerodep-dev/vue-tsx-pro-ui';
import { describe, expect, it } from 'vitest';
import { FormTabs } from '../src/form/FormTabs';
import { mount, settle } from './mount';

const pages = () => [
  <FormSection key="a" title="基本信息"><input name="title" /></FormSection>,
  <FormSection key="b" title="证明材料"><input name="proof" /></FormSection>,
];

describe('FormTabs', () => {
  it('分组的标题是标签；切到第二页看第二个分组；没切到的页也在（藏着）', async () => {
    const host = mount(() => <FormTabs aria-label="请假单">{pages()}</FormTabs>);
    await settle();
    const tabs = [...host.querySelectorAll('[role="tab"]')];
    expect(tabs.map((t) => t.textContent)).toEqual(['基本信息', '证明材料']);
    const panels = [...host.querySelectorAll<HTMLElement>('[role="tabpanel"]')];
    expect(panels.map((p) => p.hidden)).toEqual([false, true]);
    expect(host.querySelector('input[name="proof"]')).not.toBeNull();
    (tabs[1] as HTMLElement).click();
    await settle();
    expect(panels.map((p) => p.hidden)).toEqual([true, false]);
  });

  it('设计器的画布上（带 data-form-node）各页摊开排', async () => {
    const host = mount(() => <FormTabs data-form-node="0.1">{pages()}</FormTabs>);
    await settle();
    expect(host.querySelectorAll('[role="tab"]').length).toBe(0);
    expect(host.textContent).toContain('基本信息');
    expect(host.textContent).toContain('证明材料');
  });
});
