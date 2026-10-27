# 接入指南：把流程引擎接进一个已有的项目

面向要在自己的 Spring Boot + Vue 3 项目里用流程引擎的人。照着一步步做，做完能在自己的页面里发起、审批、看流程，
在管理端画流程、做表单。完整的例子是本仓库的 `examples/spring-boot`（后端）与 `typescript/apps/demo`（前端）；
细节的取舍在 `docs/decisions`（下文写成"0007 第 6 节"这样）。

> 目前只支持 MySQL 8。几个包（`flow-*`、`json-wire`、`vue-tsx-pro`）还没发布到 Maven 中央库与 npm，先按下面的"本机安装"引用。

## 0. 一张图

```
你的前端（Vite + Vue 3）                          你的后端（Spring Boot 3）
  createUi({ http, lang, router })                 flow-spring（自动配置）
  页面里放 flow-ui 的组件  ── /flow/queries/* ──▶    FlowEngine ── easy-query ── MySQL（flow_* 表）
  （StartForm、FlowInbox、WorkItemDetail…）  /flow/commands/*   你实现：Actor、OrgDirectory、ActorProvider
                                                    可选：PermissionPolicy、ViewPolicy、扩展点
```

前后端之间是 typed JSON（json-wire）：Long、BigDecimal、日期写成文字，根上的 `$types` 记下位置，前端的请求层还原成 bigint、Decimal、日期对象。

## 1. 后端

### 1.1 依赖（本机安装）

在本机把几个仓库装进本地 Maven 仓库（JDK 17 起）：

```bash
cd json-wire && ./mvnw -q install -DskipTests
cd ../flow && ./mvnw -q install -DskipTests -pl java/flow-model,java/flow-core,java/flow-spring -am
```

项目的 `pom.xml`：

```xml
<dependency>
  <groupId>org.dpdns.zerodep</groupId>
  <artifactId>flow-spring</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
<dependency>                      <!-- typed JSON：接口响应里的 Long、BigDecimal、日期 -->
  <groupId>org.dpdns.zerodep</groupId>
  <artifactId>json-wire</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
<dependency>                      <!-- 引擎经 easy-query 读写；已有 easy-query 的项目沿用自己的 -->
  <groupId>com.easy-query</groupId>
  <artifactId>sql-mysql</artifactId>
  <version>3.2.17</version>
</dependency>
<dependency><groupId>org.flywaydb</groupId><artifactId>flyway-mysql</artifactId></dependency>
<dependency><groupId>com.mysql</groupId><artifactId>mysql-connector-j</artifactId></dependency>
```

`flow-spring` 已经带了 easy-query 的 Spring Boot starter（`sql-springboot-starter`，排除了它自带的旧版 Spring Boot），由它建 `EasyEntityQuery` Bean，引擎就用这一个；数据源、事务跟着你的 Spring 配置走。

### 1.2 配置

```yaml
spring:
  datasource:
    url: ${DB_URL}              # 密码这类不写进文件，用环境变量
    username: ${DB_USER}
    password: ${DB_PASSWORD}

easy-query:
  enable: true
  database: mysql

flow:
  flyway:
    enabled: true               # 启动时建引擎的表（单独的历史表 flow_schema_history，不碰你自己的迁移）
  http:
    enabled: true               # 打开引擎自带的接口：/flow/commands/*、/flow/queries/*、管理与设计的接口
    path: /flow
  jobs:
    interval: 10s               # 定时节点、超时、提醒、异步动作多久看一次；集群里每个实例都可以开（抢占，不会重复做）
  calendar:
    zone: Asia/Shanghai         # "工作日"按哪个时区的日期算
  idempotency:
    retention: 24h              # 带 Idempotency-Key 的命令记录留多久
  archive:
    enabled: false              # 量大时再开：结束超过 after 的实例搬进 *_archive（0007 第 6 节）
    after: 180d
```

| 属性 | 默认 | 说明 |
| --- | --- | --- |
| `flow.datasource` | — | 建表用哪个 DataSource Bean；只有一个时不写 |
| `flow.flyway.enabled` / `.locations` / `.table` | `false` / `classpath:db/flow/mysql` / `flow_schema_history` | 建表。自己用别的迁移工具时关掉，把 `flow-core` 里 `db/flow/mysql/V*.sql` 抄进去按序执行 |
| `flow.http.enabled` / `.path` | `false` / `/flow` | 自带的接口 |
| `flow.jobs.enabled` / `.interval` | `true` / `10s` | 作业轮询；交给已有调度平台时关掉，定时调 `engine.runDueJobs()` |
| `flow.calendar.zone` | `UTC` | 工作日历的时区 |
| `flow.idempotency.retention` | `24h` | 幂等记录保留多久 |
| `flow.archive.enabled` / `.after` | `false` / `180d` | 归档 |

