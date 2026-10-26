# 0006 引擎对外的接口

- 状态：已定（用户 2026-10-26；主键同日改为数据库自增，见 6.1）
- 相关：方向见 [0001](0001-engine-direction.md)（第 5 条扩展点在这里展开），能力见 [0002](0002-capabilities.md)，定义见 [0005](0005-definition.md)，表单见 [0004](0004-forms.md)
- 起点：vue-tsx-pro 提交 `f7bfdc2e` 的 `packages/ui/src/workflow/runtime.ts`（实例、待办、流转记录的类型）
- 原则：**标准行为都能替换**（0001 的定位）。每一块都写明标准行为，以及怎样换成自己的

## 1. 总体（已定，用户 2026-10-26）

| 项 | 做法 |
| --- | --- |
| 三层 | **命令**（改状态：发起、认领、处理……）、**查询**（读：待办、实例详情……）、**扩展**（接入方的实现：解析器、动作、存储……） |
| 两个后端 | Java（Spring Boot）与 TS（Nuxt）的命令、查询、扩展点**同名同义**；参数与返回值是同一份 JSON 形状，两个后端跑同一套场景用例 |
| 门面 | 一个 `FlowEngine` 对象：`engine.start(...)`、`engine.complete(...)`、`engine.todo(...)`。Java 是同步方法，TS 返回 Promise |
| 命令的执行 | 每个命令是一个对象，进一条**命令管道**：取当前操作人 → 权限 → 拦截器（前） → 在一个事务里执行（先锁实例行，0001 第 3 条） → 拦截器（后） → 提交 → 提交后的事件。管道的每一段都能替换（6 节） |
| 事务 | 引擎自己开事务；外面已有同一个数据源的事务时加入（Java 经 query 的 `SpringConnections`，TS 经 query 的 `db.transaction`）。表单后端代码与"同步"自动节点在这个事务里执行 |
| 幂等 | 每个命令可以带 `requestId`；同一个 `requestId` 第二次来，直接返回第一次的结果（0002 §5） |
| 结果 | 命令返回这次改了什么：实例的新状态、新生成与结束的待办、流转记录；前端据此刷新，不用再查一次 |

## 2. 操作人与泛型（已定，用户 2026-10-26）

```java
// Java：操作人由接入方定义，引擎只要求实现这个接口
public interface Actor {
    Long id();                   // 用户编号：接入方用户表的主键（编号都是 BIGINT，0007 第 1 节第 9 条）
    Set<Long> departments();     // 所在部门：要让上级部门的待办也能被认领，就把上级部门一起返回
    Set<Long> roles();           // 拥有的角色
    default Set<Long> groups(String kind) { return Set.of(); }   // 接入方扩展的候选种类（岗位、项目组……）的归属
}
FlowEngine<U extends Actor> engine;     // U 是接入方自己的用户类型，扩展点里拿到的就是 U，不用强转
```

```ts
// TS
interface Actor { id: number; departments: readonly number[]; roles: readonly number[]; groups?(kind: string): readonly number[] }
const engine = createFlowEngine<MyUser>({ ... });   // MyUser extends Actor
```

- **当前操作人**由 `ActorProvider` 给出（Spring 里默认从 `SecurityContext` 取；Nuxt 里从请求的 `event.context` 取，取法由接入方写）。命令也可以显式带 `actor`（后台任务、系统操作）。
- 解析器、守卫、动作、计算值、表单后端代码的上下文里都带着 `U`：接入方的部门、上级、租户这些信息直接可用。
- 其余的类型不用泛型：编号、业务编号是字符串，表单的值与流程变量是 JSON（Java 给 `FormValues`，可按字段取，也可 `as(MyForm.class)` 转成自己的类型；TS 的命令带泛型参数 `complete<MyForm>(...)`）。

## 3. 命令（已定，用户 2026-10-26）

所有命令都可带 `requestId`、`actor`（不带时取当前操作人）。

### 3.1 发起与实例

| 命令 | 参数 | 说明 |
| --- | --- | --- |
| `start` | `key`、`form`、`variables`、`businessKey`、`title`、`priority`（随 0007 补）、`picks`（5.6 选下一步的处理人） | 发起；`starters`（0005 第 2 节）不放行时报错 |
| `saveStartDraft` / `startFromDraft` | 同上 | 发起前存草稿（实例状态 `draft`，不进流程） |
| `withdraw` | `instanceId`、`reason` | 发起人撤回整个实例，按定义的 `withdraw` 规则 |
| `remind` | `instanceId` 或 `workItemId` | 催办，按频率限制 |
| `comment` | `instanceId`、`text`、`mentions` | 留言、@人，记进流转记录 |
| `signal` | `instanceId` 或 `businessKey`、`event`、`outcome`、`data` | 外部事件（0005 的 4.3） |

