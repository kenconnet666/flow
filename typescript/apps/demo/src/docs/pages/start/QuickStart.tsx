/** 快速体验：把示例后端、示例前端跑起来，切换几个人走一遍 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Code, CodeBlock, Link, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Ops } from '../../site/Ops';
import { Shot } from '../../site/Shot';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '快速体验',
  description: '在本机把示例跑起来：一个 Spring Boot 示例后端、一个示例前端，带好了几个流程和几个人，十分钟内能走完一单。',
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
pnpm -C apps/demo seed     # 发布示例流程与表单：采购申请、请假、扩展演示
pnpm -C apps/demo dev      # 示例前端 http://localhost:5180，/flow 转到 8080`;

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="need" title="需要什么">
      <Tbl
        head={['', '版本']}
        rows={[
          ['JDK', '17 或更新'],
          ['Node.js 与 pnpm', 'Node 24、pnpm 11'],
          ['MySQL', '8.x（示例用 Docker 起一个）'],
        ]}
      />
    </Section>
    <Section id="run" title="跑起来">
      <CodeBlock title="1. 数据库" code={db} lang="bash" />
      <CodeBlock title="2. 示例后端" code={backend} lang="bash" />
      <CodeBlock title="3. 示例前端" code={front} lang="bash" />
      <Alert tone="info">
        示例的"登录"很简单：右上角选当前是谁，每个请求带上 <Code>X-User</Code>。真实系统里由你的登录信息决定，见 <Link href="/docs/dev/backend">后端接入</Link>。
      </Alert>
    </Section>
    <Section id="try" title="走一单">
      <Shot src="use/01-start-list.png" caption="发起页：能发起的流程按分类列出，上面是自己存的草稿" />
      <Ops
        items={[
          '右上角选"林小满"，在 发起 里点 采购申请，填标题、金额 16000，点 提交',
          '切到"周启航"，在 我的待办 里打开这一单（它在财务的收件箱里，谁先认领谁办），点 通过',
          '金额达到一万，下一步到了总经理：切到"总经理"，打开它点 同意',
          '切回"林小满"，在 我发起的 里看到它"已通过"，点开看流程图和流转记录',
        ]}
      />
      <Paragraph>
        接着可以照 <Link href="/docs/build/expense">教程 1</Link> 自己画一个报销流程，或者看 <Link href="/docs/use/handle">待办与办理</Link> 了解每个按钮。
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
  </DocPage>
));
