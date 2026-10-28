/**
 * 站点的外壳：顶栏是"文档 / 演示"两大块、搜索文档、边看文档、当前用户、重置演示、主题切换。
 * - 文档（/docs）：左侧是文档的分组导航，右侧是本页目录，正文末尾是上一篇 / 下一篇。
 * - 演示（/app）：左侧是"发起""我的待办"……管理端各页（可以收起成图标栏）。宽屏时正文是左右分栏（Splitter）：左边演示、
 *   右边文档面板，"边看文档"或分隔条上的按钮展开、收起，分隔条拖动调整比例，两栏各自滚动；窄屏时文档面板是抽屉。
 *   换人、重置演示之后各页重新挂载、重新请求。
 */
import {
  Activity,
  BookOpen,
  CalendarDays,
  ChartColumn,
  CircleAlert,
  CircleCheck,
  FileText,
  FormInput,
  Inbox,
  Mail,
  MonitorPlay,
  PanelRight,
  Send,
  Settings2,
  UserRoundCheck,
  Workflow,
} from '@lucide/icons';
import { provideFlowFiles, provideFormCompiler } from '@zerodep-dev/flow-ui/form';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { createCompileClient } from '@zerodep-dev/vue-tsx-pro-tsx/browser-compile';
import CompileWorker from '@zerodep-dev/vue-tsx-pro-tsx/browser-compile/worker?worker';
import * as css from '@zerodep-dev/vue-tsx-pro-tsx/styles';
import {
  Button,
  CommandPalette,
  Container,
  Drawer,
  Splitter,
  NavBar,
  NavItem,
  NavList,
  NavSection,
  Pager,
  Select,
  TableOfContents,
  Toaster,
  collectHeadings,
  useHttp,
  useMediaQuery,
  type HeadingEntry,
} from '@zerodep-dev/vue-tsx-pro-ui';
import { AppShell, AppShellBrand, ThemeSwitcher } from '@zerodep-dev/vue-tsx-pro-ui/patterns';
import { watch } from 'vue';
import { RouterView, useRoute, useRouter } from 'vue-router';
import { DocsPanel } from './docs/DocsPanel';
import { allPages, sectionLabel, sections } from './docs/site/nav';
import { docsPanel } from './docs/site/panel';
import { demoFiles } from './files';
import { inDocs } from './router';
import { currentUser, toaster, users } from './session';
import { ResetDemo, resets } from './shell/ResetDemo';
import { preference } from './theme';

const styles = css.create({
  tools: { display: 'flex', alignItems: 'center', gap: '_sm', marginInlineStart: 'auto' },
  docsMain: { paddingBlockStart: '2.5rem', paddingBlockEnd: '4rem' },
  docsPage: { display: 'flex', flexDirection: 'column', gap: '_xl' },
  // 演示与文档分栏：占满顶栏以下，两栏各自滚动（正文不留上下内边距，留给两栏自己）
  splitMain: { paddingBlock: 0, paddingInlineEnd: 0 },
  split: { height: 'calc(100dvh - 3rem)' },
  workPane: { paddingBlock: '_xl', paddingInlineEnd: '_xl' },
  docsPane: { paddingInline: '_lg', paddingBlockEnd: '_xl' },
});

/** 搜索面板：文档每页一项，按分组 */
const searchItems = allPages.map((page) => ({ value: page.path, label: page.title, description: page.description, group: sectionLabel(page) }));

/** 演示的左侧导航 */
const demoNav = [
  { href: '/app/start', icon: Send, label: '发起' },
  { href: '/app/inbox', icon: Inbox, label: '我的待办' },
  { href: '/app/designations', icon: UserRoundCheck, label: '待我指定' },
  { href: '/app/done', icon: CircleCheck, label: '我已办' },
  { href: '/app/started', icon: FileText, label: '我发起的' },
  { href: '/app/cc', icon: Mail, label: '抄送我的' },
  { href: '/app/admin/monitor', icon: Activity, label: '流程监控' },
  { href: '/app/admin/incidents', icon: CircleAlert, label: '异常处理' },
  { href: '/app/admin/processes', icon: Settings2, label: '流程管理' },
  { href: '/app/admin/forms', icon: FormInput, label: '表单管理' },
  { href: '/app/admin/calendars', icon: CalendarDays, label: '工作日历' },
  { href: '/app/admin/stats', icon: ChartColumn, label: '统计' },
];

