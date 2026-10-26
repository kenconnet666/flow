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
| ③a（已完成） | Java `flow-core` 的窄链：建表脚本（0007 里链路用到的表）、实例行 `rev` 加锁与待办的带条件更新、发布表单与流程、命令 `start` / `claim` / `complete` / `withdraw`、查询收件箱与实例详情、节点 start / end / task / exclusive、处理人来源（指定人、角色、部门、发起人、同某节点）、表单按节点权限合并 | 表名前缀先固定 `flow_`。还没做的管道部分：拦截器、按命令替换处理类、幂等（`flow_request`）、事件监听，随 ③c 补 |
| ③b（已完成） | `spec/scenarios/` 场景用例与 `flow-it` 的实库跑法（MySQL，库 `flow_it`）：场景是两个后端共用的验收标准 | — |
| ③c（已完成） | `flow-spring`：自动配置、可选的 HTTP 接口（0008 的响应体）、`examples/spring-boot` | 管道里还没做的（拦截器、按命令替换处理类、幂等、事件监听）随后补 |
| ③d（已完成，2026-10-27） | 前端：`typescript/packages/ui`（`@zerodep-dev/flow-ui`：引擎客户端、表单运行时 `/form`（代码表单的写法、表单容器、按清单自动排版）、收件箱、待办详情、发起页、流转记录、`flow.` 错误文案）；`typescript/apps/demo` 接 Spring 示例后端，在浏览器里走通了发起 → 财务复核 → 总经理审批，以及否决 | vue-tsx-pro 9.8（`browser-compile`、`code-editor`）、9.9（人员选择器的角色）；后端补了发起列表、发起页、待办详情三个查询 |

| ③e（已完成，2026-10-27，Java 先做） | 引擎能力：并行与包容网关（结构化配对）、退回 / 提级 / 重新提交后直接回去、会签（全部、依次、或签、比例、票数，一票否决与兜底）、协作（转办、委派、加签减签、退回收件箱、催办、留言）、管理员（终止、暂停恢复、跳转、改派）；查询我已办、我发起的，实例详情的表单按流程级 `formView` 只读给出、带"能不能撤回 / 催办"；待办详情带"能转办 / 委派 / 加签 / 退回收件箱"。前端 flow-ui 补了这些的客户端方法、协作按钮与对话框、我已办与我发起的列表、流程详情 | 场景见 `spec/scenarios`（contract-*、countersign、purchase-collaboration、purchase-views 等） |
| ③f（已完成，2026-10-27） | 定时与作业：`flow_job`（V2 脚本）、`JobStore`、`JobRunner`（普通读加带条件的更新抢占，跨租户、做每个作业时切到它的租户）、内置作业 timer / overdue / remind / unclaimed、`JobHandler` 注册新种类、`WorkCalendar`（默认周一到周五）；定时节点；待办的截止时间。Spring 默认每 10 秒做一次到期的作业（`flow.jobs.*`、`flow.calendar.zone`）；场景用 `runJobs` 拨时钟 | 场景 timer-and-overdue、overdue-mark-reminders；示例里发布一个一分钟的定时流程在浏览器里看过超时与自动提醒 |
| ③g（已完成，2026-10-27） | 抄送：`flow_cc`（V3 脚本）、`CcStore`；抄送节点（`to` 的来源展开成人，角色、部门要接入方实现 `OrgDirectory.members`）、命令 `cc`（知会）与 `readCc`、查询"抄送我的"（未读在前）；前端 `FlowCcList`、流程详情上的"抄送" | 场景 cc |
| ③h（已完成，2026-10-27） | 监听（0006 第 5 节）：`FlowListener`（事务内的抛错回滚、提交后的发通知，按事件种类与流程过滤，带节点的 meta）、`AfterCommit`（Spring 里等外面的事务提交）；引擎的事务边界统一在 `EngineContext.transaction`，流转记录统一经 `Execution.publish` 写下；示例的 `ExampleNotifications` 打日志演示通知 | `ListenerTest` |
| ③i（已完成，2026-10-27） | 扩展与自动节点：`PeopleResolver`（`{ resolver }` 来源，人工、会签、抄送都能用）、`OutcomeGuard`（处理结果的 `guards`，不通过报 `flow.guard-rejected` 带原因）、`ServiceAction`（`service` 节点：同步在流转的事务里做、失败回滚；异步排 `action` 作业、按 `retry` 重试、用完记 `job.failed`，管理员 `retryJob`）；`{ group }` 来源（扩展的候选种类，`Actor.groups()`）；发布时查扩展名有没有注册 | 场景 extensions-and-service（场景里注册的扩展见 spec/scenarios 的 README） |
| ③j（已完成，2026-10-27） | 等外部事件：`flow_wait`（V4 脚本）、`WaitStore`；`receive` 节点（`due` 超时记 `node.overdue`，`overdue.complete` 按结果往下走）；命令 `signal`（按实例或业务单号，结果要是写过的，`output` 写进表单或流程变量，不在等的只记 `signal.ignored`）；HTTP `/commands/signal` | 场景 receive-signal |
| ③k（已完成，2026-10-27） | 调用流程（`call`）：`forEach` / `as`（`as.字段` 也能引用）、`input` 写进子实例的表单或变量、同步等这一轮的子实例都结束（结果一样才按它）、`output` 收进父实例的变量（`forEach` 收成列表）、异步不等；父实例终止、撤回时子实例一起终止；子实例马上结束时直接回到同一个命令里的父实例；V5 脚本加 `parent_round`；`DefinitionCache` 并进 `EngineContext` | 场景 call-process |

之后：④ TS `core` 与 `flow-nuxt` 跑同一批场景 → 两边按场景补齐还没做的能力（定时与作业、抄送、子流程、外部事件、服务节点）→ ⑤ 流程图与设计器 → ⑥ 表单设计器。

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
11. **撤回整步（withdrawStep）推迟**：要回滚快照，暂不做；`returnTarget: "choose"`（退回时让人选节点）也推迟。
12. **作业**：工作日默认周一到周五、按 UTC 的日期（Spring 用 `flow.calendar.zone` 改时区，节假日要接入方实现 `WorkCalendar`，`flow_calendar` 表还没建）；`until` 只写日期时算那天结束；待办办完了不去删它的作业，作业到点看到待办不再打开就跳过（实例结束时才统一删）；实例暂停时作业往后推一小时；失败重试 3 次，间隔 1、4、9 分钟；超时默认只记一条"超时"（`mark`）。
13. **转办、委派、加签的待办沿用原来那一份的截止时间**，不重新计时。
14. **子流程**：子实例的发起人是父实例的发起人（出现在他的"我发起的"里），流转记录上的操作人是触发调用的人；父实例暂停时子实例结束不推进父实例（恢复后要管理员跳转）——以后可以改成恢复时补上。

下面是还没做的、按优先级： 无人认领转负责人指定的"待指定"列表与指定命令（现在只标 needs_designation 并记一条流转记录）→ 处理人来源的 pick、`emptyAssignee: admin` → 自动节点的 `onFailure: manual`（给管理员的异常处理待办）与失败作业的查询 → 幂等（`flow_request`）、命令拦截器 → ④ TS 引擎。
