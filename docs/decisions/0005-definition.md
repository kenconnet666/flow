# 0005 流程定义 JSON

- 状态：已定（用户 2026-10-26，逐节讨论定稿）
- 起点：vue-tsx-pro 提交 `f7bfdc2e` 的 `packages/ui/src/workflow/definition.ts`（BPMN 子集的图：泳道、开始与结束、人工节点、排他与并行网关、连线带处理结果与条件）
- 相关：方向见 [0001](0001-engine-direction.md)（模型是图加结构校验、定义发布后不可变），能力见 [0002](0002-capabilities.md)，表单见 [0004](0004-forms.md)

## 1. 总体（已定，用户 2026-10-26）

| 项 | 做法 |
| --- | --- |
| 模型 | 图：泳道、节点、连线。带轮次的循环、退回指定节点、提级、并行汇合，节点树表达不了（0001、frontend.md 已定） |
| 形式 | 纯 JSON，前后端同一份；不写脚本，条件是条件树，要逻辑的地方按名字引用接入方注册的扩展（解析器、动作、守卫、自定义判断） |
| 版本 | 草稿没有版本号；发布时后端给版本号，发布后不可变；进行中的实例按发起时的版本走完 |
| 布局 | 节点的位置、连线的拐点放在单独的 `layout` 里，引擎不读；版本对比默认忽略布局的变化 |
| 校验 | 结构与引用的校验规则在模型包里，前端设计器与后端发布用同一份，问题只给代号和参数（第 7 节） |

### 1.1 取值与引用

很多地方的值可以直接写，也可以取别处的值。统一写成 `{ "ref": "名字" }`，名字分四类：

| 写法 | 指的是 | 例子 |
| --- | --- | --- |
| `form.字段名` | 表单的值（0004 的 5.5） | `form.amount`、`form.buyer`、`form.lines` |
| `sys.名字` | 引擎给的只读值 | `sys.initiator`（发起人）、`sys.startedAt`、`sys.round.节点`（这个节点第几轮） |
| 变量名 | 流程变量（第 2 节 `variables` 里声明的） | `team`、`negotiationRound` |
| `fn.名字` | 接入方注册的计算值，只能用在连线条件里（6.1，2026-10-26 随第 6 节补入） | `fn.budgetRemaining` |

- 条件树里比较的 `field` 用同样的名字（与组件库的 `Condition` 同形：`{ field, op, value }` 加 `all` / `any` / `not`）。旧契约里的 `var` 改成 `field`（0003 已定）。
- 流程变量名不能带点，和其他几类分得开。

## 2. 顶层（已定，用户 2026-10-26）

```jsonc
{
  "key": "purchase",                 // 同一流程各版本相同
  "name": "采购申请",
  "description": "门店采购，金额大的要财务复核",
  "category": "采购",
  "version": 3,                      // 发布时后端给；草稿里没有
  "form": { "key": "purchase", "version": 5 },   // 一个流程一张表单（0004 的 5.1）；version 在发布时固定
  "variables": [                     // 流程变量：条件构造器、连线副作用、校验都靠这份声明
    { "name": "negotiationRound", "type": "number", "label": "协商轮次", "default": 0 },
    { "name": "team", "type": "people", "label": "承办团队" }
  ],
  "summary": ["form.title", "form.amount"],      // 待办列表的摘要列：每次提交抄进实例的展示用摘要
  "title": "采购申请：{form.title}",              // 实例标题的模板：花括号里是 { ref } 的名字；不写时用流程名
  "starters": [{ "department": 10 }],    // 谁能发起：来源同 3.2；不写时人人可发起
  "settings": { /* 第 2.1 节 */ },
  "lanes": [ /* 第 3 节 */ ],
  "nodes": [ /* 第 4、5 节 */ ],
  "edges": [ /* 第 6 节 */ ],
  "layout": { "nodes": { "check": { "x": 320, "y": 80 } }, "edges": { "e3": { "points": [[400, 40]] } } }
}
```

