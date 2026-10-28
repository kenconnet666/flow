/** 快速体验：这个站点怎么用（文档、演示、边看文档、换人、重置），走一单；在本机跑起来 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Code, CodeBlock, Link, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Ops } from '../../site/Ops';
import { Shot } from '../../site/Shot';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '快速体验',
  description: '这个站点就是一套能用的演示：顶栏切到"演示"就能发起、审批、画流程；打开"边看文档"，照着教程一步步做。',
  order: 2,
  try: '/app/start',
};

const db = `# 仓库根目录：起开发用的 MySQL 8.4（Docker），密码写在 infra/mysql/.env（不进仓库）
echo FLOW_DB_PASSWORD=换成你的密码 > infra/mysql/.env
docker compose -f infra/mysql/compose.yaml up -d`;

const backend = `# 示例后端：连 flow_demo 库，第一次启动由 Flyway 建表，端口 8080
FLOW_MYSQL_DATABASE=flow_demo . infra/mysql/live-env.sh && ./mvnw -pl examples/spring-boot -am spring-boot:run`;

const front = `cd typescript
pnpm install
pnpm -C apps/demo seed tutorials   # 重置演示：教程做完的那一档（不带参数是从头开始）
pnpm -C apps/demo dev              # http://localhost:5180，/flow、/demo 转到 8080`;

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="site" title="这个站点">
      <Tbl
        head={['顶栏', '做什么']}
        rows={[
          ['文档 / 演示', '在读文档和用演示之间切换'],
          ['搜索文档', '按标题找一篇（Ctrl + K）'],
          ['边看文档', '演示右边打开文档面板：换篇、拖动分隔条调整宽度、收起；窄屏时是抽屉'],
          ['当前用户', '示例的"登录"：选现在是谁。审批要切到处理人，看结果切回发起人'],
          ['重置演示', '清空所有数据，重新发布示例：从头开始（只有示例流程），或教程做完（六个教程的流程都在，还有几单在办、一条异常）'],
        ]}
      />
      <Paragraph>教程开头的"在演示里边看边做"直接打开那一篇在面板里，并跳到开始操作的那一页。</Paragraph>
      <Alert tone="info">演示是大家共用的：别人可能刚好也在用，数据乱了就点 重置演示。</Alert>
    </Section>
    <Section id="try" title="走一单">
      <Shot src="use/01-start-list.png" caption="发起页：能发起的流程按分类列出，上面是自己存的草稿" />
      <Ops
        items={[
          '当前用户选 林小满，在 发起 里点 采购申请，填标题、金额 16000，点 提交',
          '切到 周启航，在 我的待办 里打开这一单（它在财务的收件箱里，谁先认领谁办），点 通过',
          '金额达到一万，下一步到了总经理：切到 总经理，打开它点 同意',
          '切回 林小满，在 我发起的 里看到它"已通过"，点开看流程图和流转记录',
        ]}
      />
      <Paragraph>
        接着照 <Link href="/docs/build/expense">教程 1</Link> 自己画一个报销流程（先重置成"从头开始"），或者看 <Link href="/docs/use/handle">待办与办理</Link> 了解每个按钮。
      </Paragraph>
    </Section>
    <Section id="people" title="示例里的人">
      <Tbl
        head={['人', '身份']}
        rows={[
          ['林小满', '门店员工，教程里的发起人'],
          ['周启航、沈若水', '财务部，角色"财务"（财务的收件箱里谁先认领谁办）'],
          ['总经理', '总经办，角色"总经理"；示例里也是流程管理员'],
          ['许知远', '总经办，教程里当法务、技术评审'],
        ]}
      />
    </Section>
    <Section id="local" title="在本机跑起来">
      <Tbl
        head={['', '版本']}
        rows={[
          ['JDK', '17 或更新'],
          ['Node.js 与 pnpm', 'Node 24、pnpm 11'],
          ['MySQL', '8.x（示例用 Docker 起一个）'],
        ]}
      />
      <CodeBlock title="1. 数据库" code={db} lang="bash" />
      <CodeBlock title="2. 示例后端" code={backend} lang="bash" />
      <CodeBlock title="3. 示例站点" code={front} lang="bash" />
      <Paragraph>
        示例的"登录"只是每个请求带上 <Code>X-User</Code>；真实系统里由你的登录信息决定，见 <Link href="/docs/dev/backend">后端接入</Link>。部署成公开的演示站点时，可以定时跑 <Code>pnpm seed tutorials</Code> 把数据恢复原样；不想让人重置时在示例后端设 <Code>demo.reset.enabled: false</Code>。
      </Paragraph>
    </Section>
  </DocPage>
));