### 3.2 收件箱

| 命令 | 参数 | 说明 |
| --- | --- | --- |
| `claim` / `claimMany` | `workItemId`（们） | 认领，先认领先得；别人已认领时报 `already-claimed` |
| `release` | `workItemId`、`reason` | 退回收件箱，这个人以后不能再认领这一条 |
| `assign` | `workItemId`、`userId` | 收件箱负责人指定承办人 |
| `requestReassign` / `decideReassign` | `workItemId`、`reason` / `approve`、`userId` | 被指定的人申请改派、负责人处理 |

### 3.3 处理与协作

| 命令 | 参数 | 说明 |
| --- | --- | --- |
| `complete` / `completeMany` | `workItemId`、`outcome`、`comment`、`attachments`、`form`、`picks`、`returnTarget`（0005 的 5.4 `"choose"` 时） | 处理：守卫 → 表单后端代码 → 字段权限与清单校验 → 流转 |
| `saveDraft` | `workItemId`、`form`、`comment` | 暂存，不提交 |
| `transfer` | `workItemId`、`userId`、`reason` | 转办，自己不再参与 |
| `delegate` / `confirmDelegation` | `workItemId`、`userId` | 委派；被委派的人处理完回到自己确认 |
| `addSigners` / `removeSigners` | `workItemId`、`users`、`mode`（`before` 前加签、`after` 后加签、`parallel` 并加） | 加签、减签 |
| `withdrawStep` | `workItemId` | 处理人撤回刚处理的一步（下一步还没人处理时） |

### 3.4 管理员

| 命令 | 说明 |
| --- | --- |
| `suspend` / `resume` | 暂停（写原因与恢复条件）、恢复 |
| `terminate` | 终止，写原因 |
| `jump` | 把实例移到任意节点，原来的待办取消 |
| `changeAssignees` | 改某个待办的处理人或候选 |
| `changeData` | 改流程变量、表单的值；全部留痕 |
| `migrate` | 进行中的实例换到新版本：给出节点的对应关系，校验后迁移（0002 §1） |
| `retryJob` / `skipJob` | 异常（重试用完的作业，查询 `incidents`）重试；或跳过、按指定的结果往下走（0005 的 4.1）；终止实例用 `terminate` |

### 3.5 定义与表单

`saveDraft`、`validate`、`publish`、`disable` / `enable`、`copy`、`importJson` / `exportJson`、`diff`，流程定义与表单各一组（`engine.definitions.*`、`engine.forms.*`）。发布由后端给版本号，校验用模型包的规则（0005 第 7 节）。

## 4. 查询（已定，用户 2026-10-26）

列表类的查询都收 query 的表格请求（`TableRequest`：分页、排序、筛选、条件树，query 的 0016、0017），返回 `{ rows, total }`，前端的 `QueryTable` + `useTableQuery` 直接接上。

| 查询 | 返回 |
| --- | --- |
| `todo` | 我的待办：落到我的、我的角色或部门可认领的、需要我指定的（`scope` 选） |
| `done` | 我处理过的 |
| `started` | 我发起的 |
| `cc` | 抄送我的（带已读） |
| `instance` | 实例详情：定义快照、状态、当前节点、表单的值（按我在这个实例上的权限过滤，0004 的 4.5）、流程变量（只给管理员） |
| `timeline` | 流转记录 |
| `progress` | 流程图的进度：走过的节点与连线、正在处理的节点与处理人 |
| `predict` | 选了某个处理结果之后会走到哪些节点、哪些要选处理人（0005 的 5.6） |
| `externalView` | 外部参与方看的：当前环节、谁在处理、预计何时答复 |
| `stats` | 各节点停留时长、超时率、处理人工作量、按流程的数量 |
| `instances` | 管理端：按流程、状态、发起人、时间、表单字段筛选实例 |

- 待办的**业务摘要列**：默认是实例上的 `summary`（0005 第 2 节）；要连接入方自己的表时，实现 `TodoQueryCustomizer`，拿到 query 的查询构造器自己 join（6 节）。
- 返回的待办、实例形状沿用旧契约 `runtime.ts` 的 `WorkItem`、`ProcessInstance`（`variables` 换成按权限过滤的 `form`，加上 `summary`），定稿后放进模型包。

