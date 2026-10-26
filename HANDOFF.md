# 交接文档

给接手这个仓库的人（或换了电脑的自己）：读完这一页就能接着做。方向与已定的事在 `docs/decisions/`，这里只讲现状、
三个仓库的关系、做事方式和换电脑要准备的东西。

## 1. 现状（2026-10-26）

- **代码**（2026-10-26 起按 0004 "已定"的顺序做；第 ② 步模型包已完成，第 ③ 步（③a–③d）已完成，下一步见第 6 节）：
  - 目录按 0001 的"模块划分"：根 `pom.xml` 聚合 `java/flow-model`；`typescript/` 是 pnpm 工作区（`packages/model`、`test`）；`spec/` 放两边共用的样例。
  - 模型包已有：条件求值（`spec/conditions`）、定义的类型与校验（`spec/definitions`，0005 第 7 节的全部问题代号）、选边与连线副作用（`spec/routing`）、
    字段清单自身的校验（`spec/manifests`）、提交值的类型校验（`spec/values`）、两个版本的差别（`spec/diff`）、
    字段权限的合算与提交的合并（`spec/access`）。Java 在 `org.dpdns.zerodep.flow.model.*`，TS 是 `@zerodep-dev/flow-model`。
  - 本地跑：`./mvnw -q test`（Java）；`cd typescript && pnpm install && pnpm test`（TS）。新加一份共用样例只加 JSON 文件，两边的测试都会跑到。
  - 模型包的内容已齐（2026-10-26），含业务错误码（0008，`spec/error-codes.json`）。
  - Java 引擎 `java/flow-core`（2026-10-26，③a）：包按职责分——
    `FlowEngine`（门面与组装）、`identity`（操作人、组织、权限）、`command`（每个命令一个 record）、`data`（表单值、流程变量、运行状态）、
    `engine`（每个命令一个处理类，步骤写在类注释里；推进在 `Execution`）、`store`（六个存储接口）、
    `store/query`（用 query 的默认实现：列上的转换器把行直接映射成引擎的记录，租户与修改时间由 `FlowDatabase` 统一填）。主键是数据库自增的 `Long`。
    建表不用 query 的迁移器：`flow-core` 的 `resources/db/flow/mysql/V1__create_flow_tables.sql`，推荐 Flyway 单独一套执行（0007 第 9 节）。
  - 场景用例 `spec/scenarios`（③b）：两个后端共同的验收标准，格式见那里的 README；Java 的执行器在 `flow-it` 的 `scenario` 包。
  - Spring Boot 适配 `java/flow-spring`（③c）：自动配置（`flow.*` 配置项见 `FlowProperties`）、当前操作人默认取 Spring Security 的 principal、
    `flow.flyway.enabled` 启动时建表、`flow.http.enabled` 打开接口（清单在 0006 的 7.3）。按 Spring Boot 3.5 编译，只用 3.5 与 4.x 共有的写法。
    示例项目 `examples/spring-boot`（在构建里，CI 会编译它）。
  - 实库测试 `java/flow-it`：本机连 query 的开发 MySQL（WSL）里的 `flow_it` 库，变量 `FLOW_MYSQL_URL` / `_USER` / `_PASSWORD`
    （用户是 query，密码在 query 的 `infra/mysql/.env`）；没设时跳过。每个测试前用 Flyway 清库再建表。
  - 现在依赖 query 主干的 2.1.0-SNAPSHOT（要 wire 的 `Json.write`）：本机先在 query 里 `mvnw install`；CI 先检出 query 装好再测。
  - CI（`.github/workflows/ci.yml`）：Java（含 MySQL 8.4 服务跑实库测试）与 TS 两个任务。用户要把仓库改成开源，之后 CI 才能免费跑起来。
