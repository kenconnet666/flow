# 交接文档

给接手这个仓库的人（或换了电脑的自己）：读完这一页就能接着做。方向与已定的事在 `docs/decisions/`，这里只讲现状、
三个仓库的关系、做事方式和换电脑要准备的东西。

## 1. 现状（2026-10-27）

- **一句话**：② 模型包、③ Java 引擎与 Spring、⑤ 流程设计器已完成并在浏览器里走通。2026-10-27 用户定的重做已完成：
  **持久层从 query 换成 easy-query**（实体 + VO，导航与对象树映射）、**Node 后端删掉**（只留 Java）、**表单的值是强类型**（json-wire 自动还原），
  wire 迁成独立仓库 json-wire。随后实体补齐了关系导航，⑥ 表单设计器第一版完成；同日又做完了"把引擎做完整"的五个阶段（自动跳过与通知、暂存、
  设计草稿、表单后端代码、批量与申请改派、管理员改数据、工作日历、列表筛选、实例迁移、外部进度、统计、导入导出复制、版本对比、表单设计器第二步），
  并把 Java 代码整理成普通的 Lombok 类、按职责分包（见第 6 节）。
- **本机预览**（`.claude/launch.json`）：示例前端 `flow-demo`（5180）、后端 `flow-example-backend`（Spring Boot，8080），连本仓库 `infra/mysql` 起的开发 MySQL（WSL 的 Docker，端口 53316）里的
  flow_demo 库；空库先在 `typescript/apps/demo` 下 `pnpm seed` 发布示例的表单与流程。管理端"流程管理"里新建、编辑流程就是设计器。
- **代码**（细节按步骤记在第 6 节的表里）：
  - 目录：根 `pom.xml` 聚合 `java/flow-model`、`flow-core`、`flow-spring`、`flow-it` 与 `examples/spring-boot`；`typescript/` 是 pnpm 工作区
    （`packages/model`（模型包）、`packages/ui`（前端组件与设计器）、`apps/demo`（示例前端）、`test`（模型包的单测））；`spec/` 放共用样例。
  - 模型包：条件求值（`spec/conditions`）、定义的类型与校验（`spec/definitions`）、选边与连线副作用（`spec/routing`）、字段清单自身的校验（`spec/manifests`）、
    提交值的类型校验（`spec/values`）、两个版本的差别（`spec/diff`）、字段权限的合算与提交的合并（`spec/access`）、业务错误码（0008）。
    Java 在 `org.dpdns.zerodep.flow.model.*`，TS 是 `@zerodep-dev/flow-model`（依赖 `@zerodep-dev/json-wire`）。新加一份共用样例只加 JSON 文件，两边的测试都会跑到。
  - **值的约定**：线上、库里的 JSON 列、共用样例都是 typed JSON（json-wire）：编号是 bigint（Long / bigint）、decimal 是 BigDecimal / Decimal、
    date 是 LocalDate / CalendarDate、datetime 与各处的时刻是 Instant / ZonedDateTime，number 是 JavaScript 的数；读进来先按 `$types` 还原，
    模型包按类型校验。清单、定义里人写的值（default、min、max、调用流程的 input）按字段类型读成强类型（`FormManifest.typed`、`typedValue`）。
  - Java 引擎 `java/flow-core`：`FlowEngine`（门面，每个方法传入操作人）与 `FlowEngineBuilder`（组装）、`spi`（接入方实现或用到的全部接口）、`command`、`engine`（推进在 `Execution`；
    `engine/node` 每种节点一个 Behavior（到达时做什么）；`engine/handler` 每类命令一个处理类，步骤写在类注释里；`engine/query` 按对象分的查询；
    还有 `state`、`rule`、`assignee`、`form`、`job`、`store`、`notify`）、`entity`（表：easy-query 的实体，Lombok + `@EntityProxy`，导航写在实体上）、
    `view`（给前端的形状，按 workitem、instance、definition、form、stats 分包：`select(XxxVO.class)` 读出的 VO、`selectAutoInclude` 映射的对象树）。
    **不用 record 与 final class**，一律普通的 Lombok 类（`@Data` / `@Getter @Setter`），访问方法都是 getX；
    `db`（接入方的 easy-query 上要装的：JSON 列的转换器、Instant 按 UTC、租户与修改时间的拦截器）。写库用实体，读给前端用 VO。
    建表仍是 `flow-core` 的 `resources/db/flow/mysql/V*.sql`，推荐 Flyway 单独一套执行（0007 第 9 节）；表名、列名按 easy-query 默认的下划线命名。
  - 场景用例 `spec/scenarios`：引擎的验收标准，格式见那里的 README（场景文件是 typed JSON）；执行器在 `flow-it` 的 `scenario` 包。
  - Spring Boot 适配 `java/flow-spring`：引擎用 easy-query starter 建的 `EasyEntityQuery`（应用配 `easy-query.enable=true`、`easy-query.database=mysql`），
    `flow.*` 配置项见 `FlowProperties`；当前操作人默认取 Spring Security 的 principal；`flow.flyway.enabled` 启动时建表、`flow.http.enabled` 打开接口。
    接口返回普通对象，**typed JSON 由应用自己集成 json-wire**（示例 `examples/spring-boot` 的 `config/wire/WireResponseAdvice`）。按 Spring Boot 3.5 编译。
  - 实库测试 `java/flow-it`：连 `infra/mysql` 的开发 MySQL 里的 `flow_it` 库，变量 `FLOW_MYSQL_URL` / `_USER` / `_PASSWORD`，
    在仓库根目录 `. infra/mysql/live-env.sh` 就设好了（用户 flow，密码在 `infra/mysql/.env`，主机取 WSL 的地址）；没设时跳过。每个测试前用 Flyway 清库再建表。
  - 依赖：easy-query 3.2.17（Maven 中央）；json-wire 0.1.0-SNAPSHOT（本机在 `../json-wire` 里 `mvnw install`，TS 按 `link:` 引用）。
  - 本地跑：`./mvnw -q install`（Java，含实库测试）；`cd typescript && pnpm install && pnpm test && pnpm typecheck:ui && pnpm test:ui`（TS）。
  - CI（`.github/workflows/ci.yml`）：Java（先检出并安装 json-wire，含 MySQL 8.4 跑实库测试）与 TS 两个任务。