- `variables[].type` 用表单字段类型的一部分：`text`、`number`、`decimal`、`boolean`、`date`、`datetime`、`select`、`people`（0004 的 1.3）。
- 没有表单的流程可以不写 `form`。
- `title`：模板只做替换，不做计算；`people` 类型的值换成名字，其余按值原样。实例标题在发起时生成，之后表单改了标题字段，每次提交时重新生成（同 `summary`）。
- `starters`：可用的来源是角色、部门、指定的人、解析器（3.2 里的"发起人""同某节点"在发起时没有意义）。接入方的权限扩展点可以再收紧，引擎只按这份名单放行。
- `key`、`version`、`name` 与表里的列重复：定义存成快照时自己能说明自己是哪个流程的哪一版，单独拿出来（导出、对比）也看得懂。

### 2.1 流程级设置

| 设置 | 说明 | 默认 |
| --- | --- | --- |
| `calendar` | 用哪份工作日历（0002 §7） | `"default"` |
| `withdraw` | 发起人撤回整个实例：`"beforeClaim"`（第一个待办被认领前）、`"beforeAction"`（任何人处理前）、`"never"` | `"beforeAction"` |
| `emptyAssignee` | 处理人解析为空时：`"autoPass"`（自动通过）、`"admin"`（转给管理员）、`"error"`（发起或流转失败） | `"admin"` |
| `autoSkip` | 自动跳过：`{ "sameAsPrevious": true, "initiator": false }`（处理人和上一步相同、处理人就是发起人时自动通过） | 都关 |
| `formView` | 不在待办里看表单的人（发起人看进度、抄送、历史）对哪些字段隐藏：`{ "cost": "hidden" }`（0004 的 4.3） | 全部只读 |
| `notify` | 通知怎样发：按事件换模板、加收件人、不发（第 8 节，2026-10-26 随第 8 节补入） | 全发 |

节点上可以覆盖 `emptyAssignee`、`autoSkip`。

## 3. 泳道与处理人（已定，用户 2026-10-26）

### 3.1 泳道

```jsonc
{ "id": "finance", "name": "财务部", "inbox": { "role": 1 } }
```

- 泳道是流程图上的一行，通常对应一个角色；`inbox` 是这一行里人工节点没写处理人时的默认去处（角色收件箱）。
- 旧契约的 `group` 改成 `inbox: { role }`。**标准的组织模型是用户、部门、角色**（加接入方自己的权限、菜单、按钮控制）；"岗位"不是标准名词，要用时绑定到角色，或扩展一种候选（3.2 的"扩展的种类"）（用户 2026-10-26）。
- **收件箱的负责人**：无人认领时转负责人指定（5.3 的 `unclaimed`）、负责人指定承办人、处理改派申请（0002 §4），都要知道"这个收件箱的负责人是谁"。
  写成 `inbox: { role, manager: 来源 }`（来源同 3.2）；不写 `manager` 时调接入方的扩展点 `inboxManager(角色)`。

### 3.2 处理人

人工节点的 `assignees` 是一个或多个来源，取并集。用户、角色、部门的编号是接入方组织表的主键（正整数，超出 JavaScript 安全整数时写成只有数字的文字），写成名字时发布报 `org-id-invalid`：

| 来源 | 写法 | 落到哪 |
| --- | --- | --- |
| 角色收件箱 | `{ "role": 1 }`，或 `{ "role": { "ref": "form.reviewRole" } }` | 有这个角色的人认领，先认领先得 |
| 指定的人 | `{ "user": 1001 }`，或 `{ "user": { "ref": "form.buyer" } }` | 直接落到人；`people` 类型的值里有几个人就是几个候选 |
| 部门 | `{ "department": 20 }` | 部门下面的人认领（含不含下级部门由 `OrgDirectory` 与 `Actor.departments()` 定，0006 第 2 节） |
| 发起人 | `{ "initiator": true }` | 发起人 |
| 同某节点 | `{ "sameAs": "check" }` | 那个节点最后的处理人 |
| 解析器 | `{ "resolver": "deptLeader", "args": { "level": 2 } }` | 接入方注册的扩展点：部门负责人、上级第 N 级…… |
| 扩展的种类 | `{ "group": "position", "id": 7 }` | 接入方注册的候选种类（岗位、项目组……）：有这个归属的人认领；操作人的归属由 `Actor.groups(种类)` 给出（0006 第 2 节） |
| 上一步选 | `{ "pick": { "from": [来源…], "max": 1 } }` | 上一步的处理人提交时选（5.6，2026-10-26 随第 5 节补入） |