- **决定文档**：
  - [0001 引擎的方向](docs/decisions/0001-engine-direction.md)：已定（2026-10-26）。“已定”一节用户确认过（第一版只做 MySQL 8 / PolarDB MySQL 8.0、
    主键是数据库自增的 BIGINT（Java 的 long，2026-10-26 改定，见 0006 的 6.1）、时间按 UTC、流程变量放实例上的 JSON 列、Java 一个 jar 接 Spring、TS 一个包接 Nuxt、前端固定 vue-tsx-pro）；
    “初步方向”已定，表由 0007 接替。
  - [0002 能力清单](docs/decisions/0002-capabilities.md)：已定（2026-10-26），每节注明落在哪份文档。
  - [0003 模型包与前端的分工](docs/decisions/0003-model-and-frontend.md)：已采纳，前端取 B（见下）。
  - [0007 表](docs/decisions/0007-tables.md)：已定（2026-10-26）。待办不拆运行与历史、冷数据进归档表；候选只存打开着的待办，角色、部门按查询时的成员匹配；运行状态放实例 JSON；租户列都有；名字存快照；流程与表单都是"本身 + 版本"。
  - [0006 引擎对外的接口](docs/decisions/0006-engine-api.md)：已定（2026-10-26）。命令管道、操作人泛型 U、命令与查询清单、事件、扩展点与可替换的部分、两个后端的形态与相同的 HTTP 接口、错误代号；主键改为数据库自增（6.1）。
  - [0008 业务错误码](docs/decisions/0008-error-codes.md)：已定（2026-10-26）。一律业务错误码、HTTP 一律 200，三个仓库都守；码是带命名空间的文字（`flow.`、`query.`、`http.`），成功是 `ok`，各包提供自己的码并互相对应。
  - [0005 流程定义 JSON](docs/decisions/0005-definition.md)：已定（2026-10-26 逐节定稿）。图加结构校验、布局单独放、`{ ref }` 取 form. / sys. / 流程变量 / fn. 四类；处理人六种来源加"上一步选"；11 种节点；会签"一人拒绝即拒绝"；条件与组件库、query 同规则；问题代号清单；通知按事件可改。
  - [0004 表单](docs/decisions/0004-forms.md)：已定（2026-10-26 逐节定稿）。代码表单（TSX，运行时编译）加拖拽设计器，两边读写同一份源码；
    字段清单是 JSON5 字面量；后端代码在线编辑、存库、启动时加载（Spring Boot 用 Java、Nuxt 用 TS）；节点上四档字段权限；一个流程一张表单，表单的值单独存；
    不做隔离，安全交给接入方的框架；写进业务表用"按业务表生成代码"。剩两项待实测（fat jar 的 classpath、组件根元素接住透传属性）。
- **思路记录**（不采用，只留档）：[Spring 里嵌 GraalJS、引擎核心只写一份 TS](docs/notes/graaljs-in-spring.md)。用户 2026-10-26 定路线不变：Java（Spring Boot）与 Nuxt 两个后端。
- **依赖的两个库由用户手动发布**（2026-10-26 查 npm）：
  - vue-tsx-pro（组件库）已发 npm：`@zerodep-dev/vue-tsx-pro-tsx`、`@zerodep-dev/vue-tsx-pro-ui` 0.1.0（原名 `@vue-tsx-pro/*`），仓库里已推进到 0.2.0-dev；
  - query（ORM）已发 2.0.0：npm `@zerodep-dev/query-*`、Maven 中央仓库 `org.dpdns.zerodep:*`；query 主干已推进到 2.1.0-SNAPSHOT。
  - 本仓库按 2.0.0 依赖（根 `pom.xml` 的 `query.version`）；要先改 query 时，在 query 里 `mvnw install` 出 2.1.0-SNAPSHOT，再把 `query.version` 换过去。

## 2. 三个仓库的关系

| 仓库 | 地址 | 本仓库用它做什么 |
| --- | --- | --- |
| flow（本仓库，私有） | `kenconnet666/flow` | 引擎（Java + TS 两个后端）、模型包、流程组件（前端）、管理端页面 |
| query（公开） | `kenconnet666/query` | 持久化。为引擎补好的能力见 query 的 `.claude/HANDOFF.md`“为流程引擎做的准备”：Spring 多数据源的事务管理器、迁移历史表改名 `historyTable("flow_migration")`、PolarDB 方言、通用门面 `Sql.database(...)` / `database(driver)`、条件树 `conditionBy`、表格请求 `db.table`；引擎写法的实库场景在 query 的 `java/query-it/.../workflow/EngineTest` 与 TS `test/src/live/workflow/engine.live.test.ts` |
| vue-tsx-pro（公开） | `kenconnet666/vue-tsx-pro` | 前端技术栈与通用底座：画布 `@zerodep-dev/vue-tsx-pro-ui/diagram`（节点、连线、泳道、撤销重做、小地图、导出）、条件构造器 `ConditionBuilder`（条件树与 query 的 `conditionBy` 同形）、人员与组织选择器 `PeoplePicker`、FilterBar 的高级筛选、`QueryTable` + `useTableQuery`（与 query 的 `db.table` 对接） |

