/** 前端接入 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Code, CodeBlock, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '前端接入',
  description: '在 Vite + Vue 3 项目里用 flow-ui：依赖与 Vite 配置、启动时的 createUi、每个页面放哪个组件、附件、表单编译。',
  order: 3,
};

const deps = `{
  "dependencies": {
    "@zerodep-dev/flow-ui": "link:../flow/typescript/packages/ui",
    "@zerodep-dev/flow-model": "link:../flow/typescript/packages/model",
    "@zerodep-dev/vue-tsx-pro-ui": "link:../vue-tsx-pro/packages/ui",
    "@zerodep-dev/vue-tsx-pro-tsx": "link:../vue-tsx-pro/packages/tsx",
    "@zerodep-dev/json-wire": "^0.1.0",
    "@internationalized/date": "^3", "decimal.js": "^10", "vue": "^3.5", "vue-router": "^4", "zod": "^4"
  }
}`;

const vite = `import { defineConfig } from 'vite';
import { browserCompile, vueTsxPro, workspaceSource } from '@zerodep-dev/vue-tsx-pro-tsx/vite';

export default defineConfig({
  plugins: [workspaceSource(), ...vueTsxPro(), browserCompile()],   // browserCompile：表单在浏览器里编译
  resolve: { dedupe: ['vue', 'vue-router', '@internationalized/date', 'decimal.js', 'zod'] },
  optimizeDeps: { include: ['@zerodep-dev/json-wire'] },
  server: { proxy: { '/flow': 'http://localhost:8080' } },
});`;

const main = `import Decimal from 'decimal.js';
import { withFlowLang } from '@zerodep-dev/flow-ui';
import { createHttpClient, createUi, zhCN } from '@zerodep-dev/vue-tsx-pro-ui';
import { vueRouterAdapter } from '@zerodep-dev/vue-tsx-pro-ui/vue-router';

const http = createHttpClient({
  wire: { decimal: Decimal, dates: 'objects' },                 // typed JSON 还原成 Decimal、日期对象
  onRequest: (request) => request.headers.set('Authorization', \`Bearer \${token()}\`),
});

createApp(App)
  .use(router)
  .use(createUi({ lang: withFlowLang(zhCN), http, router: vueRouterAdapter(router) }))
  .mount('#app');`;

const page = `import { WorkItemDetail } from '@zerodep-dev/flow-ui';

export const WorkItemPage = _component(() => {
  const route = useRoute();
  const router = useRouter();
  return () => (
    <WorkItemDetail
      id={BigInt(String(route.params.id))}
      people={people}
      onDone={(result) => {
        if (result.closed.length > 0) void router.push('/inbox');   // 这个待办结束了才回列表
      }}
    />
  );
});`;

const files = `import { provideFlowFiles } from '@zerodep-dev/flow-ui/form';

provideFlowFiles({
  upload: async (file, { signal }) => {
    const body = new FormData();
    body.append('file', file);
    return api.post('/files', body, { signal });        // 返回 { id, name, size }
  },
  href: (file) => \`/api/files/\${file.id}\`,              // 打开、下载的地址
});`;

const compiler = `import { provideFormCompiler } from '@zerodep-dev/flow-ui/form';
import { createCompileClient } from '@zerodep-dev/vue-tsx-pro-tsx/browser-compile';
import CompileWorker from '@zerodep-dev/vue-tsx-pro-tsx/browser-compile/worker?worker';

provideFormCompiler(createCompileClient(() => new CompileWorker()));   // 根组件的 setup 里`;

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="deps" title="依赖">
      <Paragraph>
        flow-ui 以 TSX 源码发布，要用 Vite 和 vue-tsx-pro 的编译插件。flow 的前端包还没发布到 npm，先在本机装好 flow 的前端工作区（<Code>cd flow/typescript && pnpm install</Code>），你的项目用 <Code>link:</Code> 引用。
      </Paragraph>
      <CodeBlock title="package.json" code={deps} lang="json" />
      <CodeBlock title="vite.config.ts" code={vite} lang="ts" />
    </Section>
    <Section id="boot" title="启动时">
      <CodeBlock title="main.ts" code={main} lang="ts" />
      <Paragraph>
        flow-ui 的组件经 <Code>useHttp()</Code> 拿这个请求客户端、前缀 <Code>/flow</Code>。后端的 <Code>flow.http.path</Code> 改了时，在根组件里 <Code>{"provideFlowClient(createFlowClient(http, { prefix: '/api/flow' }))"}</Code>。<Code>withFlowLang</Code> 把流程的文案与错误码文案加进组件库的语言包。
      </Paragraph>
    </Section>
    <Section id="pages" title="页面放什么">
      <Paragraph>
        每个页面就是一个组件加上"点了去哪"。<Code>people</Code> 是人员选择器的数据来源（<Code>PeopleSource</Code>：<Code>children</Code>、<Code>search</Code>、<Code>roles</Code>，接你的组织架构接口；规模小时用 <Code>localPeopleSource(整棵树)</Code>）。
      </Paragraph>
      <Tbl
        head={['页面', '组件', '要接的']}
        rows={[
          ['发起（列表）', <><Code>useFlowClient().startable()</Code>；<Code>FlowStartDrafts</Code> 列出草稿</>, '点了去发起页'],
          ['发起', <Code>StartForm processKey draftId people</Code>, 'onStarted 之后去待办'],
          ['我的待办 / 待我指定 / 我已办', <Code>{'FlowInbox list="todo" | "designations" | "done"'}</Code>, 'onOpen 去办理页'],
          ['办理', <Code>WorkItemDetail id people</Code>, 'onDone(result)：result.closed 非空时待办结束了，回列表'],
          ['我发起的', <Code>FlowStartedList</Code>, 'onOpen 去详情'],
          ['抄送我的', <Code>FlowCcList</Code>, '同上'],
          ['流程详情', <Code>FlowInstanceDetail id people admin</Code>, '管理端打开时传 admin'],
          ['外部进度', <Code>FlowProgress instanceId</Code>, '给客户、供应商看的进度（内容有限，不经 ViewPolicy）'],
          ['管理：监控、异常、统计、日历', <Code>FlowInstanceMonitor、FlowIncidentList、FlowStatistics、FlowCalendars</Code>, '从 @zerodep-dev/flow-ui/admin 导入'],
          ['管理：流程、表单', <Code>FlowProcessList + FlowDefinitionEditor；FlowFormList + FlowFormEditor</Code>, '编辑时自动存草稿；发布时核对打开后有没有人发布过，不会互相覆盖'],
        ]}
      />
      <CodeBlock title="办理页" code={page} lang="tsx" />
      <Paragraph>我已办、我发起的、抄送我的带筛选（流程、日期区间、只看已归档）与列排序。</Paragraph>
    </Section>
    <Section id="files" title="附件">
      <Paragraph>附件字段的值只记 <Code>{'{ id, name, size }'}</Code>，文件本身存在你那里。在根组件里给一次怎样上传、怎样打开：</Paragraph>
      <CodeBlock code={files} lang="ts" />
      <Alert tone="warning">
        下载接口要按"能不能看这个实例"把关：<Code>engine.instance(actor, id)</Code> 不抛错就是能看。没给 provideFlowFiles 时附件字段只显示文件名。
      </Alert>
    </Section>
    <Section id="compile" title="表单编译">
      <Paragraph>表单是 TSX 源码，在浏览器里编译。给一个编译 Worker，改表单时界面不卡；不给时在页面线程编，也能用。</Paragraph>
      <CodeBlock code={compiler} lang="ts" />
    </Section>
  </DocPage>
));