- 解析出来只有一个人：直接落到他；多于一个人，或含角色、部门、扩展的种类：进收件箱，候选人认领。
- 指定的人、角色、部门、扩展的种类可以带 `name`（`{ "role": 1, "name": "财务" }`）：设计器选人时记下的名字快照，只给人看（属性面板、JSON），
  引擎按编号解析、不读它（2026-10-27 随设计器补入）。
- 解析为空时按 `emptyAssignee`。
- 不写 `assignees` 时用所在泳道的 `inbox`；两者都没有时发布校验报错。

### 3.3 几条规则

- **`assignees` 与会签分开**：`assignees` 是"谁能处理这一个待办"，几个候选里一个人办完就算完；要"每人各办一份"用会签 `multi.participants`（5.2）。
  所以 `{ "user": { "ref": "form.reviewers" } }` 里有三个人时，是三个候选抢一个待办，不是三个人各审一遍。
- **到达时解析一次**：流程走到这个节点时解析出候选，写进待办的候选表（0001 的 `flow_candidate`）；之后组织架构变了，已经生成的待办不跟着变。要改由管理员改处理人（0002 §6），或负责人指定。
- **解析器的约定**：接入方注册 `resolve(名字, args, 上下文)`，返回用户、部门、角色（或扩展种类）的列表（同组件库 `PeopleEntry` 的 `{ type, id }`）；上下文里有实例、表单的值、流程变量、发起人。两个后端各实现一份同名的解析器。
- **外部参与方**（供应方的账号，0002 §3）：不另设来源，用解析器（`{ "resolver": "supplierAccount", "args": { "supplier": { "ref": "form.supplier" } } }`）返回外部账号；主账号与子账号能做什么由操作拦截判断。
- **被排除的人**（退回过收件箱，不能再认领同一条，0002 §4）：运行时写进候选表，不在定义里。

## 4. 节点（已定，用户 2026-10-26）

所有节点共有：`id`（流程内唯一）、`type`、`name`、`lane`、`description`、`meta`（业务附加信息，引擎原样保存不解释，如对应的业务状态）、`offline`（线下动作：流程图画虚线框，平台上仍要有人回填结果）。

| `type` | 是什么 | 自己的字段 | 出边按什么分 |
| --- | --- | --- | --- |
| `start` | 开始，唯一 | `form`（发起时的字段权限，0004 第 4 节；随实现补） | 一条出边 |
| `end` | 结束 | `result`（通过、否决、搁置……业务据此判断怎么结束的）、`terminate`（到这里取消并行的其他分支） | 没有出边 |
| `task` | 人工节点 | 第 5 节 | 处理结果（`outcome`），再看条件 |
| `exclusive` | 排他网关 | — | 条件，按先后取第一条成立的，都不成立走 `default` |
| `parallel` | 并行网关 | — | 分叉时每条都走；汇合时等所有入边 |
| `inclusive` | 包容网关 | — | 条件成立的都走；汇合时只等实际走了的分支 |
| `service` | 自动节点 | `action`（接入方注册的动作名）、`args`（JSON）、`mode`（`"async"` 默认、`"sync"`，见 4.1）、`retry`（`{ "times": 3, "backoff": { "minutes": 1 } }`）、`onFailure`（`"manual"` 默认，转管理员处理；`"error"` 整个实例暂停） | 动作返回的结果（`outcome`），或一条出边 |
| `call` | 调用流程（子流程） | `process`（流程 key）、`mode`（`"sync"` 等子流程结束、`"async"` 发起后不等）、`input`（`{ "子流程变量或 form.字段": 值 }`）、`output`（同步时把子流程的值写回） | 同步时按子流程结束的 `result`（`outcome`） |
| `timer` | 定时等待 | `wait`：`{ "workdays": 2 }`、`{ "days": 1 }`、`{ "hours": 4 }`，或 `{ "until": { "ref": "form.signDate" } }` | 一条出边 |
| `receive` | 等待外部事件 | `event`（事件名）、`outcomes`（事件可能带回的结果列表）、`due`（等多久）、`overdue`（超时怎么办，同 5.3）、`output` | 事件带回的结果（`outcome`） |
| `cc` | 抄送节点 | `to`（同 3.2 的来源）、`message` | 一条出边，不阻塞 |

