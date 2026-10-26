// 测试的挂载工具：装上组件库的 createUi（语言加上 flow 的错误码文案），渲染到 document 上的容器里，每个测试结束后卸载
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { createUi, zhCN } from '@zerodep-dev/vue-tsx-pro-ui';
import { afterEach } from 'vitest';
import { createApp, type App, type VNodeChild } from 'vue';
import { withFlowLang } from '../src/lang/messages';

const mounted: { app: App; host: HTMLElement }[] = [];

afterEach(() => {
  for (const { app, host } of mounted.splice(0)) {
    app.unmount();
    host.remove();
  }
});

/** 渲染一段内容，返回容器 */
export function mount(render: () => VNodeChild): HTMLElement {
  const host = document.createElement('div');
  document.body.append(host);
  const app = createApp(_component(() => render)).use(createUi({ lang: withFlowLang(zhCN) }));
  app.mount(host);
  mounted.push({ app, host });
  return host;
}

/** 等 Vue 与微任务都走完（表单加载、校验是异步的） */
export const settle = () => new Promise((resolve) => setTimeout(resolve, 0));