### 1.3 要写的三样

**用户类型**：实现 `Actor`（编号、名字、部门、角色；还有别的归属如"项目组"放进 `getGroups()`）。候选人按它的部门、角色匹配，换了角色马上生效。

```java
@Data
public class AppUser implements Actor {
    private final Long id;
    private final String name;
    private final Set<Long> departments;
    private final Set<Long> roles;
}
```

**组织架构**：实现 `OrgDirectory`，声明成 Bean。引擎没有默认的组织架构。

| 方法 | 必须 | 用在 |
| --- | --- | --- |
| `userName(id)` | 是 | 处理人、抄送人的名字快照 |
| `members(kind, id)` | 用到会签、抄送写角色或部门时 | 把角色、部门展开成人 |
| `administrators()` | 建议 | 处理人解析为空时转给谁；流程管理员也总能看实例 |
| `inboxManager(kind, id)` | 可选 | 收件箱久无人认领时由谁指定承办人 |

**当前操作人**：用 Spring Security 且 principal 实现了 `Actor` 时什么都不用写（自动配置从 `SecurityContext` 取）；
否则实现 `ActorProvider<AppUser>` 声明成 Bean，从你的登录信息取。示例里从请求头 `X-User` 取**只为演示**。

### 1.4 权限

接口的登录与页面的保护由你自己的框架负责（0004 第 6 节的宽松原则）。引擎另有两个可替换的 Bean：

- `PermissionPolicy<U>`：每个命令、查询之前问"能不能做"，默认全部放行。操作名的完整清单在它的 Javadoc 里
  （`start`、`complete`、`instances.search`、`definitions.publish`、`terminate`……），目标是流程标识或编号。
  通常至少把设计（`definitions.*`、`forms.*`、`drafts.*`、`calendars.*`）与管理员操作（`terminate`、`jump`、`reassign`、`migrate`、`changeData`、
  `retryJob`、`skipJob`）以及 `instances.search`、`incidents`、`stats` 收紧到管理员。
- `ViewPolicy<U>`：谁能打开一个实例（详情、待办详情、表单的 actions、评论）。默认参与过的人（发起人、流程管理员、处理过或在处理、候选、
  被抄送、流转记录里有）总能看，别人要有 `instances.search` 的权限——所以收紧了 `instances.search`，没参与过的人自然打不开。

引擎自己的规则（只有候选人能认领、只有发起人能撤回、只有处理人能处理）不经过这两个，一直生效。

### 1.5 事务

**命令要在读已提交的事务里跑**（0007 第 5 节）。引擎自己开的事务会设好；在你自己的 `@Transactional` 方法里调引擎的命令时要写
`@Transactional(isolation = Isolation.READ_COMMITTED)`，或者把连接池、数据库的默认隔离级别设成读已提交，否则命令直接报错
（MySQL 默认的可重复读下几个人同时会签会算错，所以宁可报错）。在你的事务里调命令时，命令与你的业务写入同成同败；
提交后的监听与通知等你的事务提交了才发。

### 1.6 typed JSON

接口响应要写成 typed JSON，前端才能把 Long、金额、日期还原。把示例里的
`examples/spring-boot/.../config/wire/WireResponseAdvice.java` 抄进你的项目，`basePackages` 写上
`org.dpdns.zerodep.flow.spring` 和你自己的控制器包。请求体不用处理（前端把 bigint 写成文字，Jackson 照常读成 Long）。

### 1.7 多租户（可选）

不用多租户时什么都不用做（租户列都是 0）。要用时声明一个 `FlowTenant` Bean（`() -> 当前租户编号`，一般从登录信息取）；
不用 Spring 时 `FlowEngine.builder(easyQuery).tenant(() -> 当前租户编号)`。每张表都带租户列、查询与写入由拦截器加条件；
作业替各租户做时自动切到作业的租户。

### 1.8 监控

项目里有 Micrometer（一般随 Spring Boot Actuator 来）时自动有这些指标；没有时可以定时调 `engine.ops()` 自己报。

| 指标 | 看什么 |
| --- | --- |
| `flow.jobs.due`、`flow.jobs.due.oldest` | 到期没做的作业、最早的晚了多少秒。正常时接近 0；一直涨说明作业轮询停了或做不过来 |
| `flow.jobs.failed` | 重试用完失败的作业：要人在管理端的异常处理里重试或跳过，建议大于 0 就告警 |
| `flow.instances.active` | 进行中的实例 |
| `flow.jobs.run`、`flow.archive.instances` | 这个进程做了几个作业、归档了几个实例 |