- "出边按什么分"里的 `outcome` 是同一个概念：人工节点的处理结果、自动节点与子流程返回的结果、外部事件带回的结果。出边都用 `outcome` 选，都可以再加条件。
- `call` 的 `process` 发起时取子流程的最新版本；父子关系与来源写进实例（0001 的 `flow_instance`）。
- **时长**全文统一写成对象：`{ "workdays": n }`、`{ "days": n }`、`{ "hours": n }`、`{ "minutes": n }`，工作日按流程的 `calendar`。

### 4.1 自动节点

- 接入方注册动作 `perform(名字, args, 上下文)`，返回 `{ outcome?, variables? }`（走哪个结果、改哪些流程变量）。`args` 里可以有 `{ ref }`，执行前取好值。
- `mode: "async"`（默认）：流转的事务提交后由作业（`flow_job`）执行，失败按 `retry` 重试。外部调用（发通知、调别的系统）都应当这样，**动作要能重复执行**（至少执行一次），上下文里给一个幂等键（作业编号）。
- `mode: "sync"`：在流转的同一个事务里执行，失败就整个流转回滚、报给发起这次操作的人。只适合写本库的表。
- 重试用完后成了一条**异常**（管理端的异常列表 `incidents`、实例上记一条 `job.failed`，接入方监听它通知管理员），管理员可以重试（`retryJob`）、跳过并指定一个结果往下走（`skipJob`，结果要是出边上写的）、或终止实例。
  - `onFailure: "manual"`（默认）：实例停在这个节点，别的分支照常走。
  - `onFailure: "error"`：整个实例暂停（别的分支的待办也不能处理），管理员恢复后再重试或跳过。

### 4.2 调用流程

```jsonc
{ "id": "quote", "type": "call", "name": "供应商报价", "process": "supplier-quote", "mode": "sync",
  "forEach": { "ref": "form.suppliers" }, "as": "supplier",
  "input": { "form.supplier": { "ref": "supplier" }, "form.items": { "ref": "form.lines" } },
  "output": { "quotes": "form.total" } }
```

- `input` 的键是**子流程那一侧**的名字（子流程的 `form.字段` 或流程变量），值是父流程这一侧的值；`output` 的键是父流程的流程变量，值是子流程那一侧的名字。
- `forEach`：对一个列表里的每一项各发起一个子流程（每个供应商一份报价），`as` 是循环里那一项的名字；同步时等全部结束，`output` 收成列表。
- 同步时子流程按结束节点的 `result` 给出 `outcome`；`forEach` 时全部都是同一个结果才按它，否则按 `default` 出边。
- 父实例被终止或撤回时，进行中的子流程一起终止。

### 4.3 等待外部事件

- 业务系统调引擎的接口送来事件：`signal(实例或业务编号, 事件名, 结果, 数据)`；`output` 把数据里的值写进流程变量或表单（`{ "form.negotiatedPrice": "price" }`）。
- 事件到了而实例不在等它时，记一条流转记录后忽略（不报错，业务系统可以放心重发）。

### 4.4 并行与包容

- 分叉与汇合**要配对**（结构化）：每个并行、包容的分叉有一个对应的汇合，中间的分支不能跳出去（校验 `gateway-unpaired`）。包容汇合因此只需要等它配对的分叉实际走了的那几条。
- 退回、提级连线跳出并行区域（目标在分叉之前）时，区域里其他还在进行的分支一起取消，从目标节点重新走。
- 不带 `terminate` 的结束节点只结束它所在的分支；所有分支都结束了，实例才结束。

## 5. 人工节点（已定，用户 2026-10-26）

```jsonc
{
  "id": "review", "type": "task", "name": "财务复核", "lane": "finance",
  "assignees": [{ "role": 1 }],
  "outcomes": [
    { "key": "pass", "label": "通过" },
    { "key": "back", "label": "退回修改", "kind": "return", "comment": "required" },
    { "key": "reject", "label": "否决", "kind": "reject", "comment": "required", "veto": true }
  ],
  "multi": { "participants": { "ref": "team" }, "complete": "ratio", "ratio": 0.6 },
  "due": { "workdays": 2 },
  "reminders": [{ "before": { "hours": 4 } }, { "every": { "workdays": 1 }, "times": 3 }],
  "overdue": { "action": "complete", "outcome": "pass" },
  "unclaimed": { "after": { "workdays": 3 }, "action": "assignByManager" },
  "returnTo": "resubmit",
  "allow": { "transfer": true, "delegate": true, "addSign": true, "withdraw": true },
  "form": { "default": "readonly", "fields": { "amount": "required" } }
}
```