## 5. 事件（已定，用户 2026-10-26）

| 事件 | 时机 |
| --- | --- |
| `instance.started` / `withdrawn` / `suspended` / `resumed` / `terminated` / `finished` | 实例 |
| `workItem.created` / `claimed` / `released` / `assigned` / `completed` / `canceled` / `transferred` / `delegated` / `overdue` | 待办 |
| `node.entered` / `node.left` | 节点（含自动节点、网关） |
| `job.failed` | 作业重试用完 |

- 每个事件都写一条流转记录（0001 的 `flow_event`）。
- 监听分两种：**事务内**（`@FlowListener(phase = IN_TRANSACTION)` / `on(event, fn, { phase: 'inTransaction' })`）可以拒绝（抛错整个回滚）、可以同事务写业务表；**提交后**（`AFTER_COMMIT`）发通知、发消息队列，失败不影响流转。
- 监听按流程 `key`、节点、事件种类过滤；节点上的 `meta`（0005）随事件给出。

## 6. 扩展点与可替换的部分（已定，用户 2026-10-26）

两种用法：**注册**（按名字加一个：解析器、动作……）和**替换**（整个换掉一种标准行为）。Java 里每个接口都有一个默认实现，标 `@ConditionalOnMissingBean`，接入方声明自己的 Bean 就替换；TS 在 `createFlowEngine` 的选项里传。

| 类 | 接口 | 标准行为 | 怎样用 |
| --- | --- | --- | --- |
| 身份 | `ActorProvider<U>` | Spring 从 `SecurityContext` 取；Nuxt 无默认（接入方必须给） | 替换 |
| | `PermissionPolicy<U>` | 每个命令、查询前判断能不能做；**默认全部放行**（0004 第 6 节的宽松原则） | 替换 |
| | `ViewPolicy<U>` | 谁能看实例：参与过的、相关的角色与部门、发起人 | 替换 |
| 组织 | `OrgDirectory<U>` | 部门、角色（以及扩展的候选种类）展开成人，人在不在某部门、某角色，收件箱负责人（`inboxManager`）；**无默认**，接入方接自己的组织架构 | 替换 |
| | `AssigneeResolver` | 0005 的 3.2 六种来源加"上一步选" | 注册解析器；或替换整个处理人解析 |
| 业务 | `Guard`、`Action`、`ComputedValue` | — | 按名字注册（0005 的 5.1、4.1、6.1） |
| | `FormHandler` | — | 表单后端代码（0004 的 1.5），在线编辑或在代码里注册 |
| 流转 | `NodeBehavior` | 十一种节点类型 | 注册自定义节点类型（`x-名字`） |
| | `MultiCompletion` | 五种会签规则 | 注册新规则 |
| | `EdgeSelector`、`ConditionEvaluator`、`TitleRenderer` | 0005 的规则 | 替换 |
| 时间 | `Clock` | 系统时钟，UTC | 替换（测试里用固定时钟） |
| | `WorkCalendar`、`DurationCalculator` | `flow_calendar_day` 表 | 替换 |
| 存储 | `DefinitionStore`、`InstanceStore`、`WorkItemStore`、`EventStore`、`JobStore`、`FormStore`、`FormDataStore`、`WorkerStore`、`RequestStore`（后两个随 0007 补） | 用 query 读写 `flow_*` 表；表名前缀可配 | 每个单独替换（例如流转记录存到别处） |
| 作业 | `JobRunner` | 本进程里定时轮询 `flow_job`，普通读加带条件 UPDATE 抢占（0001 第 4 条） | 替换（例如交给接入方已有的调度平台调 `engine.jobs.runDue()`） |
| | `JobHandler` | 超时、提醒、无人认领、定时节点、异步动作 | 注册新的作业种类 |
| 通知 | `NotificationChannel` | 无（接入方注册站内信、邮件……） | 注册 |
| | `RecipientCalculator`、`NotificationFilter` | 0005 第 8 节的默认收件人与 `notify` | 替换 |
| 查询 | `TodoQueryCustomizer` | 只用实例上的 `summary` | 实现它 join 自己的表、加条件 |
| 管道 | `CommandInterceptor` | 无 | 注册：审计、业务校验、改参数；和标准处理在同一个事务里，先注册的在外层（Java 已实现：`FlowCommand` 标记命令，拦截器是环绕式的 `intercept(actor, operation, command, chain)`） |
| | 按命令替换 | 每个命令的标准实现 | **按命令替换**：哪个命令的标准行为不合适，就换掉那一个——用拦截器做：认出那个命令后不调 `chain.proceed`，自己做（不另设 `CommandHandler<C>`，少一套注册） |
| 序列化 | `JsonCodec` | Java 用 query 的 `wire`（不依赖 Jackson），TS 用内置 JSON | 替换（例如用接入方的 Jackson 配置） |
| 集群 | `FormChangeBroadcaster`（0004 的 1.5，0005 也用来刷新定义缓存） | 不发 | 实现 |