### 1.9 扩展点

按名字注册 Bean 就生效（0006 第 6 节）：

| 接口 | 做什么 |
| --- | --- |
| `PeopleResolver` | 自定义处理人来源（"发起人的上级"、"项目经理"） |
| `OutcomeGuard` | 处理前的业务校验，不通过整个处理不做 |
| `ServiceAction` | 自动节点、节点事件上的动作（调 ERP、发 MQ） |
| `CustomNode` | 自定义节点类型（`x-名字`） |
| `CountersignRule` | 自定义会签完成规则 |
| `FieldType` | 表单的自定义字段类型的后端校验 |
| `TodoQueryCustomizer` | 待办连业务表筛选、带出业务列 |
| `CommandInterceptor` | 命令前后（审计、改参数），与命令同一个事务 |
| `FlowListener` | 监听流转记录：事务内可拒绝，提交后发消息 |
| `NotificationChannel` | 通知怎么发（站内信、邮件、企业微信） |
| `WorkCalendar` | 自己的工作日历（默认读管理端维护的 `flow_calendar`） |

## 2. 前端

### 2.1 依赖

flow-ui 以 TSX 源码发布，要用 Vite 和 vue-tsx-pro 的编译插件。先在本机装好 flow 的前端工作区（`cd flow/typescript && pnpm install`），
你的项目里用 `link:` 引用：

```jsonc
{
  "dependencies": {
    "@zerodep-dev/flow-ui": "link:../flow/typescript/packages/ui",
    "@zerodep-dev/flow-model": "link:../flow/typescript/packages/model",
    "@zerodep-dev/vue-tsx-pro-ui": "link:../vue-tsx-pro/packages/ui",
    "@zerodep-dev/vue-tsx-pro-tsx": "link:../vue-tsx-pro/packages/tsx",
    "@internationalized/date": "^3", "decimal.js": "^10", "vue": "^3.5", "vue-router": "^4", "zod": "^4"
  }
}
```

`vite.config.ts`：

```ts
import { defineConfig } from 'vite';
import { browserCompile, vueTsxPro, workspaceSource } from '@zerodep-dev/vue-tsx-pro-tsx/vite';

export default defineConfig({
  plugins: [workspaceSource(), ...vueTsxPro(), browserCompile()],   // browserCompile：表单设计器在浏览器里编译表单代码
  resolve: { dedupe: ['vue', 'vue-router', '@internationalized/date', 'decimal.js', 'zod'] },
  optimizeDeps: { include: ['@zerodep-dev/json-wire'] },
  server: { proxy: { '/flow': 'http://localhost:8080' } },
});
```

### 2.2 启动时

```ts
import Decimal from 'decimal.js';
import { withFlowLang } from '@zerodep-dev/flow-ui';
import { createHttpClient, createUi, zhCN } from '@zerodep-dev/vue-tsx-pro-ui';
import { vueRouterAdapter } from '@zerodep-dev/vue-tsx-pro-ui/vue-router';

const http = createHttpClient({
  wire: { decimal: Decimal, dates: 'objects' },              // typed JSON 还原成 Decimal、日期对象
  onRequest: (request) => request.headers.set('Authorization', `Bearer ${token()}`),
});

createApp(App)
  .use(router)
  .use(createUi({ lang: withFlowLang(zhCN), http, router: vueRouterAdapter(router) }))
  .mount('#app');
```

flow-ui 的组件经 `useHttp()` 拿这个请求客户端、前缀 `/flow`；后端的 `flow.http.path` 改了时用
`provideFlowClient(createFlowClient(http, { prefix: '/api/flow' }))` 在根组件里给一个。

### 2.3 页面放什么

每个页面就是一个组件加上"点了去哪"。`people` 是人员选择器的数据来源（`PeopleSource`：`children`、`search`、`roles` 三个方法，
接你的组织架构接口；规模小时用 `localPeopleSource(整棵树)`）。