### 5.1 处理结果

| 字段 | 说明 |
| --- | --- |
| `key`、`label` | 标识与按钮文字 |
| `kind` | `approve`（默认）、`reject`、`return`（退回）、`escalate`（提级）、`terminate`（终止流程）：决定按钮外观与引擎的行为 |
| `result` | 只用于 `terminate`：终止后实例的结束结果（同结束节点的 `result`，如 `"withdrawn"`）；`terminate` 的结果不需要出边 |
| `comment`、`attachments` | `"required"` / `"optional"`；不写时不显示 |
| `veto` | 会签时一票否决：任何一人选了它，节点立即按它结束 |
| `guards` | 提交前的守卫：接入方注册的名字列表，任一不通过就不能提交（0002 §5"处理"） |
| `confirm` | 点了先确认（"确定否决？"） |

旧契约 `ProcessOutcome.fields`（处理时必须给出的流程变量）去掉：表单字段的必填由 `form.fields` 管（0004 第 4 节），流程变量由连线的副作用改。

### 5.2 会签

`multi`：`participants`（同 3.2 的来源，或 `{ "ref": ... }`）加完成规则 `complete`：

| `complete` | 何时往下 |
| --- | --- |
| `all` | 所有人都处理完 |
| `any` | 任一人处理完（或签） |
| `ratio` | 选同一结果的人数达到 `ratio`（0–1） |
| `votes` | 选同一结果的票数达到 `votes`；参与人可带权重（`{ "user": 1001, "weight": 2 }`） |
| `sequential` | 一个接一个，全部处理完 |

节点按哪个结果结束（同钉钉会签"一人拒绝即拒绝"的做法）：

| `complete` | 规则 |
| --- | --- |
| `all`、`sequential` | 有人选了非 `approve` 的结果（否决、退回、提级、终止），节点**立即**按它结束，其余人的待办取消；所有人都选了 `approve` 才按 `approve` 结束 |
| `any` | 第一个处理的人选什么就按什么结束 |
| `ratio`、`votes` | 哪个结果先达到门槛就按它结束；所有人都处理完仍没有结果达到门槛，按 `fallback`（必须写，如 `"reject"`），没写时发布校验报错（`multi-no-fallback`） |

- `veto` 只对 `ratio`、`votes` 有意义：选了它的人一出现，节点立即按它结束，不等门槛。`all`、`sequential` 下非 `approve` 的结果本来就立即生效。
- 运行时的加签、减签按 `allow.addSign` 控制。

### 5.3 时限、提醒、超时、无人认领

| 字段 | 说明 |
| --- | --- |
| `due` | `{ "workdays": n }`、`{ "days": n }`、`{ "hours": n }`、`{ "until": { "ref": "form.deadline" } }`；工作日按流程的 `calendar` |
| `reminders` | `{ "before": 时长 }`（到期前提醒）、`{ "every": 时长, "times": n }`（超时后每隔多久，最多几次） |
| `overdue` | `{ "action": "complete", "outcome": "pass" }`（视为无异议）、`{ "action": "escalate" }`（走这个节点的提级出边）、`{ "action": "mark" }`（只标红，默认） |
| `unclaimed` | 进收件箱后多久没人认领：`{ "after": 时长, "action": "assignByManager" }`（转收件箱负责人指定）或 `"remind"` |

都由 `flow_job` 承载（0001）。

### 5.4 退回与重新提交

- 退回走 `kind: "return"` 的处理结果和连线，目标默认落到那个节点**原来的处理人**（不进收件箱）。
- `returnTo`：被退回的节点重新提交后，`"restart"` 从退回的目标往下照常走，`"resubmit"` 直接回到退回它的节点（0002 §5"退回"）。默认 `"restart"`。
- 退回到发起人、退回到任意上游节点这类"运行时选目标"的退回：处理结果写 `{ "kind": "return", "target": "choose" }`，不要出边，处理时从上游已走过的节点里选（处理命令的 `returnTarget`，待办详情里这个结果的 `targets` 列出能选的）。
  - 能选的：当前节点的上游（沿前进的连线能走到当前节点）、这个实例里走到过的人工节点；不在当前节点之外的并行区域里（旁边的分支、已经汇合过的区域进去了汇合等不齐）。
  - 退回去同沿退回的连线：跳出并行区域时取消区域里的待办，落到那个节点原来的处理人，`returnTo: "resubmit"` 照样生效。
  - 一个能选的都没有时（还在第一个人工节点上）这个结果不给。超时自动完成、会签的 `fallback` 不能用它（没人选目标，发布时报 `outcome-choose-automatic`）；会签时用让节点结束的那个人选的目标。

