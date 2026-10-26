# 0006 引擎对外的接口

- 状态：已定（用户 2026-10-26：主键只做雪花与大雪花，其余按推荐定稿）
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
    String id();
    Set<String> positions();     // 所在岗位：查"可认领"的待办
    Set<String> departments();   // 所在部门
}
FlowEngine<U extends Actor> engine;     // U 是接入方自己的用户类型，扩展点里拿到的就是 U，不用强转
```

```ts
// TS
interface Actor { id: string; positions: readonly string[]; departments: readonly string[] }
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
| `start` | `key`、`form`、`variables`、`businessKey`、`title`、`picks`（5.6 选下一步的处理人） | 发起；`starters`（0005 第 2 节）不放行时报错 |
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
| `addSigners` / `removeSigners` | `workItemId`、`users`、`position`（`before` 前加签、`after` 后加签、`parallel` 并加） | 加签、减签 |
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
| `retryJob` / `resolveFailure` | 失败的作业重试；"异常处理"待办选重试、指定结果继续或终止（0005 的 4.1） |

### 3.5 定义与表单

`saveDraft`、`validate`、`publish`、`disable` / `enable`、`copy`、`importJson` / `exportJson`、`diff`，流程定义与表单各一组（`engine.definitions.*`、`engine.forms.*`）。发布由后端给版本号，校验用模型包的规则（0005 第 7 节）。

## 4. 查询（已定，用户 2026-10-26）

列表类的查询都收 query 的表格请求（`TableRequest`：分页、排序、筛选、条件树，query 的 0016、0017），返回 `{ rows, total }`，前端的 `QueryTable` + `useTableQuery` 直接接上。

| 查询 | 返回 |
| --- | --- |
| `todo` | 我的待办：落到我的、我所在岗位可认领的、需要我指定的（`scope` 选） |
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
| | `ViewPolicy<U>` | 谁能看实例：参与过的、相关岗位、发起人 | 替换 |
| 组织 | `OrgDirectory<U>` | 岗位、部门展开成人，人在不在某岗位，收件箱负责人（`inboxManager`）；**无默认**，接入方接自己的组织架构 | 替换 |
| | `AssigneeResolver` | 0005 的 3.2 六种来源加"上一步选" | 注册解析器；或替换整个处理人解析 |
| 业务 | `Guard`、`Action`、`ComputedValue` | — | 按名字注册（0005 的 5.1、4.1、6.1） |
| | `FormHandler` | — | 表单后端代码（0004 的 1.5），在线编辑或在代码里注册 |
| 流转 | `NodeBehavior` | 十一种节点类型 | 注册自定义节点类型（`x-名字`） |
| | `MultiCompletion` | 五种会签规则 | 注册新规则 |
| | `EdgeSelector`、`ConditionEvaluator`、`TitleRenderer` | 0005 的规则 | 替换 |
| 时间 | `Clock` | 系统时钟，UTC | 替换（测试里用固定时钟） |
| | `WorkCalendar`、`DurationCalculator` | `flow_calendar_day` 表 | 替换 |
| 编号 | `IdStrategy` | 雪花（BIGINT）；另带大雪花（6.1） | 二选一，或实现接口自定义 |
| 存储 | `DefinitionStore`、`InstanceStore`、`WorkItemStore`、`EventStore`、`JobStore`、`FormStore`、`FormDataStore`、`WorkerStore`、`RequestStore`（后两个随 0007 补） | 用 query 读写 `flow_*` 表；表名前缀可配 | 每个单独替换（例如流转记录存到别处） |
| 作业 | `JobRunner` | 本进程里定时轮询 `flow_job`，普通读加带条件 UPDATE 抢占（0001 第 4 条） | 替换（例如交给接入方已有的调度平台调 `engine.jobs.runDue()`） |
| | `JobHandler` | 超时、提醒、无人认领、定时节点、异步动作 | 注册新的作业种类 |
| 通知 | `NotificationChannel` | 无（接入方注册站内信、邮件……） | 注册 |
| | `RecipientCalculator`、`NotificationFilter` | 0005 第 8 节的默认收件人与 `notify` | 替换 |
| 查询 | `TodoQueryCustomizer` | 只用实例上的 `summary` | 实现它 join 自己的表、加条件 |
| 管道 | `CommandInterceptor` | 无 | 注册：审计、业务校验、改参数 |
| | `CommandHandler<C>` | 每个命令的标准实现 | **按命令替换**：哪个命令的标准行为不合适，就换掉那一个 |
| 序列化 | `JsonCodec` | Java 用 query 的 `wire`（不依赖 Jackson），TS 用内置 JSON | 替换（例如用接入方的 Jackson 配置） |
| 集群 | `FormChangeBroadcaster`（0004 的 1.5，0005 也用来刷新定义缓存） | 不发 | 实现 |

### 6.1 主键策略（已定，用户 2026-10-26）

内置只做两种，都是插入前由应用生成；不满意就实现 `IdStrategy` 自己定（同样由应用生成）。不做数据库自增。

| 策略 | 列类型 | 说明 |
| --- | --- | --- |
| **雪花（默认）** | `BIGINT` | 默认经典布局：毫秒时间 41 位、机器号 10 位、序号 12 位；布局可调（总共不超过 63 位）。机器号的规则见 0001 的"雪花的机器号" |
| 大雪花 | `DECIMAL(p, 0)` | 位数布局由接入方自由调：起始时间、时间单位（毫秒或秒）、时间位数、区域位数、机器位数、序号位数；总位数超过 64 位也行，`p` 按总位数算出来。Java 用 `BigInteger`、TS 用 `bigint` 生成 |