| 页面 | 组件 | 要接的 |
| --- | --- | --- |
| 发起（列表） | `useFlowClient().startable()` 列出能发起的流程；`FlowStartDrafts` 列出暂存的 | 点了去发起页 |
| 发起 | `StartForm processKey draftId people` | `onStarted` 之后去待办 |
| 我的待办 | `FlowInbox` | `onOpen` 去办理页 |
| 待我指定 | `FlowInbox list="designations"` | 同上 |
| 我已办 | `FlowInbox list="done"` | 同上 |
| 办理 | `WorkItemDetail id people` | `onDone(result)`：`result.closed` 非空时待办结束了，回列表 |
| 我发起的 | `FlowStartedList` | `onOpen` 去详情 |
| 抄送我的 | `FlowCcList` | 同上 |
| 流程详情 | `FlowInstanceDetail id people admin` | 管理端打开时 `admin` |
| 外部进度 | `FlowProgress instanceId` | 给客户、供应商看的进度（不经 ViewPolicy，内容有限） |
| 管理：监控、异常、统计、日历 | `FlowInstanceMonitor`、`FlowIncidentList`、`FlowStatistics`、`FlowCalendars`（从 `@zerodep-dev/flow-ui/admin`） | |
| 管理：流程、表单 | `FlowProcessList` + `FlowDefinitionEditor`；`FlowFormList` + `FlowFormEditor` | 设计中自动存草稿，别人同时改会提示 |

我已办、我发起的、抄送我的带筛选（流程、日期区间、只看已归档）与列排序，后端对排序列有白名单。

### 2.4 附件

附件字段（`type: 'file'`）的值只记 `{ id, name, size }`，文件本身存在你那里。在根组件里给一次怎样上传、怎样打开：

```ts
import { provideFlowFiles } from '@zerodep-dev/flow-ui/form';

provideFlowFiles({
  upload: async (file, { signal }) => {
    const body = new FormData();
    body.append('file', file);
    return api.post('/files', body, { signal });        // 返回 { id, name, size }
  },
  href: (file) => `/api/files/${file.id}`,                // 打开、下载的地址
});
```

没给时附件字段只显示文件名。示例的写法见 `apps/demo/src/files.ts` 与后端的 `ExampleFiles`（只为演示：存在临时目录）；
真实的下载接口要按能不能看这个实例把关（`engine.instance(actor, id)` 不抛错就是能看）。

### 2.5 表单设计器的编译

代码表单（与设计器的画布）要在浏览器里编译。给一个编译 Worker，改表单时界面不卡；不给时在页面线程编，也能用：

```ts
import { provideFormCompiler } from '@zerodep-dev/flow-ui/form';
import { createCompileClient } from '@zerodep-dev/vue-tsx-pro-tsx/browser-compile';
import CompileWorker from '@zerodep-dev/vue-tsx-pro-tsx/browser-compile/worker?worker';

provideFormCompiler(createCompileClient(() => new CompileWorker()));   // 根组件的 setup 里
```

### 2.6 业务表单

表单在管理端的表单设计器里画（字段、明细表、附件、布局、节点上的字段权限、前后端代码），发布后流程节点引用它；不用写前端代码。
代码表单里明细表写 `<form.List name="lines" />`，标签页写 `<FormTabs>` 包几个 `<FormSection title="…">`，附件写 `<form.Field name="proof"><FileField /></form.Field>`。
要连业务表时用表单的后端代码（`FormHandler`）或 `TodoQueryCustomizer`（0004）。

## 3. 上线前核对

- [ ] 数据库是 MySQL 8，账号有建表权限（或者表由你的迁移工具建好）；`transaction_isolation` 是 READ-COMMITTED，或者只靠引擎自己开事务
- [ ] `OrgDirectory` 的 `administrators()`、`members()` 接好了（会签、抄送按角色或部门时要）
- [ ] 当前操作人从登录信息取，不是示例里的请求头
- [ ] `PermissionPolicy` 把设计、管理员操作、`instances.search` 收紧了（`ViewPolicy` 跟着生效）
- [ ] 接口响应是 typed JSON（看一个带金额的接口，响应里有 `$types`）
- [ ] 集群部署时每个实例都可以开作业轮询；不想开就关掉并接调度平台
- [ ] 量大时打开归档（`flow.archive.enabled`）
- [ ] 监控上看 `flow.jobs.failed` 与 `flow.jobs.due.oldest`（1.8）

## 4. 常见问题

| 现象 | 原因与处理 |
| --- | --- |
| 命令报 `flow commands need a READ COMMITTED transaction` | 在你的可重复读事务里调了命令，见 1.5 |
| 打开实例报 `flow.forbidden`、`reason: not-visible` | ViewPolicy 不让看：不是参与者，也没有 `instances.search` 的权限 |
| 列表排序报 `flow.validation-failed`（明细 `sort: unknown-column`） | 排序列不在白名单里（每个列表一份，在 `InstanceQueries`、`InboxQueries`） |
| 金额在前端是字符串、日期不对 | 响应没走 typed JSON（1.6），或前端没给 `wire` 选项（2.2） |
| 运行中换了 jar 后报 `NoClassDefFoundError` | 开发时 `mvn install` 覆盖了正在跑的应用的 jar，重启应用 |
