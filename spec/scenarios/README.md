# 场景用例

两个后端（Java 的 `java/flow-it`、TS 的实库测试）都逐个文件跑这里的场景，跑出来的结果按同一份 JSON 比较，
所以它们就是两个后端共同的验收标准（docs/decisions/0001 的"场景用例的格式"）。新加一个场景只加一个 JSON 文件。

## 文件的样子

```jsonc
{
  "name": "一句话说明这个场景验证什么",
  "forms": ["forms/purchase-form.json"],      // 先发布的表单：{ key, name, manifest }
  "processes": ["processes/purchase.json"],   // 再发布的流程定义（0005 的 JSON）
  "clock": "2026-10-26T08:00:00Z",            // 固定时钟
  "actors": { "bob": { "id": 1002, "name": "周启航", "roles": [1], "departments": [] } },   // 键是步骤里 as 用的代号
  "steps": [
    { "id": "start", "as": "alice", "command": "start", "args": { … }, "expect": { … } }
  ]
}
```

- 编号是数据库自增的（每个场景前重建表，从 1 开始），时钟固定：两个后端跑出来的结果逐字段可比。
- 每个场景开始前清空库、按建表脚本重建。
- 操作人：键是代号（步骤里的 `as`），`id`、`roles`、`departments` 是接入方组织表里的编号（整数）。各场景用同一套：
  1000 管理员、1001 林小满、1002 周启航（财务）、1003 沈若水、1004 总经理、1005 韩笑（法务）；角色 1 财务、2 总经理、3 法务、9 收件箱负责人（有它的人管所有收件箱的指定）；部门 10 门店、20 财务部、30 总经办。1000 是流程管理员（处理人为空、写了 emptyAssignee: admin 时转给他）。

## 命令与参数

| command | args | 结果里能比的 |
| --- | --- | --- |
| `start` | `key`、`form`、`variables`、`businessKey`、`title`、`priority`、`picks`（`{ 节点: [用户编号] }`） | `instance`、`workItems`（新生成的待办）、`closed`（结束的待办）、`events`（这一步的流转记录） |
| `claim` | `workItem` | 同上 |
| `complete` | `workItem`、`outcome`、`comment`、`form`、`picks` | 同上 |
| `withdraw` | `instance`、`reason` | 同上 |
| `withdrawStep` | `workItem`、`reason` | 同上（处理人撤回刚处理的一步） |
| `startable` | — | `processes`（每项 `{ key, name, category, version }`） |
| `startView` | `key` | `process`、`form`（`{ key, version, manifest, access, start, values, … }`，没有表单时为 null）、`picks` |
| `todo`、`done` | `page`（默认 1）、`size`（默认 20） | `total`、`rows`（每行 `{ workItem, title, summary, status, outcomeLabel }`） |
| `started` | 同上 | `total`、`rows`（每行是实例） |
| `instances` | `status`、`processKey`、`keyword`、`startedBy`、`page`、`size` | 同上（管理端搜实例） |
| `processes` | — | `processes`（每项 `{ key, name, category, status, latestVersion }`） |
| `incidents` | `instance`（不写时看全部）、`page`、`size` | `total`、`rows`（异常：重试用完的作业，带节点、能不能跳过、能选的结果） |
| `workItem` | `workItem` | `workItem`、`instance`、`form`、`outcomes`、`claimable`、`completable`、`actions` |
| `instance` | `instance` | `instance`、`form`（同发起页的 form，按流程级 formView 只读）、`workItems`（全部待办）、`timeline`、`actions` |
| `transfer`、`delegate`、`reassign` | `workItem`、`user`、`reason` | 同 `start` |
| `addSigners` | `workItem`、`users`、`mode`（before / after / parallel）、`reason` | 同 `start` |
| `removeSigners` | `workItem`、`users` | 同 `start` |
| `release` | `workItem`、`reason` | 同 `start` |
| `remind` | `instance`、`workItem`（不写时催实例上全部打开着的）、`message` | 同 `start` |
| `comment` | `instance`、`text` | 同 `start` |
| `cc` | `instance`、`users`、`message` | 同 `start` |
| `readCc` | `instance` | `read`（标了几条） |
| `ccList` | `page`、`size` | `total`、`rows`（每行 `{ cc, title, summary, status }`） |
| `terminate`、`suspend` | `instance`、`reason` | 同 `start` |
| `resume` | `instance` | 同 `start` |
| `jump` | `instance`、`node`、`reason` | 同 `start` |
| `signal` | `instance` 或 `businessKey`、`event`、`outcome`、`data` | `results`（每个往下走或记了一笔的实例这一步的结果，同 `start`） |
| `assign` | `workItem`、`user` | 同 `start`（收件箱负责人指定承办人） |
| `designations` | `page`、`size` | 同 `todo`（等我指定承办人的） |
| `retryJob` | `job`（作业编号，`job.failed` 的 data 里有） | `revived` |
| `skipJob` | `job`、`outcome`、`reason` | 同 `complete`（跳过失败的作业，按结果往下走） |
| `runJobs` | `at`：把时钟拨到这一刻（不能往回拨），做到期的作业；这一步不写 `as` | `ran`（做了几个作业） |
| `publishDefinition` | `definition`（内联的定义） | — |
| `validateDefinition` | `definition` | `problems`（每项 `{ code, severity, node, edge, lane, field, params }`） |
| `disableProcess`、`enableProcess` | `key` | 流程（`{ key, name, category, status, latestVersion }`） |
| `versions` | `key` | `versions`（新的在前，每项 `{ version, note, publishedByName, publishedAt, formKey, formVersion }`） |

## 场景里注册的扩展

两个后端跑场景时都注册这一套（Java 在 `flow-it` 的 `TestExtensions`），定义里可以直接用：

| 种类 | 名字 | 做什么 |
| --- | --- | --- |
| 解析器 | `manager` | 谁发起都由 1004（总经理）处理 |
| 守卫 | `budget` | 表单的 `amount` 超过 100000 时不通过，原因"超出预算" |
| 动作 | `classify` | 参数 `amount` ≥ 10000 走结果 `big`，否则 `small`；记流程变量 `level` |
| 动作 | `archive` | 同一个去重键的前 `failures`（参数）次失败，之后成功 |
| 动作 | `explode` | 总是失败 |

## 引用前面的结果

参数里以 `$` 开头的文字是引用：`$start.workItems[0]` 是 `id` 为 `start` 的那一步新生成的第一个待办的编号，
`$start.instance` 是那一步的实例编号；`$.` 指上一步。

## 期望（expect）

- **只比写出来的字段**：对象里没写的键不比；列表要一样长，逐项按同样的规则比；`null` 表示这个字段为空或没有。
- `events`、`timeline` 写成事件种类的列表，按先后逐个比。
- 期望出错时写 `"error": { "code": "flow.…", "errors": [ … ] }`；没写 `error` 的步骤出错就算失败。
- 值按线上的 JSON 比：编号、小数、时间都是文字（`"12000.00"`）。