**0003 的 B**：流程组件（流程图、设计器、审批面板、收件箱）在本仓库按模型包重做，界面风格与 vue-tsx-pro 新的画布示例一致
（虚线泳道、点阵、`DiagramNode`）；vue-tsx-pro 的 `/workflow` 子路径（FlowDiagram、FlowDesigner、ApprovalActions、流程定义与校验）2026-10-26 已删除，
旧实现在 vue-tsx-pro 的提交 `f7bfdc2e` 的 `packages/ui/src/workflow/`，重做时可以参考（契约的类型、校验规则、排布、文案）。前端方案在 [docs/frontend.md](docs/frontend.md)。
组件库暂时缺的能力，本仓库像 vue-tsx-pro 的文档站那样用 `@zerodep-dev/vue-tsx-pro-tsx` 的 Vite 预设直接编译自己的 TSX 补上，不必等组件库发版。
模型包定稿时把流程定义条件里的 `var` 对齐成 `field`（与组件库的 `Condition` 同形）。

改契约（定义 JSON、运行时类型、条件树、表格请求）要三个仓库一起看：前端组件在 vue-tsx-pro，两个后端与模型包在本仓库，SQL 写法在 query。

## 3. 待讨论（开工前与用户定）

按 0001 的“待讨论”，2026-10-26 进展：

1. 定义 JSON：已在 [0005](docs/decisions/0005-definition.md) 定稿。
2. 引擎对外的接口：已在 [0006](docs/decisions/0006-engine-api.md) 定稿。
3. 模块划分与场景用例：已定（0001 的"模块划分"）：全部在本仓库，`java/`、`typescript/`、`spec/`、`examples/` 四棵，照 query 的组织。
4. 雪花的机器号：作废（2026-10-26 主键改为数据库自增）。
5. 表：已在 [0007](docs/decisions/0007-tables.md) 定稿。
6. 0002 能力清单：已逐项确认（2026-10-26）。

参考实现的结论（只借鉴思想，不拷代码）：

- **warm-flow** 1.8.9：七张表，任务与办理人行分开值得借鉴；缺点是契约靠字符串拼接、没有事务与乐观锁（并行汇聚会卡死）、几乎没有索引。
- **FlowLong** 1.2.7：钉钉式节点树、运行与历史分表、`task_actor` 带类型与票签权重，能力全；但没有乐观锁、条件拼字符串交给 SpEL；
  许可 Apache-2.0 / AGPL-3.0 加附加协议，设计器 AGPL。
- **Flowable / Camunda**：值得借鉴的是“谁能处理”拆成身份链接、job 表统一承载定时 / 超时 / 提醒 / 重试（lock_owner + lock_exp 抢占）、
  REV_ 乐观锁、定义版本不可变；执行令牌树、BYTEARRAY、多种 job 表属于过度设计。
- **并发写法**（已在 query 的实库场景里五个库验证）：推进前 `UPDATE 实例 SET rev = rev + 1` 锁行；提交任务用 CAS
  （`UPDATE task SET status = DONE WHERE id = ? AND status = ACTIVE`）；会签、汇聚在实例或节点行上 `FOR UPDATE` 计数；
  领取作业用“普通读候选 + 带条件的 UPDATE 抢占”，不用 `SKIP LOCKED`（InnoDB 会锁住扫描到的所有行）。云上 PolarDB 默认读已提交。

## 4. 做事方式（用户的长期要求）

这些原本记在原电脑的 Claude 记忆里，照抄在这里：

- **语言**：对话、说明、注释用中文；代码、标识符、日志、路径保持英文。
- **先讨论再动手**：设计、表结构、接口先写进 `docs/decisions/` 和用户讨论，确认后才写代码；用户确认的项在文档里标“已定 / 已采纳”。
- **标准行为都能替换**（2026-10-26，所有设计都守这一条）：用泛型、接口或其他方式留出替换口，接入方不满意任何标准行为，都能换成自己的实现。
  每设计一块就列出它的标准行为，逐个确认能被替换；决定文档里单列"可替换的部分"。Java 用接口加 Spring 的默认实现（`@ConditionalOnMissingBean`），TS 用选项传实现。
- **宽松，交给接入方**：安全、权限、页面与接口的保护由接入方自己的框架负责，引擎默认放行、只给扩展点与文档提示（0004 第 6 节）。
- **标准组织模型**（2026-10-26）：用户、部门（部门下面有人）、角色，加接入方自己的权限、菜单、按钮级权限；"岗位"不是标准名词，要用时绑定到角色，或由接入方扩展一种类型（0005 的 3.2"扩展的种类"）。
- **不派子代理**：没有用户明确指示时不用 Agent / fork / Workflow，调研、读代码自己做。
- **MySQL / PostgreSQL 优先**：两者都支持的写法按性能更好的做（递归 CTE、LATERAL 等），其他库跳过、换行为或分别实现。
- **本地测过就合并**：本地只跑改动相关的检查与测试，通过就合并进主分支并推送（本仓库与 vue-tsx-pro 直接推 master；query 走 PR 再合并），
  不等 CI；全量检查交给 CI，挂了再回来修。