两种雪花的位布局都写成 `SnowflakeLayout`，机器号的每一段（区域号、机器号……）由 `WorkerIdProvider` 给出，规则都可调、都能替换（用户 2026-10-26）。
| 自定义 | 由实现给出 | 实现 `IdStrategy` |

参考 EasyQuery（3.2.17）：应用生成的主键是 `@Column(primaryKeyGenerator = X.class)`，接口 `PrimaryKeyGenerator` 只有一个 `getPrimaryKey()`，外加可覆盖的 `setPrimaryKey(实体, 列)`；生成器按类登记实例，Spring 里收集容器中的 Bean；它自己不带雪花，留给使用方实现。
（它另有数据库生成的一套：`generatedKey = true` 加 `getGeneratedKeys` 回填。我们不做数据库自增，用不上。）`IdStrategy` 的约定（两个后端同名同义）：

```java
public interface IdStrategy {
    /** 编号列的物理类型（建表与迁移用） */
    ColumnType columnType(String table);
    /** 给出新编号 */
    String next(String table);
    /** 引擎里的编号 ↔ 绑定到语句、从结果读回的值 */
    Object toDatabase(String id);
    String fromDatabase(Object value);
}
```

- **引擎里编号一律是字符串**（接口、JSON、扩展点的参数都是），不管背后是 `BIGINT` 还是 `DECIMAL`；换策略不改任何接口。EasyQuery 用实体字段自己的类型，那是 ORM 的需要；引擎要让两个后端和前端看到的都一样，所以统一成字符串。
- **可以按表换策略**：默认整个引擎一种，个别表可以单独换（例如流转记录用大雪花），所以方法都带表名。
- 登记方式同其他扩展点：Spring 里声明一个 `IdStrategy` Bean 就替换默认的雪花；TS 在选项里传。
- 雪花、大雪花在时钟回拨时的处理可配：等到时钟追上（默认）、报错、借用序号。
- 策略在**建表时**定下：表建好以后换策略要迁移数据，引擎启动时发现配置与表的列类型不一致就报错。
- query 配合补一项能力并发版：表声明里的列类型能在建数据库对象时按配置决定（id 列按策略取 `BIGINT` 或 `DECIMAL(p,0)`），见 0001 的"对 query 的要求"。

## 7. 两个后端的形态（已定，用户 2026-10-26）

### 7.1 Java

- `flow-core`：不依赖 Spring。`FlowEngine.builder(dataSourceOrConnections).actorProvider(...).orgDirectory(...).build()`，没给的接口用默认实现。
- `flow-spring`：Spring Boot 自动配置。上面每个接口一个默认 Bean（`@ConditionalOnMissingBean`）；`@FlowResolver("deptLeader")`、`@FlowAction`、`@FlowGuard`、`@FlowListener` 之类的注解注册扩展；多数据源时用 `flow.datasource` 指定引擎用哪个（事务管理器跟着它，0001 第 3 条）。
- 可选的 HTTP 接口（7.3）：`flow-spring` 里的一组控制器，`flow.http.enabled` 打开，路径前缀可配。

### 7.2 TS

- 核心包：不依赖框架。`createFlowEngine<U>({ db, actor, org, ... })`，`db` 是 query 的数据库对象。
- `flow-nuxt`：Nuxt 模块。在 `nuxt.config` 里打开，服务端插件里 `defineFlowEngine(...)` 注册扩展；可选的 HTTP 接口挂在 `server/api/flow/**`。

### 7.3 HTTP 接口（可选）

- 两个后端的路径、参数、返回值**完全相同**，前端的流程组件（`flow-ui`）不用管背后是哪个后端。
- 一个命令一个 `POST`（`/flow/commands/complete`），一个查询一个 `GET` 或 `POST`（列表收表格请求）；`requestId` 放请求头 `Idempotency-Key`。
- JSON 约定同 0001：编号、大整数、小数按字符串，时间是带 `Z` 的 ISO 8601。
- 登录与权限由接入方的框架处理（Spring Security、Nuxt 的中间件），引擎只从 `ActorProvider` 取当前操作人。

## 8. 错误（已定，用户 2026-10-26）

错误带代号，两个后端相同；HTTP 接口按代号映射状态码：

| 代号 | 说明 | HTTP |
| --- | --- | --- |
| `not-found` | 实例、待办、定义不存在 | 404 |
| `forbidden` | `PermissionPolicy` 不放行 | 403 |
| `not-candidate` | 不是这个待办的处理人或候选 | 403 |
| `already-claimed` | 别人已认领 | 409 |
| `work-item-not-open` | 待办已处理、已取消（重复提交） | 409 |
| `instance-not-active` | 实例已结束、已暂停 | 409 |
| `concurrent-modification` | 版本冲突（乐观锁） | 409 |
| `guard-rejected` | 守卫不通过，带守卫给的原因 | 422 |
| `validation-failed` | 表单或参数校验不通过，带按字段的错误 | 422 |
| `definition-invalid` | 发布时校验不通过，带问题列表（0005 第 7 节） | 422 |
| `extension-failed` | 扩展（解析器、动作、表单后端代码）抛错，带原始错误 | 500 |

- Java 是 `FlowException`（带 `code()` 与参数），TS 是 `FlowError`（带 `code` 与参数）；接入方的扩展抛的错包在 `extension-failed` 里，原样保留。