- **决定文档**：
  - [0001 引擎的方向](docs/decisions/0001-engine-direction.md)：已定（2026-10-26）。“已定”一节用户确认过（第一版只做 MySQL 8 / PolarDB MySQL 8.0、
    主键是数据库自增的 BIGINT（Java 的 long，2026-10-26 改定，见 0006 的 6.1）、时间按 UTC、流程变量放实例上的 JSON 列、Java 一个 jar 接 Spring、前端固定 vue-tsx-pro；
    2026-10-27 改定：持久层用 easy-query、Node 后端不做）；
    “初步方向”已定，表由 0007 接替。
  - [0002 能力清单](docs/decisions/0002-capabilities.md)：已定（2026-10-26），每节注明落在哪份文档。
  - [0003 模型包与前端的分工](docs/decisions/0003-model-and-frontend.md)：已采纳，前端取 B（见下）。
  - [0007 表](docs/decisions/0007-tables.md)：已定（2026-10-26）。待办不拆运行与历史、冷数据进归档表；候选只存打开着的待办，角色、部门按查询时的成员匹配；运行状态放实例 JSON；租户列都有；名字存快照；流程与表单都是"本身 + 版本"。
  - [0006 引擎对外的接口](docs/decisions/0006-engine-api.md)：已定（2026-10-26）。命令管道、操作人泛型 U、命令与查询清单、事件、扩展点与可替换的部分、HTTP 接口、错误代号；主键改为数据库自增（6.1）；
    2026-10-27 改定：存储不再可替换（引擎直接用 easy-query），列表行是对象树。
  - [0008 业务错误码](docs/decisions/0008-error-codes.md)：已定（2026-10-26）。一律业务错误码、HTTP 一律 200，三个仓库都守；码是带命名空间的文字（`flow.`、`query.`、`http.`），成功是 `ok`，各包提供自己的码并互相对应。
  - [0005 流程定义 JSON](docs/decisions/0005-definition.md)：已定（2026-10-26 逐节定稿）。图加结构校验、布局单独放、`{ ref }` 取 form. / sys. / 流程变量 / fn. 四类；处理人六种来源加"上一步选"；11 种节点；会签"一人拒绝即拒绝"；条件与组件库、query 同规则；问题代号清单；通知按事件可改。
  - [0004 表单](docs/decisions/0004-forms.md)：已定（2026-10-26 逐节定稿）。代码表单（TSX，运行时编译）加拖拽设计器，两边读写同一份源码；
    字段清单是 JSON5 字面量；后端代码在线编辑、存库、启动时加载（Spring Boot 用 Java、Nuxt 用 TS）；节点上四档字段权限；一个流程一张表单，表单的值单独存；
    不做隔离，安全交给接入方的框架；写进业务表用"按业务表生成代码"。剩两项待实测（fat jar 的 classpath、组件根元素接住透传属性）。
- **思路记录**（不采用，只留档）：[Spring 里嵌 GraalJS、引擎核心只写一份 TS](docs/notes/graaljs-in-spring.md)。2026-10-27 用户定只留 Java（Spring Boot）后端，Node 后端删掉（要时从 git 历史取）。
- **依赖的库由用户手动发布**：
  - vue-tsx-pro（组件库）已发 npm：`@zerodep-dev/vue-tsx-pro-tsx`、`@zerodep-dev/vue-tsx-pro-ui` 0.1.0，仓库里已推进到 0.2.0-dev；本仓库按 `link:` 引用源码。
  - json-wire（typed JSON，2026-10-27 从 query 迁出）：Maven `org.dpdns.zerodep:json-wire`、npm `@zerodep-dev/json-wire`，还没发布，本机安装、`link:` 引用。
  - easy-query 是 dromara 的开源项目，按 Maven 中央的发布版本依赖（根 `pom.xml` 的 `easy-query.version`）。

## 2. 相关仓库

| 仓库 | 地址 | 本仓库用它做什么 |
| --- | --- | --- |
| flow（本仓库，私有） | `kenconnet666/flow` | 引擎（Java，Spring Boot）、模型包（Java + TS）、流程组件（前端）、管理端页面 |
| easy-query（开源，dromara） | `dromara/easy-query`（本机 `IdeaProjectseasy-query`） | 持久化：实体、导航、隐式 join、`selectAutoInclude` 对象树、拦截器、Spring Boot starter；读源码查用法比查文档快 |
| json-wire（公开） | `kenconnet666/json-wire` | 强类型 JSON 的传输：`Wire.encode` / `decode`、`toJson` / `fromJson`，TS 的 `toWire` / `fromWire`；`WireCodec` 注册自定义值类型。职责只是传输，不放别的 JSON 工具 |
| query（公开） | `kenconnet666/query` | 原来的持久层，2026-10-27 起不再用，**暂不维护**（PR #33 json-wire 的迁移留着没合）；开发库已搬进本仓库的 `infra/mysql` |
| vue-tsx-pro（公开） | `kenconnet666/vue-tsx-pro` | 前端技术栈与通用底座：画布 `@zerodep-dev/vue-tsx-pro-ui/diagram`、条件构造器 `ConditionBuilder`、人员与组织选择器 `PeoplePicker`、`QueryTable` + `useTableQuery`；请求层自带一份 typed JSON 的还原（`createHttpClient({ wire: { decimal: Decimal, dates: 'objects' } })`） |

