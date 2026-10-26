# 交接文档

给接手这个仓库的人（或换了电脑的自己）：读完这一页就能接着做。方向与已定的事在 `docs/decisions/`，这里只讲现状、
三个仓库的关系、做事方式和换电脑要准备的东西。

## 1. 现状（2026-10-26）

- **本仓库还没有引擎代码**：只有 Maven 骨架（`pom.xml`，Java 17，`org.dpdns.zerodep:flow`）和三份决定文档。
  动手写代码之前先和用户把下面“待讨论”定下来——用户的要求是**先讨论方案，确认后才动代码**。
- **决定文档**：
  - [0001 引擎的方向](docs/decisions/0001-engine-direction.md)：讨论中。“已定”一节用户确认过（第一版只做 MySQL 8 / PolarDB MySQL 8.0、
    主键默认雪花 BIGINT（另带大雪花，也可实现接口自定义，见 0006 的 6.1）、时间按 UTC、流程变量放实例上的 JSON 列、Java 一个 jar 接 Spring、TS 一个包接 Nuxt、前端固定 vue-tsx-pro）；
    “初步方向”“表的草案”还待确认。
  - [0002 能力清单](docs/decisions/0002-capabilities.md)：讨论中，用户要求“需要的功能第一版都做，尽量完善”，逐项讨论后定稿。
  - [0003 模型包与前端的分工](docs/decisions/0003-model-and-frontend.md)：已采纳，前端取 B（见下）。
  - [0007 表](docs/decisions/0007-tables.md)：已定（2026-10-26）。待办不拆运行与历史、冷数据进归档表；候选只存打开着的待办，角色、部门按查询时的成员匹配；运行状态放实例 JSON；租户列都有；名字存快照；流程与表单都是"本身 + 版本"。
  - [0006 引擎对外的接口](docs/decisions/0006-engine-api.md)：已定（2026-10-26）。命令管道、操作人泛型 U、命令与查询清单、事件、扩展点与可替换的部分、两个后端的形态与相同的 HTTP 接口、错误代号；主键只做雪花与大雪花（6.1），query 配合补"列类型按配置决定"并发版。
  - [0005 流程定义 JSON](docs/decisions/0005-definition.md)：已定（2026-10-26 逐节定稿）。图加结构校验、布局单独放、`{ ref }` 取 form. / sys. / 流程变量 / fn. 四类；处理人六种来源加"上一步选"；11 种节点；会签"一人拒绝即拒绝"；条件与组件库、query 同规则；问题代号清单；通知按事件可改。
  - [0004 表单](docs/decisions/0004-forms.md)：已定（2026-10-26 逐节定稿）。代码表单（TSX，运行时编译）加拖拽设计器，两边读写同一份源码；
    字段清单是 JSON5 字面量；后端代码在线编辑、存库、启动时加载（Spring Boot 用 Java、Nuxt 用 TS）；节点上四档字段权限；一个流程一张表单，表单的值单独存；
    不做隔离，安全交给接入方的框架；写进业务表用"按业务表生成代码"。剩两项待实测（fat jar 的 classpath、组件根元素接住透传属性）。
- **思路记录**（不采用，只留档）：[Spring 里嵌 GraalJS、引擎核心只写一份 TS](docs/notes/graaljs-in-spring.md)。用户 2026-10-26 定路线不变：Java（Spring Boot）与 Nuxt 两个后端。
- **依赖的两个库由用户手动发布**（2026-10-26 查 npm）：
  - vue-tsx-pro（组件库）已发 npm：`@zerodep-dev/vue-tsx-pro-tsx`、`@zerodep-dev/vue-tsx-pro-ui` 0.1.0（原名 `@vue-tsx-pro/*`），仓库里已推进到 0.2.0-dev；
  - query（ORM）TS 已发 npm `@zerodep-dev/query-*` 2.0.0；Java 的 Maven 中央仓库当时还搜不到 `org.dpdns.zerodep`，根 POM 仍是 `2.0.0-SNAPSHOT`。
  - Maven 发布之前本仓库要用时：用本机 `mvnw install` 出的 SNAPSHOT。

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
4. 雪花的机器号：已定（0001 的"雪花的机器号"）：默认在 `flow_worker` 表里租用，可配置写死或替换 `WorkerIdProvider`。
5. 表：已在 [0007](docs/decisions/0007-tables.md) 定稿。
6. 0002 能力清单逐项确认（多数已在 0004–0006 落实）。

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
- **发布**：npm 与 Maven 的发布都由用户手动做，不要替他发布。

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

1. 用户手动发布 query 的 Java 包到 Maven 中央仓库（npm 两个库都已发布）。
2. 和用户讨论第 3 节剩下的：0002 的逐项确认。整体顺序按 0004 "已定"里的"顺序"一行。
3. 按 0001 的"模块划分"建目录，再建模型包（TS 与 Java 各一份，共用 spec/ 的样例）、两个后端、前端。
