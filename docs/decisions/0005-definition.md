# 0005 流程定义 JSON

- 状态：讨论中（2026-10-26 起草），逐节定稿；第 1 节已定（2026-10-26）
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

很多地方的值可以直接写，也可以取别处的值。统一写成 `{ "ref": "名字" }`，名字分三类：

| 写法 | 指的是 | 例子 |
| --- | --- | --- |
| `form.字段名` | 表单的值（0004 的 5.5） | `form.amount`、`form.buyer`、`form.lines` |
| `sys.名字` | 引擎给的只读值 | `sys.initiator`（发起人）、`sys.startedAt`、`sys.round.节点`（这个节点第几轮） |
| 变量名 | 流程变量（第 2 节 `variables` 里声明的） | `team`、`negotiationRound` |

- 条件树里比较的 `field` 用同样的名字（与组件库的 `Condition` 同形：`{ field, op, value }` 加 `all` / `any` / `not`）。旧契约里的 `var` 改成 `field`（0003 已定）。
- 流程变量名不能带点，和另外两类分得开。

## 2. 顶层

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
  "settings": { /* 第 2.1 节 */ },
  "lanes": [ /* 第 3 节 */ ],
  "nodes": [ /* 第 4、5 节 */ ],
  "edges": [ /* 第 6 节 */ ],
  "layout": { "nodes": { "check": { "x": 320, "y": 80 } }, "edges": { "e3": { "points": [[400, 40]] } } }
}
```

- `variables[].type` 用表单字段类型的一部分：`text`、`number`、`decimal`、`boolean`、`date`、`datetime`、`select`、`people`（0004 的 1.3）。
- 没有表单的流程可以不写 `form`。

### 2.1 流程级设置

| 设置 | 说明 | 默认 |
| --- | --- | --- |
| `calendar` | 用哪份工作日历（0002 §7） | `"default"` |
| `withdraw` | 发起人撤回整个实例：`"beforeClaim"`（第一个待办被认领前）、`"beforeAction"`（任何人处理前）、`"never"` | `"beforeAction"` |
| `emptyAssignee` | 处理人解析为空时：`"autoPass"`（自动通过）、`"admin"`（转给管理员）、`"error"`（发起或流转失败） | `"admin"` |
| `autoSkip` | 自动跳过：`{ "sameAsPrevious": true, "initiator": false }`（处理人和上一步相同、处理人就是发起人时自动通过） | 都关 |
| `formView` | 不在待办里看表单的人（发起人看进度、抄送、历史）对哪些字段隐藏：`{ "cost": "hidden" }`（0004 的 4.3） | 全部只读 |

节点上可以覆盖 `emptyAssignee`、`autoSkip`。

## 3. 泳道与处理人

### 3.1 泳道

```jsonc
{ "id": "finance", "name": "财务部", "inbox": { "position": "finance-review" } }
```

- 泳道是流程图上的一行，通常对应一个岗位；`inbox` 是这一行里人工节点没写处理人时的默认去处（岗位收件箱）。
- 旧契约的 `group` 改成 `inbox: { position }`，和组件库 `PeoplePicker` 的类型（user / department / position）对齐。

### 3.2 处理人

人工节点的 `assignees` 是一个或多个来源，取并集：

| 来源 | 写法 | 落到哪 |
| --- | --- | --- |
| 岗位收件箱 | `{ "position": "finance-review" }`，或 `{ "position": { "ref": "form.reviewPosition" } }` | 岗位里的人认领，先认领先得 |
| 指定的人 | `{ "user": "u1001" }`，或 `{ "user": { "ref": "form.buyer" } }` | 直接落到人；`people` 类型的值里有几个人就是几个候选 |
| 部门 | `{ "department": "d20" }` | 部门里的人认领（怎么展开由接入方的解析器定） |
| 发起人 | `{ "initiator": true }` | 发起人 |
| 同某节点 | `{ "sameAs": "check" }` | 那个节点最后的处理人 |
| 解析器 | `{ "resolver": "deptLeader", "args": { "level": 2 } }` | 接入方注册的扩展点：部门负责人、上级第 N 级、角色…… |

- 解析出来只有一个人：直接落到他；多于一个人或含岗位、部门：进收件箱，候选人认领。
- 解析为空时按 `emptyAssignee`。
- 不写 `assignees` 时用所在泳道的 `inbox`；两者都没有时发布校验报错。

## 4. 节点

所有节点共有：`id`（流程内唯一）、`type`、`name`、`lane`、`description`、`meta`（业务附加信息，引擎原样保存不解释，如对应的业务状态）、`offline`（线下动作：流程图画虚线框，平台上仍要有人回填结果）。

| `type` | 是什么 | 自己的字段 | 出边按什么分 |
| --- | --- | --- | --- |
| `start` | 开始，唯一 | — | 一条出边 |
| `end` | 结束 | `result`（通过、否决、搁置……业务据此判断怎么结束的）、`terminate`（到这里取消并行的其他分支） | 没有出边 |
| `task` | 人工节点 | 第 5 节 | 处理结果（`outcome`），再看条件 |
| `exclusive` | 排他网关 | — | 条件，按先后取第一条成立的，都不成立走 `default` |
| `parallel` | 并行网关 | — | 分叉时每条都走；汇合时等所有入边 |
| `inclusive` | 包容网关 | — | 条件成立的都走；汇合时只等实际走了的分支 |
| `service` | 自动节点 | `action`（接入方注册的动作名）、`args`（JSON）、`retry`（`{ "times": 3, "backoff": "PT1M" }`）、`onFailure`（`"manual"` 转人工处理、`"error"` 实例标成出错） | 动作返回的结果（`outcome`），或一条出边 |
| `call` | 调用流程（子流程） | `process`（流程 key）、`mode`（`"sync"` 等子流程结束、`"async"` 发起后不等）、`input`（`{ "子流程变量或 form.字段": 值 }`）、`output`（同步时把子流程的值写回） | 同步时按子流程结束的 `result`（`outcome`） |
| `timer` | 定时等待 | `wait`：`{ "workdays": 2 }`、`{ "days": 1 }`、`{ "hours": 4 }`，或 `{ "until": { "ref": "form.signDate" } }` | 一条出边 |
| `receive` | 等待外部事件 | `event`（事件名）、`outcomes`（事件可能带回的结果列表）、`due`（等多久，超时按第 5.3 节） | 事件带回的结果（`outcome`） |
| `cc` | 抄送节点 | `to`（同 3.2 的来源）、`message` | 一条出边，不阻塞 |

- "出边按什么分"里的 `outcome` 是同一个概念：人工节点的处理结果、自动节点与子流程返回的结果、外部事件带回的结果。出边都用 `outcome` 选，都可以再加条件。
- `call` 的 `process` 发起时取子流程的最新版本；父子关系与来源写进实例（0001 的 `flow_instance`）。

## 5. 人工节点

```jsonc
{
  "id": "review", "type": "task", "name": "财务复核", "lane": "finance",
  "assignees": [{ "position": "finance-review" }],
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
| `votes` | 选同一结果的票数达到 `votes`；参与人可带权重（`{ "user": "u1", "weight": 2 }`） |
| `sequential` | 一个接一个，全部处理完 |

- 节点按哪个结果结束：`veto` 的结果优先；否则 `ratio` / `votes` 取先达到的结果；`all` / `sequential` 时有人选了非 `approve` 的结果就按人数最多的那个（待讨论）。
- 运行时的加签、减签按 `allow.addSign` 控制。

### 5.3 时限、提醒、超时、无人认领

| 字段 | 说明 |
| --- | --- |
| `due` | `{ "workdays": n }`、`{ "days": n }`、`{ "hours": n }`、`{ "until": { "ref": "form.deadline" } }`；工作日按流程的 `calendar` |
| `reminders` | `{ "before": 时长 }`（到期前提醒）、`{ "every": 时长, "times": n }`（超时后每隔多久，最多几次） |
| `overdue` | `{ "action": "complete", "outcome": "pass" }`（视为无异议）、`{ "action": "escalate" }`（走这个节点的提级出边）、`{ "action": "mark" }`（只标红，默认） |
| `unclaimed` | 进收件箱后多久没人认领：`{ "after": 时长, "action": "assignByManager" }`（转岗位负责人指定）或 `"remind"` |

都由 `flow_job` 承载（0001）。

### 5.4 退回与重新提交

- 退回走 `kind: "return"` 的处理结果和连线，目标默认落到那个节点**原来的处理人**（不进收件箱）。
- `returnTo`：被退回的节点重新提交后，`"restart"` 从退回的目标往下照常走，`"resubmit"` 直接回到退回它的节点（0002 §5"退回"）。默认 `"restart"`。
- 退回到发起人、退回到任意上游节点这类"运行时选目标"的退回：处理结果写 `{ "kind": "return", "target": "choose" }`，处理时从上游已走过的节点里选；校验保证只能选上游。

### 5.5 允许的动作

`allow`：`transfer`（转办）、`delegate`（委派）、`addSign`（加签、减签）、`withdraw`（处理人撤回刚处理的一步）、`remind`（催办）。默认全开，按节点关掉。

## 6. 连线、条件、副作用

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
| `when` | 条件树：组件库 `Condition` 的 14 种比较方式，加接入方注册的自定义判断 `{ "custom": "名字", "args": {} }` |
| `default` | 排他、包容网关的默认出边 |
| `kind` | `return`、`escalate`：流程图的画法，以及目标节点怎样分配处理人（退回落原处理人，提级进上级岗位的收件箱） |
| `effects` | 走这条线时改流程变量：`{ "set": "team", "value": 值或引用 }`、`{ "increment": "negotiationRound", "by": 1 }` |
| `label` | 流程图上的文字；不写时用处理结果的名字 |

- 选出边的规则沿用旧契约的 `nextProcessEdge`：条件成立的边里，先取写明了这个结果的，再取没写结果的，都没有时走默认边。
- **轮次上限**（0002 §7）不另设字段：连线副作用计数，加一条带条件的出边（`sys.round.negotiate` 或流程变量 `negotiationRound` 大于等于 N 时走提级）。

## 7. 校验与问题代号

旧契约的问题代号全部保留（`start-missing`、`outcome-unrouted`、`gateway-multiple-default`、`node-unreachable` 等），文案移到语言包（frontend.md §2.4）。新增：

| 代号 | 严重程度 | 说明 |
| --- | --- | --- |
| `gateway-unpaired` | 错误 | 并行或包容的分叉没有配对的汇合；排他分出去的分支进了并行汇合（会卡住） |
| `return-not-upstream` | 错误 | 退回连线的目标不在上游 |
| `escalate-no-inbox` | 错误 | 提级连线的目标节点没有收件箱 |
| `assignee-missing` | 错误 | 人工节点没写处理人，泳道也没有收件箱 |
| `ref-unknown` | 错误 | 引用了没声明的流程变量、表单里没有的字段 |
| `extension-unknown` | 错误 | 解析器、动作、守卫、自定义判断没有注册（发布时由后端查） |
| `form-field-unknown` | 错误 | 节点的字段权限引用了表单里没有的字段（0004 的 4.1） |
| `form-required-unfillable` | 警告 | 表单的必填字段在发起节点上是隐藏或只读（0004 的 4.6） |
| `call-cycle` | 错误 | 调用流程形成了环（A 调 B、B 调 A） |
| `due-unknown-calendar` | 错误 | 工作日历不存在 |

## 8. 待讨论

1. 第 2 节：顶层字段与流程级设置，各项默认值。
2. 第 3 节：处理人来源的写法；"一个人直接落、多人进收件箱"的规则。
3. 第 4 节：节点类型清单，`outcome` 统一表示各类节点的结果。
4. 第 5 节：人工节点，尤其会签在 `all` / `sequential` 下有人不同意时按哪个结果结束。
5. 第 6 节：自定义判断 `{ "custom" }` 要组件库的条件构造器支持（现在只有字段比较）。
6. 第 7 节：问题代号清单。
7. 通知：事件都发（0002 §8），定义里要不要按节点关掉某些通知、指定模板。
