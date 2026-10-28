import { createUi } from '@zerodep-dev/vue-tsx-pro-ui';
import { createShikiHighlighter } from '@zerodep-dev/vue-tsx-pro-ui/shiki';
import { vueRouterAdapter } from '@zerodep-dev/vue-tsx-pro-ui/vue-router';
import { createApp } from 'vue';
import { App } from './App';
import { createDocsRouter } from './router';
import { preference } from './site/theme';

const router = createDocsRouter();

createApp(App)
  .use(router)
  .use(
    createUi({
      theme: () => preference.theme,
      paintDocument: true,
      // 代码块按需加载语言：文档里出现的只有这几种
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
          powershell: () => import('@shikijs/langs/powershell'),
          sql: () => import('@shikijs/langs/sql'),
        },
      }),
      // 组件库与本仓库各装了一份同版本的 vue-router 声明（运行时由 Vite 的 dedupe 合成一份），类型上要转一下
      router: vueRouterAdapter(router as unknown as Parameters<typeof vueRouterAdapter>[0]),
    }),
  )
  .mount('#app');