### 6.1 主键（已定，用户 2026-10-26 改定）

**数据库自增的 `BIGINT`，Java 里是 `long`（`Long`）**，不用雪花，也不做可替换的主键策略。

- 建表脚本里主键列是 `BIGINT AUTO_INCREMENT`（0007 第 9 节）；引擎插入后用 query 的 `insertKeys` 取回数据库给的编号。
- 引擎的接口、记录、扩展点里编号都是 `Long`；写到线上的 JSON（typed JSON）是文字，前端不丢精度。
- 场景用例每次重建表，自增从 1 开始，两个后端跑出来的编号相同，不需要测试用的发号器。
- 原来的雪花、大雪花、`IdStrategy`、`WorkerIdProvider`、`flow_worker` 都不要了；query 也不用再补"列类型按配置决定"。

## 7. 两个后端的形态（已定，用户 2026-10-26）

### 7.1 Java

- `flow-core`：不依赖 Spring。`FlowEngine.builder(dataSourceOrConnections).actorProvider(...).orgDirectory(...).build()`，没给的接口用默认实现。
- `flow-spring`：Spring Boot 自动配置。上面每个接口一个默认 Bean（`@ConditionalOnMissingBean`）；`@FlowResolver("deptLeader")`、`@FlowAction`、`@FlowGuard`、`@FlowListener` 之类的注解注册扩展；多数据源时用 `flow.datasource` 指定引擎用哪个（事务管理器跟着它，0001 第 3 条）。
- 可选的 HTTP 接口（7.3）：`flow-spring` 里的一组控制器，`flow.http.enabled` 打开，路径前缀可配。

### 7.2 TS

- 核心包 `@zerodep-dev/flow-core`：不依赖框架。`createFlowEngine<U>({ driver, org, ...扩展 })`，`driver` 是 query 的驱动；
  命令与查询的第一个参数是操作人（Node 没有线程上的"当前请求"，由调用方传，HTTP 接口里取 `actor(event)`）。建表用 `migrateFlowSchema(driver)`。
- `@zerodep-dev/flow-nuxt`（2026-10-27 落地，不做成 Nuxt 模块，见 HANDOFF 的无人值守决定 20）：
  `createFlowHandler({ engine, actor, prefix })` 是 7.3 的 HTTP 接口（h3 的处理函数，放在 `server/routes/flow/[...path].ts`），
  `startFlowJobs(engine)` 定时做到期的作业（放在 Nitro 插件里）。示例见 `typescript/apps/demo-server`。

### 7.3 HTTP 接口（可选）

- 两个后端的路径、参数、返回值**完全相同**，前端的流程组件（`flow-ui`）不用管背后是哪个后端。
- 一个命令一个 `POST`（`/flow/commands/complete`），一个查询一个 `GET` 或 `POST`（列表收表格请求）；`requestId` 放请求头 `Idempotency-Key`。
- HTTP 一律 200，成功与失败都看响应体里的业务错误码（0008）。
- JSON 约定同 0001：编号、大整数、小数按字符串，时间是带 `Z` 的 ISO 8601。
- 登录与权限由接入方的框架处理（Spring Security、Nuxt 的中间件），引擎只从 `ActorProvider` 取当前操作人。
- 落地（2026-10-26 Java 的 flow-spring，`flow.http.enabled=true` 时有；2026-10-27 Node 的 flow-nuxt 同一套）：

  | 方法与路径 | 请求体或参数 | data |
  | --- | --- | --- |
  | `POST {前缀}/commands/start` | `{ key, form, variables, businessKey, title, priority }` | `{ instance, workItems, closed, events }` |
  | `POST {前缀}/commands/claim` | `{ workItemId }` | 同上 |
  | `POST {前缀}/commands/complete` | `{ workItemId, outcome, comment, form, picks, returnTarget }` | 同上 |
  | `POST {前缀}/commands/withdraw` | `{ instanceId, reason }` | 同上 |
  | `POST {前缀}/commands/transfer`、`delegate` | `{ workItemId, userId, reason }` | 同上 |
  | `POST {前缀}/commands/add-signers`、`remove-signers` | `{ workItemId, users, mode, reason }`（`mode`：`before`、`after`、`parallel`） | 同上 |
  | `POST {前缀}/commands/release` | `{ workItemId, reason }` | 同上 |
  | `POST {前缀}/commands/remind`、`comment` | `{ instanceId, workItemId, message }`、`{ instanceId, text }` | 同上 |
  | `POST {前缀}/admin/terminate`、`suspend`、`resume`、`jump`、`reassign`、`skip-job` | `{ instanceId, reason }`、`{ instanceId, node, reason }`、`{ workItemId, userId, reason }`、`{ jobId, outcome, reason }` | 同上；谁能做由 `PermissionPolicy` 定 |
