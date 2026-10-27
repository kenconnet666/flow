# 变更记录

版本号同 Maven 的 `0.1.0-SNAPSHOT` 与 npm 的 `0.1.0-dev`。还没发布；发布后每一版在这里写"新增、改了、要注意的"。

## 0.1.0（未发布）

第一个能接进业务系统用的版本。只支持 MySQL 8。

### 有什么

- **流程**：开始、人工、排他与包容与并行网关、定时、自动（动作）、等外部事件、调用子流程、抄送、结束，加接入方注册的自定义节点；
  会签（全部、依次、任一、按比例、按票数，加注册的规则）；退回到任意节点、按轮次的循环；处理人六种来源加上一步选、自定义解析器；
  截止时间、提醒、超时自动处理、无人认领转负责人指定，按工作日历算；发布前校验，版本并存，运行中的实例可以迁移到新版本
- **协作**：认领、退回收件箱、转办、委派、休假代理、前后加签与减签、申请改派、催办、留言与 @ 人、抄送与已读、撤回整单、处理人撤回刚处理的一步、
  批量认领与处理、发起前存草稿、处理中暂存
- **表单**：字段清单（文字、数字、金额、是否、日期、时间、选择、人员、附件、明细表），节点上的字段权限，显示与必填条件，
  前后端同一套校验；代码表单（TSX，浏览器里编译）与后端代码（Java 的 FormHandler：校验、计算、写业务表、给前端的接口）；
  表单设计器（拖放、属性面板、明细表的列、附件、预览、自动保存草稿）
- **查询**：我的待办（含角色、部门收件箱）、待我指定、我已办、我发起的、抄送我的，带筛选与按列排序；实例详情、流转记录、流程图；
  给外部参与方的进度；管理端的实例监控、异常（失败的作业）处理、统计
- **管理员**：终止、暂停与恢复、跳转、改派、改数据、重试与跳过作业、迁移版本
- **运维**：作业轮询（集群里多个实例抢占）、命令幂等（Idempotency-Key）、归档（`*_archive`）、多租户、Micrometer 指标、
  发件箱（可靠的监听与通知渠道：同事务记下、提交后投、失败重试、进异常处理）、契约测试套件（flow-test-kit）
- **接入**：Spring Boot 自动配置（建表、HTTP 接口、作业、指标）；`PermissionPolicy`、`ViewPolicy` 与十来个扩展点，
  扩展用 `info()` 告诉设计器显示名、参数与结果（扩展目录），节点的业务信息字段可声明，在线写表单后端代码可关掉；
  Vue 3 的组件（`@zerodep-dev/flow-ui`），建在 vue-tsx-pro 上。见 [docs/integration.md](docs/integration.md)

### 要注意的（与之前的开发版相比）

这些是 2026-10-27 改的，之前按开发版接过的要跟着改：

| 改了什么 | 要做什么 |
| --- | --- |
| 列表的分页参数与返回 `size` 改名 `pageSize`；排序参数 `sort`（`-startedAt,title`），只认每个列表的白名单 | 自己调接口的改参数名；flow-ui 的组件已经跟上 |
| 命令的事务要读已提交 | 在自己的 `@Transactional` 里调命令时写 `isolation = Isolation.READ_COMMITTED`，否则报错 |
| `ViewPolicy`：实例详情、待办详情、表单的 actions、评论之前问"能不能看" | 默认参与过的人加上有 `instances.search` 权限的人；没收紧过 `PermissionPolicy` 的不受影响 |
| 多租户在 Spring 里声明 `FlowTenant` Bean | 以前自己组装引擎的可以改回用自动配置 |
| 建表脚本到 V13（V11 归档表、V12 管理端看全部的索引、V13 待办的"代谁处理"） | Flyway 照常执行；自己用别的迁移工具的把新脚本按序加进去 |
| 接入方实现或用到的接口都搬进 `org.dpdns.zerodep.flow.core.spi`（原来的 `identity`、`extension`、`listener`、`notify`、`form`、`calendar` 包没有了；`FormValues`、`JsonFields`、`CommandInterceptor` 也在这里） | import 改成 `...core.spi.*`；表单后端代码（在线写的 Java）的 import 一样改 |
| `FlowEngine` 每个方法都要传操作人：去掉了不带操作人、取"当前操作人"的那一半；`FlowEngineBuilder.actorProvider` 没有了 | `engine.start(cmd)` 改成 `engine.start(user, cmd)`；`ActorProvider` Bean 照旧，只给引擎的 HTTP 接口取登录的人 |
| `CommandResult`、`BatchResult` 搬到 `view` 包；`ProcessVersionEntity` 改名 `DefinitionEntity`（表还是 `flow_definition`） | 改 import 与类名 |
| 可靠投递的监听、渠道按名字找回：`FlowListener.listenerName()`、`NotificationChannel.channelName()` | 只有一个类同时实现两者、又想自己起名时要覆盖 |

## 升级说明（以后每一版都按这些）

- **建表脚本只加不改**：`flow-core` 的 `db/flow/mysql/V*.sql` 执行过的不再改，变化都写成新的一版（加列、加索引、加表）。
  `flow.flyway.enabled` 时启动就执行；用别的工具的按版本号顺序执行没执行过的。
- **运行中的实例留在发起时的流程版本上**：发布新版本不影响已经在走的实例，新发起的才用新版本。要把在走的实例换到新版本，
  管理端（或 `engine.migrate`）按节点写对应关系迁移；新版本里删掉了实例当前所在的节点时必须写对应关系。
- **表单版本同理**：实例按发起时的表单版本显示与校验；新表单版本只给新实例。
- **前后端一起升级**：模型包（流程定义、表单、校验规则、错误码）前后端各一份、规则一致，两边用同一个版本。
- **定义的格式版本**：流程定义 JSON 的 `schema` 是格式本身的版本。改格式时加一、两边各加一步升级，旧的已发布版本读之前自动升上来；
  比引擎还新的定义（更新的引擎发布的）读不了，报 `schema-newer`，先升级引擎。
- **升级后跑一遍契约核对**：接入方项目里继承 `FlowContractTest` 的那个测试（flow-test-kit）。
- **先在测试库跑一遍**：`./mvnw test -pl java/flow-it`（连真实 MySQL）与 `pnpm -C typescript/apps/demo e2e`（真实浏览器）。