export const App = _component(() => {
  // 附件存在示例后端（files.ts）：表单里的附件字段经它上传、打开
  provideFlowFiles(demoFiles(useHttp()));
  // 代码表单在 Worker 里编译：表单设计器每改一下都要重编，放在页面线程会卡
  provideFormCompiler(createCompileClient(() => new CompileWorker()));

  const route = useRoute();
  const router = useRouter();
  const wide = useMediaQuery('(min-width: 80rem)');
  const docs = () => inDocs(route.path);
  let content = _state.raw<HTMLElement | null>(null);
  let headings = _state.raw<HeadingEntry[]>([]);

  // 文档页是懒加载的：观察正文的增删，下一帧重新收集 h2、h3 作本页目录；结果没变不更新
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

  const header = () => (
    <div {...css.attrs(styles.tools)}>
      <CommandPalette items={searchItems} onAction={(item) => void router.push(item.value)} />
      {!docs() && (
        <Button variant={docsPanel.open ? 'soft' : 'ghost'} icon={PanelRight} aria-pressed={docsPanel.open} onClick={() => (docsPanel.open = !docsPanel.open)}>
          边看文档
        </Button>
      )}
      <Select
        aria-label="当前用户"
        items={users.map((user) => ({ value: String(user.id), label: user.name, description: user.description }))}
        _value={String(currentUser.value)}
        onUpdate:_value={(id: string | null) => id && (currentUser.value = Number(id))}
      />
      <ResetDemo />
      <ThemeSwitcher preference={preference} />
    </div>
  );

  const docsSidebar = () => (
    // 两套导航是两个不同的列表：给不同的 key，换块时整个换掉
    <NavList key="docs" aria-label="文档">
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
  );

  const demoSidebar = () => (
    <NavList key="demo" aria-label="演示">
      {demoNav.map((item) => (
        <NavItem key={item.href} href={item.href} icon={item.icon}>
          {item.label}
        </NavItem>
      ))}
    </NavList>
  );

  const docsContent = () => (
    <div ref={_toRef(content)} {...css.attrs(styles.docsPage)}>
      <div key={route.path}>
        <RouterView />
      </div>
      <Container size="md" padding="none">
        <Pager {...neighbors()} />
      </Container>
    </div>
  );

  // 换人、重置之后各页重新挂载、重新请求
  const page = () => <RouterView key={`${currentUser.value}:${resets.value}`} />;

  const demoContent = () =>
    wide.value ? (
      <Splitter
        label="调整演示与文档的宽度"
        _size={docsPanel.size}
        minSize={35}
        maxSize={80}
        collapsible="end"
        _collapsed={!docsPanel.open}
        onUpdate:_collapsed={(collapsed: boolean) => (docsPanel.open = !collapsed)}
        start={page()}
        end={<DocsPanel />}
        sx={styles.split}
        propsStart={{ sx: styles.workPane }}
        propsEnd={{ sx: styles.docsPane, 'aria-label': '文档' }}
      />
    ) : (
      <>
        {page()}
        <Drawer _open={docsPanel.open} onUpdate:_open={(open: boolean) => (docsPanel.open = open)} side="end" title="文档">
          <DocsPanel />
        </Drawer>
      </>
    );

  return () => (
    <AppShell
      brand={
        <AppShellBrand href="/docs" icon={Workflow}>
          流程引擎
        </AppShellBrand>
      }
      nav={
        <NavBar
          aria-label="站点"
          items={[
            { label: '文档', href: '/docs', icon: BookOpen },
            { label: '演示', href: '/app', icon: MonitorPlay },
          ]}
        />
      }
      header={header()}
      sidebar={docs() ? docsSidebar() : demoSidebar()}
      sidebarCollapsible={!docs()}
      aside={docs() && headings.length > 0 && <TableOfContents items={headings} />}
      propsMain={{ sx: docs() ? styles.docsMain : wide.value ? styles.splitMain : undefined }}
    >
      {docs() ? docsContent() : demoContent()}
      <Toaster controller={toaster} />
    </AppShell>
  );
});
