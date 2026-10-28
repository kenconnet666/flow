/**
 * 路由：演示在 /app 下（发起、待办、办理……管理端），文档在 /docs 下（按 docs/pages 收集），首页是文档的第一篇。
 * 导航按前缀判断当前页（vueRouterAdapter 的 match: 'prefix'）：/app 下的都算"演示"、/docs 下的都算"文档"，
 * 所以演示的各页不互为前缀（发起列表是 /app/start、流程监控是 /app/admin/monitor）。
 */
import { createRouter, createWebHistory } from 'vue-router';
import { allPages } from './docs/site/nav';
import { CalendarsPage } from './pages/CalendarsPage';
import { CcPage } from './pages/CcPage';
import { DesignationsPage } from './pages/DesignationsPage';
import { DonePage } from './pages/DonePage';
import { FormEditPage } from './pages/FormEditPage';
import { FormsPage } from './pages/FormsPage';
import { InboxPage } from './pages/InboxPage';
import { IncidentsPage } from './pages/IncidentsPage';
import { InstancePage } from './pages/InstancePage';
import { MonitorPage } from './pages/MonitorPage';
import { ProcessEditPage } from './pages/ProcessEditPage';
import { ProcessesPage } from './pages/ProcessesPage';
import { ProgressPage } from './pages/ProgressPage';
import { StartListPage } from './pages/StartListPage';
import { StartPage } from './pages/StartPage';
import { StartedPage } from './pages/StartedPage';
import { StatsPage } from './pages/StatsPage';
import { WorkItemPage } from './pages/WorkItemPage';

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: allPages[0]!.path },
    { path: '/docs', redirect: allPages[0]!.path },
    ...allPages.map((page) => ({ path: page.path, component: page.component, meta: { title: page.title, docs: true } })),
    { path: '/app', redirect: '/app/start' },
    { path: '/app/start', component: StartListPage },
    { path: '/app/start/:key', component: StartPage },
    { path: '/app/inbox', component: InboxPage },
    { path: '/app/work-items/:id', component: WorkItemPage },
    { path: '/app/done', component: DonePage },
    { path: '/app/designations', component: DesignationsPage },
    { path: '/app/started', component: StartedPage },
    { path: '/app/cc', component: CcPage },
    { path: '/app/admin', redirect: '/app/admin/monitor' },
    { path: '/app/admin/monitor', component: MonitorPage },
    { path: '/app/admin/incidents', component: IncidentsPage },
    { path: '/app/admin/calendars', component: CalendarsPage },
    { path: '/app/admin/stats', component: StatsPage },
    { path: '/app/admin/processes', component: ProcessesPage },
    { path: '/app/admin/processes/:key', component: ProcessEditPage },
    { path: '/app/admin/forms', component: FormsPage },
    { path: '/app/admin/forms/:key', component: FormEditPage },
    { path: '/app/instances/:id', component: InstancePage },
    { path: '/app/progress/:id', component: ProgressPage },
    // 打错的地址回到首页，不留一页空白
    { path: '/:unknown(.*)*', redirect: '/' },
  ],
  // 换页回到顶部；带锚点时滚到那一节
  scrollBehavior: (to, _from, saved) => saved ?? (to.hash ? { el: decodeURIComponent(to.hash), top: 72 } : { top: 0 }),
});

/** 当前在不在文档里 */
export const inDocs = (path: string) => path === '/docs' || path.startsWith('/docs/');
