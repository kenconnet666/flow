# 流程引擎的示例前端

用 `@zerodep-dev/flow-ui` 搭的最小审批应用：发起、我的待办、办理（表单、意见与处理结果、流转记录）。
接示例后端（Vite 把 `/flow` 转到 `http://localhost:8080`）：Spring Boot 的 `examples/spring-boot` 或 Node 的 `apps/demo-server`，两个的接口相同，开哪个都行；顶栏切换当前用户，请求头 `X-User` 带上他的编号（只为演示）。

| 用户 | 编号 | 能做的 |
| --- | --- | --- |
| 林小满 | 1001 | 发起采购、请假 |
| 周启航 | 1002 | 财务（角色 1）：认领并复核采购 |
| 总经理 | 1004 | 审批大额采购（金额 ≥ 10000）、请假 |

## 跑起来

1. 起示例后端：Spring Boot（见 `examples/spring-boot/README.md`，本机用 `.claude/run-example-backend.sh`）或 Nitro（见 `apps/demo-server/README.md`，本机用 `.claude/run-node-backend.sh`）。
2. 组件库 vue-tsx-pro 要在本仓库的旁边（`../vue-tsx-pro`），装好依赖、构建过编译器（`pnpm --filter @zerodep-dev/vue-tsx-pro-tsx build`）。
3. 在 `typescript/` 下 `pnpm install`，然后：

```bash
pnpm -C apps/demo seed
```

```bash
pnpm -C apps/demo dev
```

`seed` 发布三个流程：**采购**（表单只有字段清单，按清单自动排版；财务复核后金额大的再给总经理）、**请假**
（表单是代码 `seed/leave-form.tsx`，清单从源码里取出，页面里在浏览器中编译）与**扩展演示**（用示例后端 `extension` 包里注册的扩展：
外部核验节点 → 按计算值"金额档位"分支 → 大额由解析器"发起人的上级"处理、提交前检查金额不超过一百万 → 推送外部系统；
在流程管理里打开它，能看到设计器怎样列出这些扩展）。再跑一次就是各自的新版本。
