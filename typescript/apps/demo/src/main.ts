/**
 * 流程引擎的示例站点：演示（/app：发起、我的待办、办理、我已办、我发起的、抄送我的、流程详情、外部参与方看的进度、管理端）
 * 和文档（/docs：使用、搭建教程、管理、开发）在同一个站点里，演示旁边可以开着文档边看边做。接示例后端（Spring Boot，vite.config.ts 把 /flow、/demo 转过去）。
 * 组件库的配置里给好请求客户端（带上当前用户）、路由适配、带 flow 错误码文案的语言、主题与代码高亮；flow-ui 的组件取 useHttp() 拼出引擎客户端。
 */
import Decimal from 'decimal.js';
import { withFlowLang } from '@zerodep-dev/flow-ui';
import { createHttpClient, createUi, zhCN } from '@zerodep-dev/vue-tsx-pro-ui';
import { createShikiHighlighter } from '@zerodep-dev/vue-tsx-pro-ui/shiki';
import { vueRouterAdapter } from '@zerodep-dev/vue-tsx-pro-ui/vue-router';
import { createApp } from 'vue';
import { App } from './App';
import { router } from './router';
import { currentUser } from './session';
import { preference } from './theme';

// 示例的"登录"：每个请求带上当前用户（真实应用由登录信息决定，见 session.ts）。
// 线上的 typed JSON 还原成强类型：小数是 Decimal、日期与时刻是 @internationalized/date 的对象（表单的值、模型包的规则都按它们）
const http = createHttpClient({
  wire: { decimal: Decimal, dates: 'objects' },
  onRequest: (request) => request.headers.set('X-User', String(currentUser.value)),
});

createApp(App)
  .use(router)
  .use(
    createUi({
      lang: withFlowLang(zhCN),
      http,
      theme: () => preference.theme,
      paintDocument: true,
      // 文档里的代码块按需加载语言：出现的只有这几种
      highlighter: createShikiHighlighter({
        languages: {
          tsx: () => import('@shikijs/langs/tsx'),
          ts: () => import('@shikijs/langs/typescript'),
          java: () => import('@shikijs/langs/java'),
          json: () => import('@shikijs/langs/json'),
          jsonc: () => import('@shikijs/langs/jsonc'),
          yaml: () => import('@shikijs/langs/yaml'),
          xml: () => import('@shikijs/langs/xml'),
          bash: () => import('@shikijs/langs/shellscript'),
          sql: () => import('@shikijs/langs/sql'),
        },
      }),
      // 组件库与本仓库各装了一份同版本的 vue-router 声明（运行时由 Vite 的 dedupe 合成一份），类型上要转一下
      router: vueRouterAdapter(router as unknown as Parameters<typeof vueRouterAdapter>[0], { match: 'prefix' }),
    }),
  )
  .mount('#app');