- **整体优化**：不必迁就旧的写法与约定，该重做就重做，改过的约定写回规范文档。
- **通用性**：引擎要通用、扩展性强。用户背后的真实业务项目只用来检验扩展点够不够，它的业务名词、编号、公司信息**不进任何仓库**
  （公开仓库尤其如此；示例、测试、文档一律用通用的虚构场景：采购、销售、门店、供应商甲）。
- **密钥**：`GITHUB_TOKEN`、`CONTEXT7_API_KEY`、数据库密码只放用户级环境变量或已忽略的 `.env`，配置用 `${VAR}` 引用，绝不写进文件或提交。
- **发布**：npm 与 Maven 的发布都由用户手动做，不要替他发布。做引擎时碰到 query、wire、组件库这些基础设施的问题，直接在那个仓库里改好，只在本地打包安装给本仓库用（用户 2026-10-26）。

## 5. 换电脑

`git clone` 之后仓库里的东西就齐了。三个仓库放在同一个目录下（原电脑是 `C:\Users\lionheart\IdeaProjects\`），
本仓库 `.claude/launch.json` 按相对路径 `../vue-tsx-pro` 起组件库的文档站。

| 东西 | 原电脑上的位置 | 新电脑上怎么办 |
| --- | --- | --- |
| 三个仓库 | `IdeaProjects\flow`、`query`、`vue-tsx-pro` | `git clone https://github.com/kenconnet666/<名字>` 放在同一目录 |
| 参考仓库（只读） | `IdeaProjects\flow-references\`（warm-flow、flowlong、flowlong-designer） | 需要时重新浅克隆；不影响构建 |
| Claude 项目记忆 | `C:\Users\lionheart\.claude\projects\C--Users-lionheart-IdeaProjects-<仓库>\memory\` | 含真实业务项目的需求要点与资料位置（所以不进仓库）。按需手动拷到新电脑对应目录（目录名随用户名与路径变）；没有它也能继续，长期要求已抄在各仓库的交接文档里 |
| 开发数据库 | WSL Debian 里的 Docker（query 的 `infra/*/compose.yaml`），密码在各目录已忽略的 `.env` | 见 query 的交接文档“换电脑后的准备”；WSL 空闲会关机，跑实库测试前挂一个 `wsl -d Debian -- sleep 14400` |
| 工具 | JDK 17（query 是 Java 17）、Node 24、pnpm（vue-tsx-pro 用 12、query 用 11，由 `packageManager` 固定）、`gh` | 新装；vue-tsx-pro 首次跑浏览器测试前 `pnpm --filter @vue-tsx-pro/host-demo exec playwright install chromium firefox webkit` |
| 用户级环境变量 | `GITHUB_TOKEN`、`CONTEXT7_API_KEY`、发布用的 `NPM_TOKEN` | 在新电脑重新设置 |

## 6. 下一步

主线是流程引擎（用户 2026-10-26）：query 与组件库只在引擎用到时补，不单独开新活。顺序按 0004 "已定"里的"顺序"一行，② 已完成。

第 ③ 步最小链路的拆分（2026-10-26，用户让先做下去）：

| 步 | 做什么 | 顺带的基础设施 |
| --- | --- | --- |
| ③a（已完成） | Java `flow-core` 的窄链：建表脚本（0007 里链路用到的表）、实例行 `rev` 加锁与待办的带条件更新、发布表单与流程、命令 `start` / `claim` / `complete` / `withdraw`、查询收件箱与实例详情、节点 start / end / task / exclusive、处理人来源（指定人、角色、部门、发起人、同某节点）、表单按节点权限合并 | 表名前缀先固定 `flow_`。管道的事件监听、幂等（`flow_request`）、拦截器后来补上（③u） |
| ③b（已完成） | `spec/scenarios/` 场景用例与 `flow-it` 的实库跑法（MySQL，库 `flow_it`）：场景是两个后端共用的验收标准 | — |
| ③c（已完成） | `flow-spring`：自动配置、可选的 HTTP 接口（0008 的响应体）、`examples/spring-boot` | 管道的其余部分后来补上（③u） |
| ③d（已完成，2026-10-27） | 前端：`typescript/packages/ui`（`@zerodep-dev/flow-ui`：引擎客户端、表单运行时 `/form`（代码表单的写法、表单容器、按清单自动排版）、收件箱、待办详情、发起页、流转记录、`flow.` 错误文案）；`typescript/apps/demo` 接 Spring 示例后端，在浏览器里走通了发起 → 财务复核 → 总经理审批，以及否决 | vue-tsx-pro 9.8（`browser-compile`、`code-editor`）、9.9（人员选择器的角色）；后端补了发起列表、发起页、待办详情三个查询 |

| ③e（已完成，2026-10-27，Java 先做） | 引擎能力：并行与包容网关（结构化配对）、退回 / 提级 / 重新提交后直接回去、会签（全部、依次、或签、比例、票数，一票否决与兜底）、协作（转办、委派、加签减签、退回收件箱、催办、留言）、管理员（终止、暂停恢复、跳转、改派）；查询我已办、我发起的，实例详情的表单按流程级 `formView` 只读给出、带"能不能撤回 / 催办"；待办详情带"能转办 / 委派 / 加签 / 退回收件箱"。前端 flow-ui 补了这些的客户端方法、协作按钮与对话框、我已办与我发起的列表、流程详情 | 场景见 `spec/scenarios`（contract-*、countersign、purchase-collaboration、purchase-views 等） |
| ③f（已完成，2026-10-27） | 定时与作业：`flow_job`（V2 脚本）、`JobStore`、`JobRunner`（普通读加带条件的更新抢占，跨租户、做每个作业时切到它的租户）、内置作业 timer / overdue / remind / unclaimed、`JobHandler` 注册新种类、`WorkCalendar`（默认周一到周五）；定时节点；待办的截止时间。Spring 默认每 10 秒做一次到期的作业（`flow.jobs.*`、`flow.calendar.zone`）；场景用 `runJobs` 拨时钟 | 场景 timer-and-overdue、overdue-mark-reminders；示例里发布一个一分钟的定时流程在浏览器里看过超时与自动提醒 |
| ③g（已完成，2026-10-27） | 抄送：`flow_cc`（V3 脚本）、`CcStore`；抄送节点（`to` 的来源展开成人，角色、部门要接入方实现 `OrgDirectory.members`）、命令 `cc`（知会）与 `readCc`、查询"抄送我的"（未读在前）；前端 `FlowCcList`、流程详情上的"抄送" | 场景 cc |
| ③h（已完成，2026-10-27） | 监听（0006 第 5 节）：`FlowListener`（事务内的抛错回滚、提交后的发通知，按事件种类与流程过滤，带节点的 meta）、`AfterCommit`（Spring 里等外面的事务提交）；引擎的事务边界统一在 `EngineContext.transaction`，流转记录统一经 `Execution.publish` 写下；示例的 `ExampleNotifications` 打日志演示通知 | `ListenerTest` |
| ③i（已完成，2026-10-27） | 扩展与自动节点：`PeopleResolver`（`{ resolver }` 来源，人工、会签、抄送都能用）、`OutcomeGuard`（处理结果的 `guards`，不通过报 `flow.guard-rejected` 带原因）、`ServiceAction`（`service` 节点：同步在流转的事务里做、失败回滚；异步排 `action` 作业、按 `retry` 重试、用完记 `job.failed`，管理员 `retryJob`）；`{ group }` 来源（扩展的候选种类，`Actor.groups()`）；发布时查扩展名有没有注册 | 场景 extensions-and-service（场景里注册的扩展见 spec/scenarios 的 README） |
| ③j（已完成，2026-10-27） | 等外部事件：`flow_wait`（V4 脚本）、`WaitStore`；`receive` 节点（`due` 超时记 `node.overdue`，`overdue.complete` 按结果往下走）；命令 `signal`（按实例或业务单号，结果要是写过的，`output` 写进表单或流程变量，不在等的只记 `signal.ignored`）；HTTP `/commands/signal` | 场景 receive-signal |
| ③k（已完成，2026-10-27） | 调用流程（`call`）：`forEach` / `as`（`as.字段` 也能引用）、`input` 写进子实例的表单或变量、同步等这一轮的子实例都结束（结果一样才按它）、`output` 收进父实例的变量（`forEach` 收成列表）、异步不等；父实例终止、撤回时子实例一起终止；子实例马上结束时直接回到同一个命令里的父实例；V5 脚本加 `parent_round`；`DefinitionCache` 并进 `EngineContext` | 场景 call-process |
| ③l（已完成，2026-10-27） | 上一步选下一步的处理人（0005 的 5.6 `pick`）：`start` / `complete` 带 `picks`，核对 from 与 max；发起页、待办详情按结果算出要选人的下一步与能选的人（`PickPreview` 穿过网关与抄送）；前端 `PickDialog` 接进审批面板与发起页 | 场景 pick；浏览器里走过发起选人、审批后选人 |
| ③m（已完成，2026-10-27） | 命令的幂等：`flow_request`（V6 脚本）、`RequestStore`、`FlowEngine.idempotent`（与命令同一个事务记下响应，同一个编号再来返回第一次的，用在别的命令或换了人报 `requestId: reused`）；HTTP 的命令带 `Idempotency-Key` 头时走它 | `SpringHttpTest`；过期记录的清理还没做 |
| ③n（已完成，2026-10-27） | 管理端：查询 `instances`（按状态、流程、标题、发起人）、`processes`、`failedJobs`，实例详情带跳转能去的节点；前端 `FlowInstanceMonitor`（流程监控）与流程详情的管理员操作（暂停、恢复、终止、跳转、改派，`InstanceAdminActions`）；示例前端加"流程监控" | 场景 admin-queries；浏览器里走过筛选、暂停恢复、跳转 |
| ③o（已完成，2026-10-27） | 流程图（只读，⑤ 的第一步）：实例详情带 `diagram`（节点走过、正停着、没到，连线走过没有，位置取定义的 `layout`）；前端 `FlowDiagram`（组件库的 Diagram，没画布局时按步数自动分列），接进流程详情与待办详情 | 场景 purchase-large-amount 的 diagram；diagram-layout 单元测试；浏览器里看过 |
| ③p（已完成，2026-10-27） | 流程管理：校验定义不发布（错误与警告，`validateDefinition`）、停用与启用、版本列表、取某个版本的原文；前端子路径 `@zerodep-dev/flow-ui/admin`（`FlowProcessList`、`FlowDefinitionEditor` 用代码编辑器改 JSON、校验、发布，问题代号有中文说明，`FlowInstanceMonitor` 也搬到这里）；示例前端加"流程管理" | 场景 process-manage；浏览器里走过停用、校验出错、发布被拒、改好发布 |
| ③q（已完成，2026-10-27） | 处理人为空转管理员（`emptyAssignee: admin`，节点上的盖过流程级的，`OrgDirectory.administrators`）；无人认领转收件箱负责人指定（`OrgDirectory.inboxManager`、记下 designator、查询"待我指定"、命令 `assign`）；前端待办详情"指定承办人"、FlowInbox 的 designations | 场景 designation；浏览器里走过一分钟没人认领后负责人指定 |
| ③r（已完成，2026-10-27） | 处理人撤回刚处理的一步（`withdrawStep`）：处理时存一份快照（`flow_step`，每个实例只留最近一步），撤回时取消下一步的待办、恢复运行状态/变量/表单、待办重新打开；要求节点 `allow.withdraw`、下一步还没人动（没认领、没处理）、这一步之后只进过人工/网关/抄送/结束节点；前端"我已办"打开待办，详情上"撤回这一步" | 场景 withdraw-step；浏览器里走过 |
| ③s（已完成，2026-10-27） | 自动节点失败的异常处理：重试用完的作业成了"异常"（查询 `incidents`，带节点、原因、能选的结果；原替代 `failedJobs`），管理员重试、跳过并按结果往下走（`skipJob`，记 `job.skipped`）或终止；`onFailure: "error"` 时整个实例暂停；失败原因记最里层异常的话。前端 admin 的 `FlowIncidentList`（异常处理）、流程详情（admin）上的异常一块、`IncidentActions`；示例后端加动作 `demo.push`、通知监听 `job.failed` | 场景 incidents；浏览器里走过列表跳过、详情重试、失败即停→恢复→跳过 |
| ③t（已完成，2026-10-27） | 退回时选节点（处理结果 `{ kind: "return", target: "choose" }`，0005 的 5.4）：待办详情里这个结果带 `targets`（上游走到过的人工节点、上次的处理人），处理命令带 `returnTarget`；同沿退回线走（跳出并行区域取消、落到原处理人、`returnTo: resubmit` 生效）；发布校验 `return-target-invalid`、`outcome-choose-automatic`（Java、TS 同步）；前端审批面板点这个结果弹"退回到哪一步" | 场景 return-choose、定义 return-choose；浏览器里走过退回到起草、改完直接回来 |
| ③u（已完成，2026-10-27） | 命令拦截器（0006 第 6 节"管道"）：命令记录都实现 `FlowCommand`，`CommandInterceptor` 环绕式（先注册的在外层），和标准处理在同一个事务里；`FlowEngine` 的 23 个命令都走 `execute`（权限 → 拦截器 → 标准处理）；Builder `interceptor(...)`、Spring 的 Bean 按 `@Order` 注册 | 场景 interceptors（拦下、改参数、做完再拦整个回滚） |
| ③v（已完成，2026-10-27） | 幂等记录的定期清理：`RequestStore.deleteBefore`（跨租户，走 created_at 索引）、`engine.purgeRequests(olderThan)`；Spring 的 `FlowJobPoller` 每小时删一次，留 `flow.idempotency.retention`（默认 24 小时）；租户过滤器的名字收成 `FlowDatabase.TENANT_FILTER` | SpringHttpTest 的幂等用例补了清理 |
| ③w（已完成，2026-10-27） | 表单的管理：查询 `forms`、`formVersions`、`formDocument`，停用启用（停用的表单新发布的流程不能再选，校验报 `form-disabled`）；前端 admin 的 `FlowFormList`、`FlowFormEditor`（字段清单或代码两种写法，边改边校验、预览，清单转代码用 form-tools 的 `codeFromManifest`）；示例前端加"表单管理" | 场景 forms-admin、form-tools 的单测；浏览器里走过编辑采购单、转成代码、发布第 3 版、流程改用后发起页按代码渲染 |
| ④a（已完成，2026-10-27） | TS 引擎 `@zerodep-dev/flow-core`（typescript/packages/core）：照 Java flow-core 移植——query TS 上的默认存储（同一套表与 JSON 形状，两个后端读写同一张表互通）、发布与校验、发起、认领、处理（会签、委派加签回到原待办、退回选节点、守卫）、撤回、撤回这一步、命令拦截器、监听、各个查询（待办详情、实例详情带流程图、我的待办、已办、我发起的、抄送我的、管理端）；模型包加 `FLOW_EVENT` 常量，校验对没写 outcomes 的人工节点不再出错；TS 的场景运行器跑 spec/scenarios 的实库（`pnpm test:live`，CI 检出 query、起 MySQL）；建表脚本从 Java 的 resources 来（④c 起生成进源码，见下） | 场景 29 个里 17 个通过、0 失败；12 个跳过（要作业的轮询、协作命令、管理员命令、外部事件、子流程、表单管理与异常的查询，见下一步） |
| ④b（已完成，2026-10-27） | TS 引擎补齐：作业（`JobRunner` 抢占、按租户做、重试与失败；内置 timer、overdue、remind、unclaimed、action、receiveTimeout，`onFailure: error` 暂停）、协作命令（转办、委派、加签减签、退回收件箱、催办、留言、抄送与已读、指定承办人）、管理员命令（终止、暂停恢复、跳转、改派、重试与跳过失败的作业）、外部事件（`flow_wait`、signal）、调用流程（同步异步、forEach、子实例结束回到父实例、连带终止）、异常与表单管理的查询、幂等（`idempotent`、`purgeRequests`）；引擎自己推进时的操作人 `SYSTEM_ACTOR` | 场景 29 个全部通过（同 Java） |
| ④c（已完成，2026-10-27） | `@zerodep-dev/flow-nuxt`（typescript/packages/nuxt）：`createFlowHandler({ engine, actor, prefix })` 是 h3 的处理函数，路径、参数、响应体同 Spring 的 `FlowHttpController`（请求体的读法同 `RequestBody`，带 `Idempotency-Key` 的命令幂等）；`startFlowJobs(engine, { intervalMs, retentionSeconds })` 同 `FlowJobPoller`。flow-core 加 `migrateFlowSchema(driver)`（query 的迁移器，历史表 `flow_migration`）与 `MYSQL_SCRIPTS`：建表脚本由 `packages/core/scripts/sync-sql.mjs` 从 Java 的 resources 生成进源码，单测比对两边。Nitro 示例后端 `typescript/apps/demo-server`（同 examples/spring-boot，本机 `.claude/run-node-backend.sh`）。顺带修 Java：外部事件的结果改成与命令同形（原先是 `created`，前端读 `workItems`），发布定义、表单返回的编号也走 typed JSON | TS 实库 32 个（场景 29 + HTTP 3）、单测 122、Java 154 全部通过；浏览器里示例前端接 Node 后端走完一单采购（发起、财务认领通过、总经理同意），异常处理重试（Node 的轮询重做、再失败）与跳过，流程与表单管理页；Node 后端读写 Java 建的 flow_demo，同一个实例的流转记录里两边的步骤接得上；`nitro build` 的产物对空库建表 7 个版本 |

### 无人值守时按推荐定（2026-10-27，待用户过目）

用户离开时让我"按推荐和取舍自行决策"，下面这些是那时定的，没有和用户讨论过，回来请过一遍：

1. **ID**：主键都是数据库自增 BIGINT；用户、部门、角色、租户的编号也是 BIGINT（租户默认 0）；流程、表单、节点的键与业务单号仍是文字。
2. **定义与清单存 MEDIUMTEXT 不用 JSON 列**：MySQL 的 JSON 类型会重排键的顺序，表单清单的字段顺序就是界面顺序。
3. **表单提交**：能改的字段没提交（键不在）保留原值，显式给 null 才清空。
4. **网关配对按结构**：并行、包容的分叉与汇合按支配树配对（`GatewayPairs`），不支持交叉的非结构化图；退回跳出并行区域时取消区域里其余分支。
5. **退回给原处理人、提级只进收件箱**：退回到的节点落给上次处理它的人；提级（escalate）只按节点上的收件箱来源生成，不落人。
6. **加签的语义**：前加签时自己的待办等着、加的人先办；后加签时加的人的待办先生成但等着，自己办完才打开；并加一起办。减签只能减自己加的、还没办的人。
7. **催办一小时一次**：同一个待办一小时内只记一次催办事件，通知由接入方监听流转记录去发。
8. **实例详情的表单**：不在待办里看表单的人（发起人看进度、我已办里打开）一律只读，按流程级 `formView` 隐藏字段；在待办里看走待办详情（节点权限）。
9. **待办列表的行**带实例状态与处理结果的名字（按实例的定义版本查 outcome 的 label），我已办不用再查定义。
10. **流转记录存名字快照**：处理结果的名字（`outcomeLabel`）、转办委派改派的对象（`toUserName`）、加签的人（`userNames`）、减签的人（`userName`）都记在事件数据里。
11. **撤回整步（withdrawStep）用快照回滚**：每个实例只留最近一步的快照（`flow_step`），所以只能撤回最后处理的那一步；这一步之后进过自动节点（服务、子流程、等事件）就不能撤，因为它们的副作用回滚不了；实例结束了也不能撤。`returnTarget: "choose"`（退回时让人选节点）还没做。
12. **作业**：工作日默认周一到周五、按 UTC 的日期（Spring 用 `flow.calendar.zone` 改时区，节假日要接入方实现 `WorkCalendar`，`flow_calendar` 表还没建）；`until` 只写日期时算那天结束；待办办完了不去删它的作业，作业到点看到待办不再打开就跳过（实例结束时才统一删）；实例暂停时作业往后推一小时；失败重试 3 次，间隔 1、4、9 分钟；超时默认只记一条"超时"（`mark`）。
13. **转办、委派、加签的待办沿用原来那一份的截止时间**，不重新计时。
14. **子流程**：子实例的发起人是父实例的发起人（出现在他的"我发起的"里），流转记录上的操作人是触发调用的人；父实例暂停时子实例结束不推进父实例（恢复后要管理员跳转）——以后可以改成恢复时补上。
15. **异常不生成人工待办**：0005 原先写"给管理员生成异常处理待办"，改成管理端的异常列表加实例详情上的一块（同 Camunda 的 incident、Flowable 的 dead-letter job）——待办挂在自动节点上要另造一套表单、结果、处理人规则，收益不大；通知管理员交给接入方监听 `job.failed`。跳过时结果必须是出边上写的；暂停的实例要先恢复才能重试或跳过。
16. **退回时选节点不进别的并行区域**：能选的限于当前节点所在区域之内或之外的上游，旁边的分支、已经汇合过的区域里的节点不给（进去了汇合等不齐）；一个能选的都没有时按钮不给；会签时用让节点结束的那个人选的目标。
17. **按命令替换处理类用拦截器做**：不另设 `CommandHandler<C>`，拦截器认出命令后不调 proceed 就是换掉标准行为；发布定义、发布表单、停用启用暂不过拦截器（不是命令记录，审计可以先用 PermissionPolicy 记）。
18. **表单发布时只存源码**：编译结果与编译器版本（0004 的 5.4）等组件库给出编译器版本号后再存；现在审批页按源码在浏览器里编译（已有的路径）。表单编辑器预览也是这条路径。
19. **Node 版建表用 query 的迁移器**：历史表 `flow_migration`，与 Java 的 Flyway（`flow_schema_history`）互不认，同一个库只由一边建表（示例后端连 Java 建好的库时设 `FLOW_MIGRATE=false`）。脚本生成成 TS 模块而不是运行时读文件：应用经 Nitro、Vite 打包后 `import.meta.url` 变了，按相对路径找不到。
20. **flow-nuxt 不做 Nuxt 模块**：只给 h3 的处理函数与作业轮询，接入是一个路由文件（`server/routes/flow/[...path].ts`）加一个插件；模块（nuxt.config 的 modules 自动注册）多一层配置、收益小，用户要时再包。
21. **Node 后端的时间不带 timestamptz 标记**：引擎里本来就是 ISO 文字（同 Java `Instant` 的写法），前端请求层默认也按文字用，所以响应体只给编号记 bigint；两个后端的 data 除了 `$types` 里少这几项外相同。
22. **取不到操作人**：`actor` 抛 FlowError（如 `flow.forbidden`）时同样是 200 加错误码（同 Spring 示例）；抛 h3 的错误（`createError({ statusCode: 401 })`）原样交给 h3。

下面是还没做的、按优先级：⑤ 可视化设计器 → ⑥ 表单设计器。
