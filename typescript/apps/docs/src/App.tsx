/**
 * 文档站的外壳（同组件库的文档站）：AppShell 的顶栏（站点名、搜索、主题切换）、左侧导航、右侧本页目录，
 * 正文是当前路由的页面，末尾是上一篇 / 下一篇。
 */
import { Workflow } from '@lucide/icons';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import * as css from '@zerodep-dev/vue-tsx-pro-tsx/styles';
import {
  CommandPalette,
  Container,
  Icon,
  NavItem,
  NavList,
  NavSection,
  Pager,
  TableOfContents,
  collectHeadings,
  type HeadingEntry,
} from '@zerodep-dev/vue-tsx-pro-ui';
import { AppShell, ThemeSwitcher } from '@zerodep-dev/vue-tsx-pro-ui/patterns';
import { watch } from 'vue';
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router';
import { allPages, sectionLabel, sections } from './site/nav';
import { preference } from './site/theme';

const styles = css.create({
  brand: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '_sm',
    color: '_text',
    fontFamily: '_heading',
    fontSize: '_lg',
    fontWeight: '_semibold',
    textDecoration: 'none',
  },
  brandMark: { color: '_accentText' },
  tools: { display: 'flex', alignItems: 'center', gap: '_md', marginInlineStart: 'auto' },
  page: { display: 'flex', flexDirection: 'column', gap: '_xl' },
  // 标题离顶栏远一点，页底留出空白
  main: { paddingBlockStart: '2.5rem', paddingBlockEnd: '4rem' },
});

/** 搜索面板：每页一项，按侧栏分组 */
const searchItems = allPages.map((page) => ({ value: page.path, label: page.title, description: page.description, group: sectionLabel(page) }));

export const App = _component(() => {
  const route = useRoute();
  const router = useRouter();
  let content = _state.raw<HTMLElement | null>(null);
  let headings = _state.raw<HeadingEntry[]>([]);

  // 页面是懒加载的：观察正文的增删，下一帧重新收集 h2、h3 作本页目录；结果没变不更新
  _effect(() => {
    const root = content;
    if (!root) return;
    let frame = 0;
    const collect = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        const next = collectHeadings(root, 'h2[id], h3[id]');
        if (JSON.stringify(next) !== JSON.stringify(headings)) headings = next;
      });
    };
    collect();
    const observer = new MutationObserver(collect);
    observer.observe(root, { childList: true, subtree: true });
    return () => {
      observer.disconnect();
      cancelAnimationFrame(frame);
    };
  });
  watch(
    () => route.meta.title as string | undefined,
    (title) => (document.title = title ? `${title} · 流程引擎` : '流程引擎'),
    { immediate: true },
  );

  const neighbors = () => {
    const index = allPages.findIndex((page) => page.path === route.path);
    if (index < 0) return {};
    const link = (page: (typeof allPages)[number] | undefined) => page && { href: page.path, label: page.title };
    return { previous: link(allPages[index - 1]), next: link(allPages[index + 1]) };
  };

  return () => (
    <AppShell
      brand={
        <RouterLink to="/" {...css.attrs(styles.brand)}>
          <Icon icon={Workflow} sx={styles.brandMark} />
          流程引擎
        </RouterLink>
      }
      header={
        <div {...css.attrs(styles.tools)}>
          <CommandPalette items={searchItems} onAction={(item) => void router.push(item.value)} />
          <ThemeSwitcher preference={preference} />
        </div>
      }
      sidebar={
        <NavList aria-label="文档">
          {sections.map((section) => (
            <NavSection key={section.label} label={section.label}>
              {section.pages.map((page) => (
                <NavItem key={page.path} href={page.path}>
                  {page.title}
                </NavItem>
              ))}
            </NavSection>
          ))}
        </NavList>
      }
      aside={headings.length > 0 && <TableOfContents items={headings} />}
      propsMain={{ sx: styles.main }}
    >
      <div ref={_toRef(content)} {...css.attrs(styles.page)}>
        <div key={route.path}>
          <RouterView />
        </div>
        <Container size="md" padding="none">
          <Pager {...neighbors()} />
        </Container>
      </div>
    </AppShell>
  );
});
