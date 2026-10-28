/**
 * 演示旁边的文档面板：顶上选看哪一篇（按分组）、上一篇 / 下一篇、整页打开、关掉，下面是那一篇的正文。
 * 面板里的文档页知道自己在面板里（InDocsPanel）：不显示"边看边做"；点文档里的链接在面板里换篇，不离开演示。
 */
import { ChevronLeft, ChevronRight, Maximize2, X } from '@lucide/icons';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import * as css from '@zerodep-dev/vue-tsx-pro-tsx/styles';
import { Flex, IconButton, Select } from '@zerodep-dev/vue-tsx-pro-ui';
import { defineAsyncComponent, h, watch, type Component } from 'vue';
import { useRouter } from 'vue-router';
import { inDocs } from '../router';
import { allPages, sectionLabel } from './site/nav';
import { InDocsPanel, docsPanel } from './site/panel';

const styles = css.create({
  panel: { display: 'flex', flexDirection: 'column', gap: '_md', minWidth: 0 },
  bar: { position: 'sticky', top: 0, zIndex: 1, paddingBlock: '_xs', backgroundColor: '_surface' },
  picker: { flex: 1, minWidth: 0 },
});

/** 每一篇的组件只建一次：换篇再换回来不重新加载 */
const components = new Map<string, Component>(allPages.map((page) => [page.path, defineAsyncComponent(page.component)]));

const items = allPages.map((page) => ({ value: page.path, label: page.title, group: sectionLabel(page) }));

export const DocsPanel = _component(() => {
  InDocsPanel.provide(true);
  const router = useRouter();

  /** 显示的那一篇（记下的找不到时是第一篇） */
  const current = () => (components.has(docsPanel.path) ? docsPanel.path : allPages[0]!.path);
  const index = () => allPages.findIndex((page) => page.path === current());
  const go = (step: number) => {
    const next = allPages[index() + step];
    if (next) docsPanel.path = next.path;
  };

  // 链接的点击在捕获阶段截下（Link 自己的跳转在冒泡阶段）：指向文档的在面板里换篇
  let root = _state.raw<HTMLElement | null>(null);
  _effect(() => {
    const element = root;
    if (!element) return;
    const keepInPanel = (event: MouseEvent) => {
      const href = (event.target as HTMLElement).closest('a')?.getAttribute('href')?.split('#')[0];
      if (!href || !inDocs(href)) return;
      event.preventDefault();
      event.stopPropagation();
      docsPanel.path = href;
    };
    element.addEventListener('click', keepInPanel, true);
    return () => element.removeEventListener('click', keepInPanel, true);
  });
  // 换了一篇：回到开头（面板在 Splitter 的一栏或抽屉里，滚的是外面那一层）
  watch(current, () => root?.scrollIntoView({ block: 'start' }));

  return () => {
    const Page = components.get(current());
    return (
      <div ref={_toRef(root)} {...css.attrs(styles.panel)}>
        <Flex gap="xs" align="center" sx={styles.bar}>
          <Select
            aria-label="文档"
            items={items}
            searchable
            _value={current()}
            onUpdate:_value={(path: string | null) => path && (docsPanel.path = path)}
            sx={styles.picker}
          />
          <IconButton icon={ChevronLeft} aria-label="上一篇" disabled={index() <= 0} onClick={() => go(-1)} />
          <IconButton icon={ChevronRight} aria-label="下一篇" disabled={index() >= allPages.length - 1} onClick={() => go(1)} />
          <IconButton icon={Maximize2} aria-label="整页打开" onClick={() => void router.push(current())} />
          <IconButton icon={X} aria-label="收起文档" onClick={() => (docsPanel.open = false)} />
        </Flex>
        <div key={current()}>{Page && h(Page)}</div>
      </div>
    );
  };
});