### 5.5 允许的动作

`allow`：`transfer`（转办）、`delegate`（委派）、`addSign`（加签、减签）、`withdraw`（处理人撤回刚处理的一步）、`remind`（催办）。默认全开，按节点关掉。

### 5.6 由上一步选下一步的处理人

很多审批里"下一步谁来办"由这一步的处理人在提交时选（审批面板的"选择下一处理人"，frontend.md §3.3）。写在**下一步**节点的处理人来源里：

```jsonc
"assignees": [{ "pick": { "from": [{ "role": 1 }], "max": 1 } }]
```

- 上一步提交时，审批面板列出 `from` 解析出的人让他选（`from` 不写时可以选任何人，人员选择器的数据由接入方给）；选的结果就是这个节点的处理人。
- 上一步到这一步之间隔着网关时，面板按选中的处理结果和当前的值算出会走到哪些节点，只为要选人的节点显示选择框。
- 这是给第 3 节的处理人来源加一种（3.2 的表里加一行），其他规则不变。
## 6. 连线、条件、副作用（已定，用户 2026-10-26）

```jsonc
{ "id": "e5", "from": "review", "to": "negotiate", "outcome": "back", "kind": "return",
  "when": { "all": [{ "field": "form.amount", "op": "gt", "value": "10000" }] },
  "effects": [{ "increment": "negotiationRound" }],
  "label": "退回协商" }
```

| 字段 | 说明 |
| --- | --- |
| `from`、`to` | 起止节点 |
| `outcome` | 起点按结果分出边时（第 4 节），哪个结果走这条；不写时任何结果都可以走（再看 `when`） |
| `when` | 条件树：组件库 `Condition` 的 14 种比较方式；比较的 `field` 可以是表单字段、流程变量、引擎值，以及接入方注册的计算值 `fn.名字`（6.1） |
| `default` | 排他、包容网关的默认出边 |
| `kind` | `return`、`escalate`：流程图的画法，以及目标节点怎样分配处理人（退回落原处理人，提级进目标节点的收件箱） |
| `effects` | 走这条线时改流程变量：`{ "set": "team", "value": 值或引用 }`、`{ "increment": "negotiationRound", "by": 1 }` |
| `label` | 流程图上的文字；不写时用处理结果的名字 |

- 选出边的规则沿用旧契约的 `nextProcessEdge`：条件成立的边里，先取写明了这个结果的，再取没写结果的，都没有时走默认边。
- **轮次上限**（0002 §7）不另设字段：连线副作用计数，加一条带条件的出边（`sys.round.negotiate` 或流程变量 `negotiationRound` 大于等于 N 时走提级）。
- **先选边，再改变量**：用走之前的值选出边，走过时应用 `effects`，下一个节点解析处理人时看到的是改过的值。

### 6.1 自定义判断写成计算值

原稿想加一种条件节点 `{ "custom": "名字", "args": {} }`，但组件库的条件构造器只会编辑"字段 + 比较方式 + 值"，要为它改组件库。改为：

- 接入方注册**计算值**：`fn.名字` 由一个函数按当前实例算出来（上下文同解析器：实例、表单的值、流程变量、发起人），声明类型（`number`、`boolean`、`select`……）与显示名。
- 条件里照常比较：`{ "field": "fn.budgetRemaining", "op": "lt", "value": "1000" }`、`{ "field": "fn.isKeySupplier", "op": "eq", "value": true }`。
- 条件构造器把它们当成"计算"这一组字段（`ConditionField` 的 `group`），**组件库不用改**。
- 要带参数的判断，注册成几个计算值，或写在表单的后端代码里算好存进字段。
- 这给第 1 节的 `{ ref }` 名字加一类 `fn.`（第 1 节随第 6 节补入）；`fn.` 只能用在连线条件里（后端求值），表单的 `visibleWhen`、`requiredWhen` 要在前端算，不能用。

### 6.2 条件求值的规则

