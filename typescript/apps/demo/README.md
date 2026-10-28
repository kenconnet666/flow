# 流程引擎的示例站点

演示与文档在同一个站点里，部署出去给人边看边试：

- **文档**（`/docs`）：介绍与快速体验、日常使用、搭建流程（设计器入门与六个教程）、表单、管理、开发（接入与扩展），配图是在演示上真实操作截的。
- **演示**（`/app`）：用 `@zerodep-dev/flow-ui` 搭的审批应用——发起、我的待办、办理、我已办、我发起的、抄送我的、流程详情，以及管理端（流程监控、异常处理、流程与表单的设计、工作日历、统计）。
- **边看文档**：演示右边打开文档面板（宽屏时分栏、能拖动比例，窄屏时是抽屉）；教程开头的"在演示里边看边做"直接跳到开始操作的那一页。
- **重置演示**：顶栏的按钮清空示例后端里引擎的数据，重新发布种子，两档——"从头开始"（只有示例流程，照着教程从零画）与"教程做完"（六个教程的流程都在，还有几单在办）。
- 顶栏切换当前用户，请求头 `X-User` 带上他的编号（只为演示）。

| 用户 | 编号 | 身份 |
| --- | --- | --- |
| 林小满 | 1001 | 门店员工，教程里的发起人 |
| 周启航、沈若水 | 1002、1003 | 财务部，角色"财务" |
| 总经理 | 1004 | 角色"总经理"，也是流程管理员 |
| 许知远 | 1005 | 总经办，教程里当法务、技术评审 |

## 跑起来

1. 起示例后端 `examples/spring-boot`（本机用 `.claude/run-example-backend.sh`，连 `infra/mysql` 起的 MySQL）。
2. 组件库 vue-tsx-pro 要在本仓库的旁边（`../vue-tsx-pro`），装好依赖。
3. 在 `typescript/` 下 `pnpm install`，然后：

```bash
pnpm -C apps/demo seed            # 重置成"从头开始"；加 tutorials 是"教程做完"（同页面上的重置演示，部署后可以定时跑）
pnpm -C apps/demo dev             # http://localhost:5180，/flow、/demo 转到 8080
```

## 目录

| 目录 | 内容 |
| --- | --- |
| `src/pages/` | 演示的各页（每页一个 flow-ui 组件加上"点了去哪"） |
| `src/docs/pages/<分组>/` | 文档，每页一个 TSX 文件，导出 `meta`（标题、简介、顺序，`try` 是边看边做时开始的演示页） |
| `src/docs/site/` | 文档的骨架：导航 `nav.ts`、文档页 `DocPage`、配图 `Shot`、操作步骤 `Ops`、表格 `Tbl`、面板状态 `panel.ts` |
| `src/shell/` | 外壳上的部件：重置演示 |
| `seed/` | 种子：`demo-seed.ts`（页面与命令行共用）、请假单与扩展演示的定义、`tutorials.json`（教程做完那一档） |
| `shots/` | 截图脚本（Playwright，按教程一步步在演示上真实操作、边做边截），图存 `public/shots/` |
| `e2e/` | 端到端测试 |

## 重新截图

截图要在干净的数据上跑，跑完顺便导出"教程做完"那一档：

```bash
pnpm -C apps/demo seed            # 从头开始
pnpm -C apps/demo shots           # 按教程操作并截图（build、use、forms、admin 按顺序）
pnpm -C apps/demo seed:export     # 把教程画出的流程与报销单导出成 seed/tutorials.json
```

文档内容以仓库里的设计文档（`docs/decisions/0001`–`0008`）与接入文档（`docs/integration.md`）为准。
