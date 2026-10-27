# flow-core

流程引擎的 Java 实现，不依赖 Spring（Spring Boot 用 `flow-spring`）。规则（条件、选线、字段权限、校验）在模型包 `flow-model`，前后端共用。
接入方只用 `FlowEngine`：组装一次（`FlowEngine.builder(easyQuery)…build()`），调它的命令与查询。

读代码建议的顺序：`FlowEngine` → `engine.handler.StartHandler` / `CompleteHandler`（一个命令的步骤）→ `engine.Execution`（实例怎么往前走）
→ `engine.node`（每种节点到达时做什么）→ `entity` 与 `view`。

## 一次"处理"是怎么跑的

```
FlowEngine.complete(actor, command)
  │ 先问 PermissionPolicy，再过命令拦截器（CommandInterceptor，先注册的在外层）
  ▼
CompleteHandler.handle            （一个事务）
  1. 锁住实例行                   Instances.lock：版本加一，并发的处理在这里排队
  2. 重读待办                     已处理的报 flow.work-item-not-open；还在收件箱里的先认领
  3. 核对结果与意见               结果要是节点上写的；意见必填的要填；守卫（OutcomeGuard）放行
  4. 收表单                       FormSubmission：按节点上的字段权限合并、校验；表单的后端代码（FormHandler）校验、计算、写业务表
  5. 待办改成已处理               带条件的更新，改到 0 行就是别人先处理了；会签按完成规则决定等别人还是结束
  6. 往下走                       Execution.leave：选线 → 进下一个节点（engine.node）→ 生成待办 / 结束
  7. 写回                         Execution.flush：重算标题与摘要列，写回实例，写入流转记录，算出要发的通知
  ▼
CommandResult { instance, workItems（新生成的）, closed（结束的）, events（流转记录） }
```

发起（`StartHandler`）、认领（`ClaimHandler`）、撤回（`WithdrawHandler`）、协作（`CollaborationHandler`）、管理员（`AdminHandler`）
是同样的结构，步骤写在各自的类注释里，代码里用同样的序号标出。

## 包

| 包 | 管什么 |
| --- | --- |
| `org.dpdns.zerodep.flow.core` | `FlowEngine`（门面：每个操作先问权限，再交给 engine 里对应的处理类）、`FlowEngineBuilder`（组装） |
| `spi` | **接入方实现或用到的全部接口都在这一个包里**：操作人（`Actor`）与组织架构（`OrgDirectory`）、权限（`PermissionPolicy`、`ViewPolicy`）、当前操作人（`ActorProvider`，HTTP 接口用）；按名字注册的扩展（`PeopleResolver`、`OutcomeGuard`、`ServiceAction`、`ComputedValue`、`CustomNode`、`CountersignRule`、`FieldType`）与它们给设计器的说明（`ExtensionInfo`）；监听（`FlowListener`）与通知渠道（`NotificationChannel`）；表单后端代码（`FormHandler`、`FormValues`……）；工作日历（`WorkCalendar`）；命令拦截器、作业的处理、待办连表 |
| `command` | 命令的参数：每个命令一个类 |
| `entity` | 表（easy-query 的实体，导航写在实体上）：写库用它们；`TodoQueryCustomizer` 连表时也用 |
| `view` | 给前端与接入方的形状：命令的结果（`CommandResult`、`BatchResult`）、`select(XxxVO.class)` 读出的 VO 与拼起来的视图，按 workitem、instance、definition、form、stats 分 |
| `db` | 接入方的 easy-query 上要装的：JSON 列的转换器、时间、租户与修改时间的拦截器 |
| `engine` | 引擎内部（下表）；类是 public 只为分包，接入方经 `FlowEngine` 用 |

`engine` 的子包：

| 子包 | 管什么 |
| --- | --- |
| `engine` | 推进的核心：`Execution`（选线 → 进节点 → 生成待办或结束，最后一次写回）、`EngineContext`（共用的 easy-query、时钟、组织、扩展、事件分发、定义缓存）、`EventDispatcher`（流转记录交给监听、通知交给渠道、发件箱，管着事务边界）、`Extensions`（按名字注册的扩展）、`InstanceDisplay`（标题与摘要列）、`TableWorkCalendar`（默认的工作日历）、`DefinitionCache`、`FlowErrors` |
| `engine.state` | 存在 JSON 列里的引擎状态：运行状态（`RunState`）、流程变量、暂存 |
| `engine.node` | 每种节点到达时做什么：人工（含会签、自动跳过、生成待办）、网关、定时、自动、等事件、调用流程、抄送、自定义节点 |
| `engine.handler` | 每个命令一个处理类：发起（含发起前的草稿）、认领、处理（含暂存）、撤回、撤回一步、协作、管理员（含迁移、改数据）、外部事件、发布、设计草稿、工作日历 |
| `engine.rule` | 流转规则：会签怎么算结果、委派加签的子待办、退回能选的节点、截止时间 |
| `engine.assignee` | 处理人：按来源解析成人与收件箱、谁算候选、预测下一步（`NextSteps`） |
| `engine.form` | 表单：按节点权限合并提交、引用（form.x、sys.x、流程变量）的取值、后端代码的编译与加载 |
| `engine.job` | 作业：轮询抢占（`JobRunner`）与内置的定时、超时、提醒、无人认领、异步动作；发件箱的投递（`DeliveryJob`） |
| `engine.query` | 查询，按对象各一个类：`InboxQueries`（待办、已办、抄送）、`WorkItemQueries`、`InstanceQueries`、`ProcessQueries`、`FormQueries`、`StatsQueries`；列表的筛选 `ListFilter` 与排序 `SortOrder` |
| `engine.store` | 几处共用的读写：锁实例、表单的值、待办的状态；归档（`Archives`） |
| `engine.notify` | 按流转记录算出要发的通知 |

## 建表

只支持 MySQL。命令要在读已提交的事务里跑：引擎自己开的事务会设；在自己的 `@Transactional` 里调命令时要写
`isolation = Isolation.READ_COMMITTED`，否则报错（0007 第 5 节）。

脚本在 `src/main/resources/db/flow/mysql`（V1 起，执行过的不再改），推荐交给 Flyway 单独一套执行（docs/decisions/0007 第 9 节）。
归档表（V11 的 `*_archive`）默认不用，打开归档后由 `FlowEngine.archive` 搬（0007 第 6 节）。
