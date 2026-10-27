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
| `identity` | 接入方实现的：操作人（`Actor`）、当前操作人、组织架构（`OrgDirectory`）、权限（`PermissionPolicy`） |
| `command` | 命令的参数与结果（每个命令一个类）、命令拦截器 |
| `data` | 形状由接入方定的数据：表单的值、流程变量；引擎内部的运行状态、暂存 |
| `entity` | 表（easy-query 的实体，导航写在实体上）：写库用它们 |
| `view` | 给前端与接入方的形状：`select(XxxVO.class)` 读出的 VO 与拼起来的视图，按 workitem、instance、definition、form、stats 分 |
| `extension` | 接入方扩展：处理人解析器、守卫、自动节点的动作、自定义节点、会签规则、字段类型、表单后端代码的创建、待办连表、作业的处理 |
| `form` | 表单后端代码的接口（`FormHandler`、`FormContext`……） |
| `listener`、`notify` | 监听流转记录；通知的渠道 |
| `calendar` | 工作日历（默认读 `flow_calendar` 表） |
| `db` | 接入方的 easy-query 上要装的：JSON 列的转换器、时间、租户与修改时间的拦截器 |
| `engine` | 引擎内部（下表）；类是 public 只为分包，接入方经 `FlowEngine` 用 |

`engine` 的子包：

| 子包 | 管什么 |
| --- | --- |
| `engine` | 推进的核心：`Execution`（选线 → 进节点 → 生成待办或结束，最后一次写回）、`EngineContext`（共用的 easy-query、时钟、组织、扩展、事件分发、定义缓存）、`EventDispatcher`（流转记录交给监听、通知交给渠道，管着事务边界）、`DefinitionCache`、`FlowErrors` |
| `engine.node` | 每种节点到达时做什么：人工（含会签、自动跳过、生成待办）、网关、定时、自动、等事件、调用流程、抄送、自定义节点 |
| `engine.handler` | 每个命令一个处理类：发起（含发起前的草稿）、认领、处理（含暂存）、撤回、撤回一步、协作、管理员（含迁移、改数据）、外部事件、发布、设计草稿、工作日历 |
| `engine.rule` | 流转规则：会签怎么算结果、委派加签的子待办、退回能选的节点、标题与摘要列、截止时间 |
| `engine.assignee` | 处理人：按来源解析成人与收件箱、谁算候选、预测下一步（`NextSteps`） |
| `engine.form` | 表单：按节点权限合并提交、引用（form.x、sys.x、流程变量）的取值、后端代码的编译与加载 |
| `engine.job` | 作业：轮询抢占（`JobRunner`）与内置的定时、超时、提醒、无人认领、异步动作 |
| `engine.query` | 查询，按对象各一个类：`InboxQueries`（待办、已办、抄送）、`WorkItemQueries`、`InstanceQueries`、`ProcessQueries`、`FormQueries`、`StatsQueries`；列表的筛选 `ListFilter` 与排序 `SortOrder` |
| `engine.store` | 几处共用的读写：锁实例、表单的值、待办的状态；归档（`Archives`） |
| `engine.notify` | 按流转记录算出要发的通知 |

## 建表

只支持 MySQL。命令要在读已提交的事务里跑：引擎自己开的事务会设；在自己的 `@Transactional` 里调命令时要写
`isolation = Isolation.READ_COMMITTED`，否则报错（0007 第 5 节）。

脚本在 `src/main/resources/db/flow/mysql`（V1 起，执行过的不再改），推荐交给 Flyway 单独一套执行（docs/decisions/0007 第 9 节）。
归档表（V11 的 `*_archive`）默认不用，打开归档后由 `FlowEngine.archive` 搬（0007 第 6 节）。
