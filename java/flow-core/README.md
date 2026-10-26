# flow-core

流程引擎的 Java 实现，不依赖 Spring（Spring Boot 用 `flow-spring`）。规则（条件、选线、字段权限、校验）在模型包 `flow-model`，两个后端共用。

## 一次"处理"是怎么跑的

```
FlowEngine.complete(actor, command)
  │ 先问 PermissionPolicy
  ▼
CompleteHandler.handle            （一个事务）
  1. 锁住实例行                   InstanceStore.lock：版本加一，并发的处理在这里排队
  2. 重读待办                     已处理的报 flow.work-item-not-open；还在收件箱里的先认领
  3. 核对结果与意见               结果要是节点上写的；意见必填的要填
  4. 收表单                       FormSubmission：按节点上的字段权限合并、校验
  5. 待办改成已处理               WorkItemStore.complete：带条件的更新，改到 0 行就是别人先处理了
  6. 往下走                       Execution.leave：选线 → 进下一个节点 → 生成待办 / 结束
  7. 写回                         Execution.flush：重算标题与摘要列，写回实例，写入流转记录
  ▼
CommandResult { instance, workItems（新生成的）, closed（结束的）, events（流转记录） }
```

发起（`StartHandler`）、认领（`ClaimHandler`）、撤回（`WithdrawHandler`）是同样的结构，步骤写在各自的类注释里。

## 包

| 包 | 管什么 |
| --- | --- |
| `org.dpdns.zerodep.flow.core` | `FlowEngine`：门面与组装 |
| `identity` | 接入方实现的：操作人、当前操作人、组织架构、权限 |
| `command` | 命令的参数与结果 |
| `data` | 表单的值、流程变量、运行状态 |
| `engine` | 引擎内部：每个命令一个处理类，实例往前走在 `Execution` |
| `store` | 六个存储接口，每个都能单独替换 |
| `store.query` | 存储的默认实现（query） |

建表脚本在 `src/main/resources/db/flow/mysql`，推荐交给 Flyway 执行（docs/decisions/0007 第 9 节）。
