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
  "actors": { "bob": { "name": "周启航", "roles": ["finance"], "departments": [] } },
  "steps": [
    { "id": "start", "as": "alice", "command": "start", "args": { … }, "expect": { … } }
  ]
}
```

- 编号用按顺序发号的主键策略（`SequentialIds`），时钟固定：两个后端跑出来的结果逐字段可比。
- 每个场景开始前清空库、按建表脚本重建。

## 命令与参数

| command | args | 结果里能比的 |
| --- | --- | --- |
| `start` | `key`、`form`、`variables`、`businessKey`、`title`、`priority` | `instance`、`workItems`（新生成的待办）、`closed`（结束的待办）、`events`（这一步的流转记录） |
| `claim` | `workItem` | 同上 |
| `complete` | `workItem`、`outcome`、`comment`、`form` | 同上 |
| `withdraw` | `instance`、`reason` | 同上 |
| `todo` | `page`（默认 1）、`size`（默认 20） | `total`、`rows` |
| `instance` | `instance` | `instance`、`form`、`workItems`（全部待办）、`timeline` |
| `publishDefinition` | `definition`（内联的定义） | — |

## 引用前面的结果

参数里以 `$` 开头的文字是引用：`$start.workItems[0]` 是 `id` 为 `start` 的那一步新生成的第一个待办的编号，
`$start.instance` 是那一步的实例编号；`$.` 指上一步。

## 期望（expect）

- **只比写出来的字段**：对象里没写的键不比；列表要一样长，逐项按同样的规则比；`null` 表示这个字段为空或没有。
- `events`、`timeline` 写成事件种类的列表，按先后逐个比。
- 期望出错时写 `"error": { "code": "flow.…", "errors": [ … ] }`；没写 `error` 的步骤出错就算失败。
- 值按线上的 JSON 比：编号、小数、时间都是文字（`"12000.00"`）。