| `POST {前缀}/admin/retry-job` | `{ jobId }` | `{ revived }` |
  | `POST {前缀}/definitions/publish?note=` | 定义的 JSON 本身 | `{ id, key, version }` |
  | `POST {前缀}/forms/publish` | `{ key, name, category, manifest, clientSource, …, note }` | `{ id, key, version }` |
| `POST {前缀}/definitions/disable`、`enable`、`forms/disable`、`enable` | `{ key }` | 流程或表单列表的一行 |
| `GET {前缀}/queries/processes`、`forms` | — | 管理端的流程、表单列表（停用的也在） |
| `GET {前缀}/queries/processes/{key}/versions`、`definition?version=`、`forms/{key}/versions`、`document?version=` | — | 版本列表；某个版本的原文（流程是 `{ json }`，表单是清单与源码） |
| `GET {前缀}/queries/instances?status=&processKey=&keyword=`、`incidents?instanceId=` | — | 管理端搜实例、异常（重试用完的作业） |
  | `GET {前缀}/queries/startable` | — | `[{ key, name, category, version }]`：我能发起的流程 |
  | `GET {前缀}/queries/start/{key}` | — | `{ process, form }`：发起页，`form` 是开始节点上的表单（清单、前端代码、节点权限、值） |
  | `GET {前缀}/queries/todo?page=&pageSize=` | — | `{ rows, total, page, size, totalPages }` |
  | `GET {前缀}/queries/work-items/{id}` | — | `{ workItem, instance, form, outcomes, claimable, completable }`：待办详情 |
  | `GET {前缀}/queries/instances/{id}` | — | `{ instance, form, workItems, timeline }` |

  请求体用 query wire 的 `Json.read` 读（小数不丢精度，不依赖应用的 Jackson 版本）；data 是 typed JSON（编号、小数、时间是文字，根上带 `$types`）。
  命令带 `Idempotency-Key` 头时幂等（`flow_request`，过期的定期删）。

## 8. 错误（已定，用户 2026-10-26；HTTP 部分按 0008 改）

错误带业务错误码，两个后端相同。**HTTP 一律 200**，响应体里带码（用户 2026-10-26，见 [0008](0008-error-codes.md)；原来按代号映射 404 / 409 / 422 的做法作废）：

| 代号 | 说明 |
| --- | --- |
| `not-found` | 实例、待办、定义不存在 |
| `forbidden` | `PermissionPolicy` 不放行 |
| `not-candidate` | 不是这个待办的处理人或候选 |
| `already-claimed` | 别人已认领 |
| `work-item-not-open` | 待办已处理、已取消（重复提交） |
| `instance-not-active` | 实例已结束、已暂停 |
| `concurrent-modification` | 版本冲突（乐观锁） |
| `guard-rejected` | 守卫不通过，带守卫给的原因 |
| `validation-failed` | 表单或参数校验不通过，带按字段的错误 |
| `definition-invalid` | 发布时校验不通过，带问题列表（0005 第 7 节） |
| `extension-failed` | 扩展（解析器、动作、表单后端代码）抛错，带原始错误 |

- Java 是 `FlowException`（带 `code()` 与参数），TS 是 `FlowError`（带 `code` 与参数）；接入方的扩展抛的错包在 `extension-failed` 里，原样保留。
- 码的完整写法（命名空间、成功的码、响应的形状）与 query、vue-tsx-pro 的对应关系见 0008。
