/** 后端接入 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Code, CodeBlock, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '后端接入',
  description: '把引擎接进一个 Spring Boot 3 项目：依赖、配置、要写的三样（用户类型、组织架构、当前操作人）、事务、多租户、监控。',
  order: 2,
};

const install = `# flow 自己的包还没发布到 Maven 中央库，先装进本机仓库（JDK 17 起）
cd flow
./mvnw -q install -DskipTests -pl java/flow-model,java/flow-core,java/flow-spring,java/flow-test-kit -am`;

const pom = `<dependency>
  <groupId>org.dpdns.zerodep</groupId>
  <artifactId>flow-spring</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
<dependency>                      <!-- typed JSON：接口响应里的 Long、BigDecimal、日期（Maven 中央库） -->
  <groupId>org.dpdns.zerodep</groupId>
  <artifactId>json-wire</artifactId>
  <version>0.1.0</version>
</dependency>
<dependency>                      <!-- 引擎经 easy-query 读写（Maven 中央库）；已有 easy-query 的项目沿用自己的 -->
  <groupId>org.dpdns.zerodep.easyquery</groupId>
  <artifactId>sql-mysql</artifactId>
  <version>3.3.1-zd.1</version>
</dependency>
<dependency><groupId>org.flywaydb</groupId><artifactId>flyway-mysql</artifactId></dependency>
<dependency><groupId>com.mysql</groupId><artifactId>mysql-connector-j</artifactId></dependency>
<dependency>                      <!-- 契约核对，只在测试里用 -->
  <groupId>org.dpdns.zerodep</groupId>
  <artifactId>flow-test-kit</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <scope>test</scope>
</dependency>`;

const yml = `spring:
  datasource:
    url: \${DB_URL}              # 密码这类不写进文件，用环境变量
    username: \${DB_USER}
    password: \${DB_PASSWORD}

easy-query:
  enable: true
  database: mysql

flow:
  flyway:
    enabled: true               # 启动时建引擎的表
  http:
    enabled: true               # 打开引擎自带的接口：/flow/commands/*、/flow/queries/*
    path: /flow
  jobs:
    interval: 10s               # 作业轮询的间隔
  calendar:
    zone: Asia/Shanghai         # "工作日"按哪个时区的日期算`;

const user = `@Data
public class AppUser implements Actor {
    private final Long id;
    private final String name;
    private final Set<Long> departments;
    private final Set<Long> roles;
}`;

const org = `@Component
public class AppOrgDirectory implements OrgDirectory {
    private final UserMapper users;   // 你自己的用户、部门、角色表

    public AppOrgDirectory(UserMapper users) { this.users = users; }

    @Override
    public String userName(Long userId) { return users.nameOf(userId); }

    /** 角色、部门展开成人：会签、抄送写成角色或部门时用 */
    @Override
    public List<Long> members(String kind, Long id) {
        return "role".equals(kind) ? users.idsWithRole(id) : users.idsInDepartment(id);
    }

    /** 流程管理员：处理人解析为空时转给他们，也总能看实例 */
    @Override
    public List<Long> administrators() { return users.idsWithRole(ADMIN_ROLE); }
}`;

const actor = `@Component
public class AppActors implements ActorProvider<AppUser> {
    @Override
    public AppUser current() {
        // 从你的登录信息取：Spring Security 的 principal、会话、令牌……
        return (AppUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}`;

const tx = `@Transactional(isolation = Isolation.READ_COMMITTED)
public void submitOrder(Order order, AppUser user) {
    orders.insert(order);                                   // 你的业务写入
    engine.start(user, new StartCommand("purchase",          // 和命令同成同败
        FormValues.of(Map.of("title", order.getTitle(), "amount", order.getAmount())),
        null, order.getNo(), null, 0, null));
}`;

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="deps" title="依赖">
      <CodeBlock code={install} lang="bash" />
      <CodeBlock title="pom.xml" code={pom} lang="xml" />
      <Paragraph>
        <Code>flow-spring</Code> 带了 easy-query 的 Spring Boot starter，由它建 <Code>EasyEntityQuery</Code> Bean，数据源、事务跟着你的 Spring 配置走。完整的例子是仓库里的 <Code>examples/spring-boot</Code>。
      </Paragraph>
    </Section>
    <Section id="config" title="配置">
      <CodeBlock title="application.yml" code={yml} lang="yaml" />
      <Tbl
        head={['属性', '默认', '说明']}
        rows={[
          [<Code>flow.datasource</Code>, '—', '建表用哪个 DataSource Bean；只有一个时不写'],
          [<Code>flow.flyway.enabled</Code>, 'false', '建表。用别的迁移工具时关掉，把 flow-core 里 db/flow/mysql/V*.sql 抄进去按序执行'],
          [<><Code>flow.http.enabled</Code>、<Code>.path</Code></>, 'false、/flow', '自带的接口（见 接口与 Java API）'],
          [<><Code>flow.jobs.enabled</Code>、<Code>.interval</Code></>, 'true、10s', '作业轮询；交给已有调度平台时关掉，定时调 engine.runDueJobs()'],
          [<Code>flow.calendar.zone</Code>, 'UTC', '工作日历的时区'],
          [<Code>flow.idempotency.retention</Code>, '24h', '带 Idempotency-Key 的命令记录留多久'],
          [<><Code>flow.archive.enabled</Code>、<Code>.after</Code></>, 'false、180d', '归档结束很久的实例'],
          [<Code>flow.forms.server-code</Code>, 'true', '能不能在线写表单的后端代码（见 表单后端代码）'],
          [<Code>flow.designer.node-meta</Code>, '—', '节点的业务信息字段（见 监听、通知与业务表）'],
        ]}
      />
    </Section>
    <Section id="three" title="要写的三样">
      <Paragraph>用户类型：实现 <Code>Actor</Code>。候选人按它的部门、角色匹配，换了角色马上生效；还有别的归属（项目组）放进 <Code>getGroups()</Code>。</Paragraph>
      <CodeBlock code={user} lang="java" />
      <Paragraph>组织架构：实现 <Code>OrgDirectory</Code>，声明成 Bean。引擎没有自己的组织架构，详见 组织与权限。</Paragraph>
      <CodeBlock code={org} lang="java" />
      <Paragraph>
        当前操作人：用 Spring Security 且 principal 实现了 <Code>Actor</Code> 时什么都不用写；否则实现 <Code>ActorProvider</Code>。示例项目从请求头 <Code>X-User</Code> 取只为演示，不能用在生产。
      </Paragraph>
      <CodeBlock code={actor} lang="java" />
    </Section>
    <Section id="tx" title="事务">
      <Paragraph>
        命令要在<strong>读已提交</strong>的事务里跑。引擎自己开的事务会设好；在你自己的 <Code>@Transactional</Code> 方法里调命令时要写隔离级别，或者把连接池、数据库的默认隔离级别设成读已提交，否则命令直接报错（MySQL 默认的可重复读下几个人同时会签会算错，所以宁可报错）。
      </Paragraph>
      <CodeBlock code={tx} lang="java" />
      <Paragraph>这样调用时命令和你的业务写入同成同败；提交后的监听与通知等你的事务提交了才发。</Paragraph>
    </Section>
    <Section id="tenant" title="多租户（可选）">
      <Paragraph>
        不用时什么都不用做（租户列都是 0）。要用时声明一个 <Code>FlowTenant</Code> Bean（<Code>{'() -> 当前租户编号'}</Code>，一般从登录信息取）。每张表都带租户列，查询与写入由拦截器加条件；作业替各租户做时自动切到作业的租户。
      </Paragraph>
    </Section>
    <Section id="metrics" title="监控">
      <Tbl
        head={['指标（Micrometer）', '看什么']}
        rows={[
          [<><Code>flow.jobs.due</Code>、<Code>flow.jobs.due.oldest</Code></>, '到期没做的作业、最早的晚了多少秒；一直涨说明作业轮询停了或做不过来'],
          [<Code>flow.jobs.failed</Code>, '重试用完失败的作业，要人在异常处理里重试或跳过；建议大于 0 就告警'],
          [<Code>flow.instances.active</Code>, '进行中的实例'],
          [<><Code>flow.jobs.run</Code>、<Code>flow.archive.instances</Code></>, '这个进程做了几个作业、归档了几个实例'],
        ]}
      />
      <Alert tone="info">没有 Micrometer 时可以定时调 engine.ops() 自己报。</Alert>
    </Section>
  </DocPage>
));
