/**
 * 演示旁边的文档面板：开没开、显示哪一篇、演示与文档怎么分宽度，记在浏览器里（刷新后照旧）。
 * 文档页里"边看边做"调 followAlong：打开这一篇在面板里，演示跳到这一篇开始操作的那一页。
 */
import { createContext } from '@zerodep-dev/vue-tsx-pro-tsx';
import { reactive, watch } from 'vue';
import type { Router } from 'vue-router';
// 只用 nav 的类型：文档页经 DocPage 引用这里，而 nav 在收集文档页，取值会成循环
import type { NavPage } from './nav';

const KEY = 'flow-demo:docs-panel';

/** 面板的状态 */
export interface PanelState {
  open: boolean;
  /** 显示的那一篇（/docs/...）；空串或找不到时面板显示第一篇 */
  path: string;
  /** 宽屏时演示一栏占的百分比（Splitter 的 size），其余是文档 */
  size: number;
}

/** 演示一栏默认占的百分比 */
const DEMO_SIZE = 62;

function read(): PanelState {
  const fallback = { open: false, path: '', size: DEMO_SIZE };
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) ?? 'null') as { open?: unknown; path?: unknown; size?: unknown } | null;
    if (!saved || typeof saved.path !== 'string') return fallback;
    return { open: saved.open === true, path: saved.path, size: typeof saved.size === 'number' ? saved.size : DEMO_SIZE };
  } catch {
    return fallback;
  }
}

/** 面板现在的状态 */
export const docsPanel = reactive<PanelState>(read());

watch(docsPanel, (state) => {
  try {
    localStorage.setItem(KEY, JSON.stringify(state));
  } catch {
    // 隐私模式下存不了：只是刷新后面板回到默认
  }
});

/** 照着这一篇在演示里操作：面板显示它，演示跳到它开始的那一页 */
export function followAlong(router: Router, page: Pick<NavPage, 'path' | 'try'>) {
  docsPanel.path = page.path;
  docsPanel.open = true;
  void router.push(page.try ?? '/app');
}

/** 文档页是不是显示在面板里（面板里不再显示"边看边做"，链接改成在面板里换篇） */
export const InDocsPanel = createContext<boolean>('DocsPanel');