- 和组件库的 `matchesCondition`、query 的 `conditionBy`（query 的 0017）同一套规则：不等于、不在其中、不包含把空值算作满足；小数、大整数、日期按类型比较。模型包与这两处共用同一套样例。
- 不同的一点：筛选里"没填完整的比较、空的组"当作不加条件，流程里**发布时报错**（`condition-incomplete`），不让一条没写完的条件默默变成"总是成立"。
- 连线条件在后端求值；前端只在预览时求值（5.6 算出会走到哪些节点），用到 `fn.` 时问后端。

## 7. 校验与问题代号（已定，用户 2026-10-26）

一个问题是 `{ code, severity, node?, edge?, lane?, params }`：只给代号和参数，文案在前端的语言包里（frontend.md §2.4）。规则写在模型包里，设计器保存时、后端发布时跑同一份；
要查注册情况的几条（标"发布"）只在后端跑。**错误**不能发布（引擎会走不下去），**警告**可以发布（多半是画漏了）。

### 7.1 沿用旧契约的代号

| 代号 | 严重程度 | 说明 | 变化 |
| --- | --- | --- | --- |
| `start-missing` / `start-multiple` | 错误 | 没有开始节点 / 不止一个 | — |
| `start-incoming` | 错误 | 有线连进开始节点 | — |
| `end-missing` | 错误 | 没有结束节点 | — |
| `end-outgoing` | 错误 | 结束节点有出边 | — |
| `node-duplicate` / `edge-duplicate` / `lane-duplicate` | 错误 | 标识重复 | — |
| `node-type-unknown` | 错误 | 节点类型既不是内置的、也不以 `x-` 开头（多半写错了） | 随实现补 |
| `lane-unknown` | 错误 | 节点的泳道不存在 | — |
| `edge-unknown-node` | 错误 | 连线的起点或终点不存在 | — |
| `node-no-outgoing` | 错误 | 除结束以外的节点没有出边 | — |
| `node-dead-end` | 错误 | 从这个节点走不到任何结束节点 | — |
| `node-unreachable` | 警告 | 从开始走不到这个节点 | — |
| `outcome-duplicate` | 错误 | 同一节点的结果标识重复 | 适用于人工节点、自动节点、外部事件 |
| `outcome-unrouted` | 错误 | 某个结果没有出边（`terminate`、`target: "choose"` 的结果除外） | 同上 |
| `return-target-invalid` | 错误 | 处理结果的 `target` 不是写在退回上、或不是 `choose` | — |
| `outcome-choose-automatic` | 错误 | 超时自动完成、会签的 `fallback` 用了要处理人选退回节点的结果 | — |
| `edge-unknown-outcome` | 错误 | 连线的 `outcome` 在起点上不存在 | 同上 |
| `edge-outcome-not-branching` | 错误 | 起点不按结果分出边，连线却写了 `outcome` | 原 `edge-outcome-not-task`，改名 |
| `edge-default-not-gateway` | 警告 | 起点不是排他或包容网关，连线却写了 `default` | 原 `edge-default-not-exclusive`，改名 |
| `gateway-multiple-default` | 错误 | 网关有不止一条默认出边 | — |
| `gateway-edge-unconditional` | 警告 | 排他、包容网关的出边既没条件也不是默认 | 加上包容 |
| `parallel-edge-condition` | 警告 | 并行网关的出边写了条件（不起作用） | — |
| `parallel-useless` | 警告 | 并行网关只有一条出边、一条入边 | — |
| `overdue-unknown-outcome` | 错误 | 超时自动完成的结果不存在 | — |
| `task-no-outcome` | 错误 | 人工节点没有处理结果 | — |
| `assignee-unknown-node` | 错误 | `sameAs` 指的节点不存在或不是人工节点 | — |
| `assignee-missing` | 错误 | 人工节点没写处理人，泳道也没有收件箱 | 原 `task-no-assignee`，改名 |

### 7.2 新增的代号