**0003 的 B**：流程组件（流程图、设计器、审批面板、收件箱）在本仓库按模型包重做，界面风格与 vue-tsx-pro 新的画布示例一致
（虚线泳道、点阵、`DiagramNode`）；vue-tsx-pro 的 `/workflow` 子路径（FlowDiagram、FlowDesigner、ApprovalActions、流程定义与校验）2026-10-26 已删除，
旧实现在 vue-tsx-pro 的提交 `f7bfdc2e` 的 `packages/ui/src/workflow/`，重做时可以参考（契约的类型、校验规则、排布、文案）。前端方案在 [docs/frontend.md](docs/frontend.md)。
组件库暂时缺的能力，本仓库像 vue-tsx-pro 的文档站那样用 `@zerodep-dev/vue-tsx-pro-tsx` 的 Vite 预设直接编译自己的 TSX 补上，不必等组件库发版。
模型包定稿时把流程定义条件里的 `var` 对齐成 `field`（与组件库的 `Condition` 同形）。

改契约（定义 JSON、运行时类型、条件树、表格请求、typed JSON）要几个仓库一起看：前端组件在 vue-tsx-pro，后端与模型包在本仓库，线上格式在 json-wire。

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
  例外（2026-10-27 用户定）：存储不再做成可替换的接口，引擎直接用 easy-query；替换口留在业务层（组织、解析器、守卫、动作、拦截器、监听）与 easy-query 自己的扩展点。
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
| 仓库 | `IdeaProjects\flow`、`json-wire`、`vue-tsx-pro`、可选 `query`（已不用）、 `easy-query`（dromara，读源码用） | `git clone https://github.com/kenconnet666/<名字>` 放在同一目录；json-wire 先 `mvnw install`，TS 侧 `pnpm install && pnpm build` |
| 参考仓库（只读） | `IdeaProjects\flow-references\`（warm-flow、flowlong、flowlong-designer） | 需要时重新浅克隆；不影响构建 |
| Claude 项目记忆 | `C:\Users\lionheart\.claude\projects\C--Users-lionheart-IdeaProjects-<仓库>\memory\` | 含真实业务项目的需求要点与资料位置（所以不进仓库）。按需手动拷到新电脑对应目录（目录名随用户名与路径变）；没有它也能继续，长期要求已抄在各仓库的交接文档里 |
| 开发数据库 | WSL Debian 里的 Docker（本仓库 `infra/mysql/compose.yaml`，库 flow_demo 与 flow_it），密码在已忽略的 `infra/mysql/.env` | 照 compose.yaml 开头的步骤建 `.env`、`docker compose up -d --build`，示例库再在 `typescript/apps/demo` 下 `pnpm seed`；WSL 空闲会关机，跑实库测试前挂一个 `wsl -d Debian -- sleep 14400` |
| 工具 | JDK 17（query 是 Java 17）、Node 24、pnpm（vue-tsx-pro 用 12、query 用 11，由 `packageManager` 固定）、`gh` | 新装；vue-tsx-pro 首次跑浏览器测试前 `pnpm --filter @vue-tsx-pro/host-demo exec playwright install chromium firefox webkit` |
| 用户级环境变量 | `GITHUB_TOKEN`、`CONTEXT7_API_KEY`、发布用的 `NPM_TOKEN` | 在新电脑重新设置 |

## 6. 下一步

主线是流程引擎（用户 2026-10-26）：query 与组件库只在引擎用到时补，不单独开新活。顺序按 0004 "已定"里的"顺序"一行，② 已完成。

第 ③ 步最小链路的拆分（2026-10-26，用户让先做下去）。下表是当时的记录：其中的 `XxxStore`、`FlowDatabase`、query、Node 引擎（④）在 2026-10-27 的重做里已换掉或删掉，见表末的"重做"一行。

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
| ⑤（已完成，2026-10-27） | 流程设计器 `FlowDesigner`（flow-ui/admin/designer）：建在组件库的 Diagram 上，左边节点面板（点一下加在选中节点后面并连上，或拖到画布上），画布上拖动、从端口连线、改接、Delete 删除、Ctrl+Z / Ctrl+Shift+Z 撤销重做（组件库 createHistory）、自动排版；右边属性面板——流程（标识、名称、表单、实例标题、摘要列、撤回与找不到处理人的规则）、节点（通用加各类型的常用设置：处理人用 PeoplePicker 选人角色部门、会签、处理结果、时限、允许的操作、表单字段权限；自动、定时、等事件、抄送、子流程、结束）、连线（结果、条件用 ConditionBuilder 取表单字段与流程变量、默认出边、退回提级、文字），节点面板里"连到……"是不用拖的连线做法；下面实时列出模型包校验出的问题，点一条选中出问题的地方。编辑操作是纯函数（editing.ts，单测），位置写进定义的 layout。`FlowDefinitionEditor` 分"设计 / JSON"两个页签，互相转换；模型包的处理人来源可带 `name`（名字快照，0005 的 3.2）| flow-ui 单测 26（编辑操作 9 个）；浏览器里用设计器从骨架画出"报销"（删线、加条件分支与财务审批、选财务角色、金额 ≥ 5000 的条件、默认出边），后端校验通过、发布，两个金额分别走两条路结束，实例流程图按设计器的布局显示 |
| ④c（已完成，2026-10-27） | `@zerodep-dev/flow-nuxt`（typescript/packages/nuxt）：`createFlowHandler({ engine, actor, prefix })` 是 h3 的处理函数，路径、参数、响应体同 Spring 的 `FlowHttpController`（请求体的读法同 `RequestBody`，带 `Idempotency-Key` 的命令幂等）；`startFlowJobs(engine, { intervalMs, retentionSeconds })` 同 `FlowJobPoller`。flow-core 加 `migrateFlowSchema(driver)`（query 的迁移器，历史表 `flow_migration`）与 `MYSQL_SCRIPTS`：建表脚本由 `packages/core/scripts/sync-sql.mjs` 从 Java 的 resources 生成进源码，单测比对两边。Nitro 示例后端 `typescript/apps/demo-server`（同 examples/spring-boot，本机 `.claude/run-node-backend.sh`）。顺带修 Java：外部事件的结果改成与命令同形（原先是 `created`，前端读 `workItems`），发布定义、表单返回的编号也走 typed JSON | TS 实库 32 个（场景 29 + HTTP 3）、单测 122、Java 154 全部通过；浏览器里示例前端接 Node 后端走完一单采购（发起、财务认领通过、总经理同意），异常处理重试（Node 的轮询重做、再失败）与跳过，流程与表单管理页；Node 后端读写 Java 建的 flow_demo，同一个实例的流转记录里两边的步骤接得上；`nitro build` 的产物对空库建表 7 个版本 |
| 对象建模（已完成，2026-10-27） | 实体补齐关系导航：流程、表单的 `versions`、`latest`，流程版本的 `process`、`form`，实例的 `definition`、`workItems`、`events`、`formData`、`parent`、`children`，待办的 `instance`、`candidates`、`parent`、`children`，抄送、流转记录、作业的 `instance`；表单版本的源码、编译结果默认不读（`autoSelect = false`）。引擎沿导航读：`DefinitionCache.processes()` 连同 `latest` 与它的 `form` 读流程，异常列表 include 作业的实例与待办 | Java 154 全部通过 |
| ⑥ 表单设计器（第一版，2026-10-27） | form-tools 的 `design/`：`parseDesign` 把源码解析成字段列表与组件树（路径 `0.2.1`、源码区间），`edit.ts` 的插入、删除、移动、改属性、换控件、改字段、加删字段、改名、补导入都只改受影响的那一段；`registry.ts` 登记认得的组件（`registerFormComponent` 给接入方）。admin 的 `FormDesigner`：左边组件与字段列表（没摆的能拖进画布），画布照常渲染表单、上面盖操作层（点选、拖放、插入线），右边属性面板分字段（改清单，显示与必填条件用 ConditionBuilder）与控件（改 JSX 属性、换控件）两组；预览模式去掉操作层。`FlowFormEditor` 多一种"设计"写法，与代码共用一个代码编辑器（设计时藏起来），撤销重做就是它的历史（组件库 CodeEditorHandle 加了 `edit`、`undo`、`redo`）。顺带：`form.Field` 按字段类型给控件补没写的属性（controls.ts：decimal、日期、选项、人员、长度），代码表单只写 `<Select />` 就对上强类型；FlowForm 加 `keepPrevious`，连着换代码时只用最后一次的结果 | form-tools 设计单测 10 个，UI 共 38；浏览器里走过选中改标签、加分组、往分组里加金额与选项字段、拖动换位置、改选项后预览里的下拉跟着变、三步撤销与重做；发起页金额 350.25 照样存成 typed JSON |
| 整理（2026-10-27） | 用户要求：不用 record、final class，少用 Object、Map 这类临时类型；按职责分包、方法与注释写清楚。record 全部改成 Lombok 类、访问方法统一 getX（`Actor`、`ProcessNode`、`FlowException` 的接口也改了）；`engine` 拆出 `node`（TaskBehavior、GatewayBehavior、TimerBehavior、ServiceBehavior、ReceiveBehavior、CcBehavior、CallBehavior）、`handler`、`query`（InboxQueries、WorkItemQueries、InstanceQueries、ProcessQueries、FormQueries、StatsQueries）等子包；`FlowEngineBuilder` 单独一个类；前端 `admin/designer` 改名 `process-designer` | 行为不变，Java 与场景全部通过 |
| ⑦ 把引擎做完整（2026-10-27） | 自动跳过（发起人、上一步处理人，记 `node.skipped`）与通知渠道 `NotificationChannel`（引擎按流转记录算出种类与默认收件人，定义的 notify 改模板、加人，提交后才发）；暂存 `saveDraft`（存在待办的 draft 列）；设计中的草稿 `flow_draft`（V8，版本列防覆盖，发布后删）；表单后端代码（`FormHandler`：load、validate、compute、submit、actions，发布时用 javax.tools 编译，按表单版本缓存）；批量认领与处理、申请改派、管理员改数据；子流程在父实例暂停期间结束、恢复时补上往下走；工作日历表 `flow_calendar`（V9，`TableWorkCalendar` 成为默认）与维护页；我已办、我发起的、抄送我的按流程与时间筛；实例迁移到另一个版本（`MigrateCommand`，节点对应关系、类型要相同）；外部参与方看的进度 `ProgressView` / `FlowProgress`；统计（`StatsQueries` 分组计数，V10 加了按处理时间的索引）与 `FlowStatistics`；流程、表单的导出、导入、复制（走发布接口，两个列表合成 `CatalogList`）；定义编辑器"与已发布版本对比"（模型包的 `diffProcess`） | 场景 auto-skip、save-draft、batch-reassign-change 等，实库 DesignDraftTest、FormCodeTest、CalendarTest、AdminOpsTest、NotificationTest；Java 模型与核心 121、实库 43，TS 120，UI 40 全部通过；浏览器里走过各个页面 |
| ⑥ 表单设计器第二步（2026-10-27） | 单选组 RadioGroup、多选组 CheckboxGroup（form.Field 按清单补选项与值的转换，与 Select 共用）；预览里填的值在重新编译后留着、"只读预览"；前端代码的问题（语法、清单写法）与后端代码的编译问题点一条跳到代码位置（组件库 CodeEditorHandle 加 `goTo`）；发布时存前端的编译结果与编译器版本（组件库 browser-compile 导出 `COMPILER_VERSION`），FlowForm 只在版本一样时直接执行存下的代码 | UI 单测补了编译器版本的两种情况；浏览器里走过加单选组、预览选中后重编仍选中、只读预览 |
| ⑧ 补齐小缺口（2026-10-27） | 开发 MySQL 搬进本仓库的 `infra/mysql`（query 暂不维护；`live-env.sh` 设实库测试与示例后端的连接，主机取 WSL 地址）；待办已读（`readWorkItem`，收件箱标"新"，换处理人时清掉）；实例详情的流程变量只给流程管理员（`OrgDirectory.administrators`）；留言 @ 人（`mentions`，通知 `mentioned`） | 场景 read-work-item、call-process 加两步；实库 NotificationTest 加一例 |
| ⑨ 引擎能力补全（2026-10-27） | 发起前存草稿（draft 实例，`saveStartDraft`、`discardStartDraft`、`startDrafts`，`start` 带 `draftId`；前端"存草稿"与 `FlowStartDrafts`）；预测下一步（`NextSteps`，结果与发起页带 `next`，审批面板写"通过 → 总经理审批（总经理）"）；扩展口 `CustomNode`（x- 节点，当场做完或等事件）、`CountersignRule`、`FieldType`（发布认得、提交暂存时查）、`TodoQueryCustomizer`（待办连接入方的表，`biz.` 参数、`TodoItem.business`） | 场景 start-draft、custom-node、field-type，purchase-views 加 next；实库 TodoCustomizerTest；Java 实库 49、UI 41 |
| ⑩ 组件库收拢、整理、归档（2026-10-27） | 组件库收进四样通用的：`scopeHttp`（带前缀的请求简写，引擎客户端建在它上面）、`useAutosave`（设计草稿的自动保存）、diagram 的 `layoutLayers`（流程图的分层排版）、`dayRangeBounds`（列表筛选换成 DateRangePicker）。列表与组件库表格对齐：后端 `Page` 的 `size` 改名 `pageSize`；`sort` 用 formatSorts 的写法（`SortOrder`，每个列表一份白名单，easy-query 的 orderByObject）。整理：文案按语言分文件（lang/zh-CN.ts、en-US.ts）；HTTP 接口按路径分四个控制器（`FlowHttpSupport` 共用）；表单设计器的画布位置挪进 canvas.ts；package-info 删掉，包的分工写进 flow-core 的 README。归档：V11 的 `*_archive`，`FlowEngine.archive`，`flow.archive.*`；实例详情自动读归档，列表带 `archived` | 实库 ArchiveTest、admin-queries 加了排序；组件库补了 http、autosave、layout、date 的测试；数据量探针 `QueryVolumeProbe`（`-Dflow.perf=true`） |
| ⑪ 数据量与多租户（2026-10-27） | 探针造 5 万实例、49 万流转记录：个人的列表与详情 10–55ms，都走索引；管理端看全部补 V12（租户, `started_at`），列表 80ms → 不到 1ms；环节统计改在库里分组算；流转记录不分表（0007 第 6 节）。多租户隔离的专项测试；`PermissionPolicy` 的操作名清单补全。并发测试查出一个真问题：几个人同时会签同意时实例停住不走（可重复读的快照），改成命令的事务用读已提交，加入外面的事务时核对 | 实库 TenantIsolationTest、ConcurrencyTest（同时认领、连点处理、同时会签、撤回与处理同时到、外面是可重复读时报错）、AdminOpsTest 补了停留与超时比例 |
| ⑫ 休假代理（2026-10-27） | 对照降本项目资料（单机降本主流程、DMC 降本、需求定义方案）逐条核对能力清单，只有"代位人"（Q-15）是缺的通用能力：`OrgDirectory.standIn` 默认没有，待办要落到不在岗的人时换成代理人（顺着找，绕回来就停），待办记 `on_behalf_of_id/name`（V13，归档表同加），到达记录的 `standIns`；被代理的人算参与过；前端处理人写"李四（代 张三）"（`runtime/parts/holder.ts`），流转记录写"李四 代 张三" | 实库 StandInTest（直接落到人、代理链与绕回、会签参与人）|
| ⑬ 扩展能被设计器认得（2026-10-27） | 扩展目录：注册的扩展覆盖 `info()`（`ExtensionInfo`：显示名、说明、参数 `ExtensionParam`、结果、计算值的类型），`FlowEngine.extensions()` / `GET /queries/extensions` 列出；设计器的解析器、动作、守卫、会签规则从下拉选，参数按声明画控件（`process-designer/extensions.tsx`），自定义节点进节点列表、属性按声明画，动作与自定义节点的出边能选声明的结果，校验认得注册的名字。计算值 `fn.名字`（`ComputedValue`）原先只在文档里，现在实现了。节点的业务信息 `flow.designer.node-meta`。`flow.forms.server-code: false` 关掉在线后端代码。顺带修：自定义节点的出边写结果被校验拦下（`isBranching`）；运行时流程图上自定义节点、动作结果显示键而不是名字；新建流程改了标识后草稿误报"别人刚保存过"（旧标识的草稿留着、带着旧行的版本号存新行） | 实库 ExtensionCatalogTest、规格 custom-node-outcomes、UI designer-extensions；浏览器里用设计器搭出"外部核验 → 按金额档位分支 → 上级审批（解析器往上 2 级、金额上限检查、业务状态）→ 推送"，发布后三单：25000 落到总经理并走完、200 万被检查拦下、300 直接走完 |
| ⑭ 发件箱、定义格式版本、契约测试套件（2026-10-27） | 发件箱：监听的 `RELIABLE`、渠道的 `reliable()`，`EventDispatcher.outbox` 算出要投的（`OutboxEntry`），Execution 同事务记成 `deliver` 作业，提交后经 `engine.onOutbox` 叫醒作业轮询（`FlowJobPoller` 登记），`DeliveryJob` 投、失败重试 5 次、进异常处理（重试、放弃投递），实例结束时不删；示例的通知渠道改成可靠的。定义格式版本：`schema`（Java `DefinitionSchema`、TS `schema.ts` 的 `parseDefinition`），比引擎新的报 `schema-newer`。契约测试套件 `java/flow-test-kit`：`FlowContractTest` / `FlowContractChecks`（表、组织架构、扩展的声明、冒烟一单），示例的 `FlowContractIT`。顺带修：同时实现监听与渠道的类 `name()` 冲突（改成 `listenerName()`、`channelName()`）；附件没传完就提交会悄悄丢掉（表单校验不放行，提示"附件还在上传"），端到端里"病假带证明"后端刚启动时失败就是它 | 实库 OutboxTest、ContractKitTest，模型 DefinitionSchemaTest 与 TS schema 单测，UI 附件上传中的校验；示例 FlowContractIT 6 项通过；浏览器里通知从作业线程即时发出，异常处理页的"投递（发件箱）"放弃投递 |
| ⑮ 发布前整理（2026-10-27） | 用户定：① `FlowEngine` 只留传操作人的方法（158 → 82 个），`FlowEngineBuilder.actorProvider` 去掉，`ActorProvider` 只给 HTTP 接口（控制器经 `ObjectProvider<ActorProvider<?>>` 取）；② 接入方实现或用到的接口全部收进 `core.spi`（原 identity、extension、listener、notify、form、calendar 包与 `FormValues`、`JsonFields`、`CommandInterceptor`），`Extensions`、`TableWorkCalendar`、`InstanceDisplay` 进 `engine`，引擎内部的 JSON 状态进 `engine.state`，`CommandResult`、`BatchResult` 进 `view`；③ `ProcessVersionEntity` 改名 `DefinitionEntity`。内部：`Extensions` 改用 builder 收集，失败原因合成 `FlowErrors.rootMessage`。前端：`client/types.ts` 拆成 runtime、design、admin 三份（原文件汇总导出），`runtime/parts` 收小工具，`admin` 分 catalog、editor、ops | Java 模型 125、核心单元 7（新）、实库 93，TS 124，UI 60，端到端 9（新增扩展 4 个）；浏览器里管理端各页、后端代码骨架用新包名试编译通过 |
| 重做（已完成，2026-10-27） | 用户定：① 删掉 Node 后端（TS 引擎、flow-nuxt、Nitro 示例后端）；② 持久层换成 easy-query——去掉 12 个存储接口，`entity` 包的实体写库、`view` 包的 VO 读给前端，待办行、抄送行用 `selectAutoInclude` 映射成带 `instance` 的对象树，租户与修改时间交给拦截器；③ wire 迁成独立仓库 json-wire，接口的 typed JSON 由接入方集成；④ 表单的值是强类型（json-wire 还原，模型包按类型校验，spec 与场景文件改成 typed JSON），前端时刻是 ZonedDateTime | Java 154（模型 121、实库 33）、TS 120、UI 26 全部通过；浏览器里走过发起（金额 350.25 原样存成 typed JSON）、财务处理、我已办、流程监控；顺带修了自动排版的数字控件把小数对齐成整数的问题 |

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
11. **撤回整步（withdrawStep）用快照回滚**：每个实例只留最近一步的快照（`flow_step`），所以只能撤回最后处理的那一步；这一步之后进过自动节点（服务、子流程、等事件）就不能撤，因为它们的副作用回滚不了；实例结束了也不能撤。
12. **作业**：工作日默认周一到周五、按 UTC 的日期（Spring 用 `flow.calendar.zone` 改时区，节假日、调休在 `flow_calendar` 表里维护（管理端"工作日历"），接入方也能换成自己的 `WorkCalendar`）；`until` 只写日期时算那天结束；待办办完了不去删它的作业，作业到点看到待办不再打开就跳过（实例结束时才统一删）；实例暂停时作业往后推一小时；失败重试 3 次，间隔 1、4、9 分钟；超时默认只记一条"超时"（`mark`）。
13. **转办、委派、加签的待办沿用原来那一份的截止时间**，不重新计时。
14. **子流程**：子实例的发起人是父实例的发起人（出现在他的"我发起的"里），流转记录上的操作人是触发调用的人；父实例暂停时子实例结束不推进父实例，恢复时补上往下走（`CallBehavior.resumeWaitingCalls`）。
15. **异常不生成人工待办**：0005 原先写"给管理员生成异常处理待办"，改成管理端的异常列表加实例详情上的一块（同 Camunda 的 incident、Flowable 的 dead-letter job）——待办挂在自动节点上要另造一套表单、结果、处理人规则，收益不大；通知管理员交给接入方监听 `job.failed`。跳过时结果必须是出边上写的；暂停的实例要先恢复才能重试或跳过。
16. **退回时选节点不进别的并行区域**：能选的限于当前节点所在区域之内或之外的上游，旁边的分支、已经汇合过的区域里的节点不给（进去了汇合等不齐）；一个能选的都没有时按钮不给；会签时用让节点结束的那个人选的目标。
17. **按命令替换处理类用拦截器做**：不另设 `CommandHandler<C>`，拦截器认出命令后不调 proceed 就是换掉标准行为；发布定义、发布表单、停用启用暂不过拦截器（不是命令记录，审计可以先用 PermissionPolicy 记）。
18. **表单发布时存编译结果**：表单编辑器发布前在浏览器里编译，连同组件库的 `COMPILER_VERSION` 一起存；审批页版本一样时直接执行，不一样（组件库升级改了编译结果的格式）时按源码重编，不需要批量重新发布。
19. （已作废：Node 后端删掉了）**Node 版建表用 query 的迁移器**：历史表 `flow_migration`，与 Java 的 Flyway（`flow_schema_history`）互不认，同一个库只由一边建表（示例后端连 Java 建好的库时设 `FLOW_MIGRATE=false`）。脚本生成成 TS 模块而不是运行时读文件：应用经 Nitro、Vite 打包后 `import.meta.url` 变了，按相对路径找不到。
20. （已作废）**flow-nuxt 不做 Nuxt 模块**：只给 h3 的处理函数与作业轮询，接入是一个路由文件（`server/routes/flow/[...path].ts`）加一个插件；模块（nuxt.config 的 modules 自动注册）多一层配置、收益小，用户要时再包。
21. （已作废）**Node 后端的时间不带 timestamptz 标记**：引擎里本来就是 ISO 文字（同 Java `Instant` 的写法），前端请求层默认也按文字用，所以响应体只给编号记 bigint；两个后端的 data 除了 `$types` 里少这几项外相同。
22. （已作废）**取不到操作人**：`actor` 抛 FlowError（如 `flow.forbidden`）时同样是 200 加错误码（同 Spring 示例）；抛 h3 的错误（`createError({ statusCode: 401 })`）原样交给 h3。

23. **处理人记名字快照**：设计器选人时在来源上记 `name`（`{ "role": 1, "name": "财务" }`），只给人看，引擎不读；改了名字的人要在设计器里重选一次才更新。
24. **设计器只做常用设置**：提醒、超时处理、无人认领、跳过规则、通知、泳道、流程变量、发起人范围、走线时改变量（effects）在 JSON 页签里写，设计器原样保留；改标识要在输入完（失焦、回车）才生效，并把连线、布局、"同某节点"一起改过去。
25. **定义按不可变的方式改**：设计器每改一步换一份新的定义对象（父级用 `_state.raw` 存），撤销记引用；属性面板一律写 `onUpdate:_value`，不让组件直接改对象属性。
26. **自动跳过只在顺着流转到达时**：处理人是发起人或上一步的处理人、节点有"同意"类的结果时才跳；退回、重新提交、提级、跳转来的不跳；会签、落到收件箱的不跳。
27. **通知只在提交后发、按流转记录算**：引擎不自己发消息，只算出种类（到达、退回、催办、快到期、超时、抄送、结束）与默认收件人交给 `NotificationChannel`；渠道抛错只记日志，不影响流转。
28. **暂存不查必填、不推进、不记流转记录**：只收这个节点能改的字段并按类型、范围查；收件箱里的待办暂存时先认领给自己。
29. **设计草稿每个流程（表单）一份、大家共用**：带版本列，别人先存过时报 `flow.concurrent-modification` 并带最后修改人，由人选择载入他的；发布成功时同一事务里删掉。
30. **表单后端代码不广播、不预热**：按表单版本 id 缓存编译出的类，版本不可变所以不用失效，集群各节点用到时各自编译；执行顺序是清单校验之后 validate → compute → submit，都在流转的事务里。
31. **批量操作各自一个事务**：一条失败不影响别的，结果逐条带成败与错误码；批量同意按每个待办第一个"同意"类的结果。
32. **实例迁移**：只能迁到同一个流程的别的版本；正停着的节点在新版本里要有（同名或写了对应关系）且类型相同；待办、等待、作业里的节点一起改名，表单数据换到新版本的表单；会签、并行的内部状态按节点名平移，不做别的转换。
33. **统计不建汇总表**：计数在库里分组算，停留时长、超时比例取那段时间处理完的待办在内存里算（默认 30 天）；数据量大了再考虑汇总表或交给 BI。
34. **导入、复制都走发布接口**：不另开导入接口，后端照常校验；复制是取最新版本换标识与名字发布成新的一个，流程复制不连带复制它的表单。
35. **流程变量只给流程管理员**：按 `OrgDirectory.administrators` 判（处理人为空时转的那批人），不另设管理员角色；默认谁都看不到。
36. **发起前的草稿是 draft 实例**：从草稿发起不另开命令，`start` 带 `draftId`；按发起时最新的版本走；草稿不算进"我发起的"、管理端搜实例（不指定状态时）与统计。
37. **predict 并进视图**：不另开查询，待办详情每个结果、发起页各带 `next`；只用定义与现在的值推，不跑自动节点，停在自动、调用、等事件这类节点上为止；处理人只列看得出来的（指定的人、发起人、同某节点的上一位）。
38. **自定义节点能等事件**：等的事件也由 `signal` 送来，和等事件节点共用 `flow_wait`；自定义会签规则的参与人一起处理（不支持依次）。
39. **注册的字段类型只查顶层字段**：明细表里的列用了注册类型时后端不查它的值。
40. **待办连表给 easy-query 的代理**：`TodoQueryCustomizer.filter` 直接拿 `WorkItemEntityProxy`，接入方用导航或 exists 子查询；筛选参数是字符串（HTTP 的 `biz.` 参数），业务列是 Map（列由接入方定，没法强类型）。
41. **排序只认白名单**：列名就是实体的属性名，各列表一份（我发起的、管理端搜实例、我已办、抄送我的）；我的待办是两段合并，不支持排序；默认顺序接在用户的排序后面，翻页稳定。
42. **归档不和热表合在一页**：列表带 `archived=true` 时只看归档表；实例详情例外，热表里找不到就读归档表（旧链接照样打开）。归档不分租户，一批 200 个、一批一个事务。
43. **目前只支持 MySQL**（2026-10-27 用户定）：建表脚本、归档（`CREATE TABLE … LIKE`、`information_schema`）、实库测试都只按 MySQL 做，别的库以后再说。
44. **命令的事务是读已提交**：引擎自己开的事务设成读已提交；加入接入方的事务时查 `@@transaction_isolation`，不是就报错。
45. **谁能看实例（ViewPolicy）默认不改变宽松原则**：参与过的人总能看，别人看 `instances.search` 的权限；权限默认放行所以不配时谁都能看，接入方收紧管理端权限后自动只剩参与过的人（用户离开前让我自己定，2026-10-27）。
46. **休假代理只问接入方，不建代理表**：`OrgDirectory.standIn(用户, 流程, 时间)` 默认没有；待办生成时问（直接落到人、收件箱里点名的人、会签参与人，转办与加签的目标也算），记 `on_behalf_of`（V13）与到达记录的 `standIns`。休假与代理关系是人事数据，各家系统已有，引擎不另存一份；已在手里的待办不自动移走（2026-10-27，对照降本项目资料的 Q-15 补的通用能力）。
47. **扩展的设计器信息跟着扩展走**：显示名、参数、结果写在扩展自己的 `info()` 里（不另建登记表、不放配置文件），扩展和它的说明一起改、一起发布；没写 `info()` 的扩展照常能用，设计器退回到写名字、写 JSON。节点业务信息的字段不属于某个扩展，放在配置里（2026-10-27）。
48. **发件箱复用作业表，不另建 outbox 表**：要投的内容记成 `flow_job` 的 `deliver` 作业，抢占、重试、异常处理、管理端都是现成的；提交后叫醒轮询保证及时，进程挂了靠轮询兜底。投递只记编号、投的时候读库，所以通知里的实例与待办是投的那时的样子（2026-10-27）。
49. **定义格式版本与流程版本分开**：`schema` 只在 JSON 格式改动（改名、换结构）时加，只加字段不加；读的时候升级，不改库里存的原文（2026-10-27）。
50. **契约核对在接入方的测试里跑**：`flow-test-kit` 只依赖 flow-core 与 JUnit API，接入方用自己的引擎、组织架构、权限跑；冒烟流程跑完停用，不删（2026-10-27）。
51. **公开接口按"接入方要碰的"划线**：`spi` 是接入方实现或用到的全部接口，`FlowEngine` 与 `command`、`view` 是调用面，`entity` 给连表用，其余（`engine`、`db`）是内部；当前操作人不归引擎管，由调用方传入（2026-10-27 用户定）。

下面是还没做的、按优先级（阶段三起，2026-10-27 的规划）：

0. 阶段三已完成（⑩⑪）。阶段五：端到端测试（apps/demo/e2e，2026-10-27 完成）、手机宽度（各页 375px 无横向滚动，完成）；阶段六：接入指南（docs/integration.md，完成）、依赖换版本号（等发布）。
   成熟可用还要补的（用户同意继续做，只做 MySQL）：
   作业积压、失败重试、归档进度的指标（2026-10-27 完成：engine.ops() 与 FlowMetrics）；CHANGELOG 与升级说明（2026-10-27 完成：CHANGELOG.md）。
1. 表单设计器余下的：标签页（2026-10-27 完成：FormTabs，往里拖分组、每个分组一页，设计时摊开排）、明细表 form.List（2026-10-27 完成：代码表单里一行 <form.List name="lines" />，设计器面板里有"明细表"、属性面板改列）、附件控件（2026-10-27 完成：FileField 包组件库的 Upload，接入方 provideFlowFiles 给上传与打开；示例后端 ExampleFiles）；
   编译放 Worker（2026-10-27 完成：接入方 provideFormCompiler(createCompileClient(() => new CompileWorker()))，没给时在当前线程编）；预览时套用某个流程节点的权限（2026-10-27 完成：/queries/forms/{key}/usages，设计器预览里"按节点看"）。
2. 发布 json-wire、vue-tsx-pro 0.2 到 npm / Maven（用户手动），之后本仓库从 `link:` 换成版本号。
