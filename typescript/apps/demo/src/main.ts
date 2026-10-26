/**
 * 流程引擎的示例前端：发起、我的待办、办理、我已办、我发起的、抄送我的、流程详情，接 Spring Boot 示例后端（vite.config.ts 把 /flow 转过去）。
 * 组件库的配置里给好请求客户端（带上当前用户）、路由适配、带 flow 错误码文案的语言；flow-ui 的组件取 useHttp() 拼出引擎客户端。
 */
import { withFlowLang } from '@zerodep-dev/flow-ui';
import { createHttpClient, createUi, zhCN } from '@zerodep-dev/vue-tsx-pro-ui';
import { vueRouterAdapter } from '@zerodep-dev/vue-tsx-pro-ui/vue-router';
import { createApp } from 'vue';
import { createRouter, createWebHistory } from 'vue-router';
import { App } from './App';
import { CcPage } from './pages/CcPage';
import { DonePage } from './pages/DonePage';
import { InboxPage } from './pages/InboxPage';
import { InstancePage } from './pages/InstancePage';
import { StartListPage } from './pages/StartListPage';
import { StartedPage } from './pages/StartedPage';
import { StartPage } from './pages/StartPage';
import { WorkItemPage } from './pages/WorkItemPage';
import { currentUser } from './session';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: StartListPage },
    { path: '/start/:key', component: StartPage },
    { path: '/inbox', component: InboxPage },
    { path: '/work-items/:id', component: WorkItemPage },
    { path: '/done', component: DonePage },
    { path: '/started', component: StartedPage },
    { path: '/cc', component: CcPage },
    { path: '/instances/:id', component: InstancePage },
  ],
});

// 示例的"登录"：每个请求带上当前用户（真实应用由登录信息决定，见 session.ts）
const http = createHttpClient({ onRequest: (request) => request.headers.set('X-User', String(currentUser.value)) });

createApp(App)
  .use(router)
  .use(
    createUi({
      lang: withFlowLang(zhCN),
      http,
      // 组件库与本仓库各装了一份同版本的 vue-router 声明（运行时由 Vite 的 dedupe 合成一份），类型上要转一下
      router: vueRouterAdapter(router as unknown as Parameters<typeof vueRouterAdapter>[0]),
      paintDocument: true,
    }),
  )
  .mount('#app');