| 代号 | 严重程度 | 说明 |
| --- | --- | --- |
| `gateway-unpaired` | 错误 | 并行或包容的分叉没有配对的汇合，或分支跳出了区域；排他分出去的分支进了并行汇合（会卡住）（4.4） |
| `return-not-upstream` | 错误 | 退回连线的目标不在上游 |
| `escalate-no-inbox` | 错误 | 提级连线的目标节点没有收件箱 |
| `ref-unknown` | 错误 | 引用了没声明的流程变量、表单里没有的字段、不存在的节点（`sys.round.节点`） |
| `ref-type-mismatch` | 错误 | 引用的类型用不上（处理人来源取了一个不是 `people` 的字段，时限取了不是日期的字段） |
| `org-id-invalid` | 错误 | 直接写的用户、角色、部门编号不是正整数（或只有数字的文字），多半是写成了名字；编号是接入方组织表的主键 |
| `condition-incomplete` | 错误 | 条件里有没写完的比较或空的组（6.2） |
| `condition-fn-in-form` | 错误 | 表单的显示、必填条件里用了 `fn.`（6.1） |
| `multi-no-fallback` | 错误 | 按比例、按票数的会签没写 `fallback`（5.2） |
| `form-field-unknown` | 错误 | 节点的字段权限引用了表单里没有的字段（0004 的 4.1） |
| `form-required-unfillable` | 警告 | 表单的必填字段在发起节点上是隐藏或只读（0004 的 4.6） |
| `call-cycle` | 错误（发布） | 调用流程形成了环（A 调 B、B 调 A） |
| `call-unknown-process` | 错误（发布） | 调用的流程不存在或已停用 |
| `extension-unknown` | 错误（发布） | 解析器、动作、守卫、计算值没有注册 |
| `due-unknown-calendar` | 错误（发布） | 工作日历不存在 |

## 8. 通知（已定，用户 2026-10-26）

引擎在下面这些时候都发事件（0002 §8），渠道（站内信、邮件、企业微信、钉钉）由接入方注册，引擎只给出收件人、事件与模板参数：

| 事件 | 默认收件人 |
| --- | --- |
| `arrived` 待办到达 | 处理人或收件箱里的候选人 |
| `reminded` 催办 | 当前处理人 |
| `dueSoon` 即将到期、`overdue` 已超时 | 当前处理人；超时另发收件箱的负责人 |
| `cc` 抄送 | 抄送的人 |
| `returned` 被退回 | 退回的目标处理人 |
| `mentioned` 留言里 @ 了（2026-10-27 补） | 被 @ 的人，不含留言的人自己 |
| `finished` 流程结束（带结束结果） | 发起人 |

定义里可以按流程或按节点改（默认全发，这里只是放宽给接入方用的口子）：

```jsonc
"notify": {
  "arrived": { "template": "purchase-arrived", "to": [{ "user": { "ref": "form.buyer" } }] }, // 换模板、多发给谁
  "dueSoon": false                                                                          // 不发
}
```

- 流程级的 `notify` 写在 `settings` 里，节点上的覆盖流程级的。
- `template` 只是一个名字，模板内容和渲染由接入方的渠道负责；不写时渠道用默认模板。
- `to` 是额外的收件人，来源同 3.2，不替换默认收件人。

## 9. 可替换的部分（2026-10-26 按"标准行为都能替换"补）

定义里每一种标准行为都放在接口后面，接入方不满意就换成自己的实现（Java 是接口加 Spring 的默认实现，TS 是选项里传实现）：

| 标准行为 | 怎样替换 |
| --- | --- |
| 处理人解析（3.2 的六种来源加"上一步选"） | 注册解析器扩展一种来源；或替换整个"处理人解析"接口，来源的含义由自己定 |
| 收件箱负责人 | `inbox.manager`，或替换 `inboxManager` |
| 会签的完成规则（5.2 的五种） | 注册新的规则名（`"complete": "my-rule"`），实现"收到一个结果后是否结束、按哪个结果结束" |
| 选出边的规则（6 节沿用 `nextProcessEdge`） | 替换"选边"接口 |
| 条件求值（6.2） | 替换"条件求值"接口；计算值 `fn.` 只是其中最常用的扩展方式 |
| 节点类型（4 节的十一种） | 注册自定义节点类型（`"type": "x-名字"`）：实现到达、完成、超时时做什么；流程图上按注册的外观画 |
| 时限与工作日（5.3） | 替换"工作日历"与"时长计算"接口 |
| 实例标题（2 节的 `title` 模板） | 替换"标题生成"接口 |
| 通知（8 节） | 注册渠道；替换"收件人计算"与"是否发送"接口 |
| 校验（7 节） | 注册自己的规则（同样给代号和参数）；内置的规则可以按代号关掉 |
| 定义里业务自己的数据 | 节点、流程上的 `meta` 原样保存；要更多字段时放 `meta`，引擎不解释 |
