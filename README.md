# flow

嵌进业务系统用的流程引擎：Java 后端（不依赖 Spring，另有 Spring Boot 自动配置）、MySQL 存储（easy-query），
Vue 3 前端组件（审批、流程图、流程与表单设计器，建在 vue-tsx-pro 上）。审批流里常见的都有：会签、或签、按比例与票数、
退回到任意节点、转办委派加签、抄送、撤回、催办、定时与超时、子流程、外部事件、表单的字段权限与前后端代码。

## 接进自己的项目

看 [docs/integration.md](docs/integration.md)：后端引入 `flow-spring`、写组织架构与当前操作人；前端在页面里放组件。

## 仓库里有什么

| 目录 | 内容 |
| --- | --- |
| `java/flow-model` | 模型包：流程定义、表单、条件与选线规则、错误码（前后端共用一份规则） |
| `java/flow-core` | 引擎（[README](java/flow-core/README.md) 有包的分工与"一次处理怎么跑"） |
| `java/flow-spring` | Spring Boot 自动配置、HTTP 接口、作业轮询 |
| `java/flow-it` | 连真实 MySQL 的测试：场景用例（`spec/scenarios`）、并发、多租户、谁能看实例、归档、数据量探针 |
| `typescript/packages/model` | 模型包的 TypeScript 版 |
| `typescript/packages/ui` | 前端组件（`@zerodep-dev/flow-ui`） |
| `typescript/apps/demo` | 示例前端 |
| `examples/spring-boot` | 示例后端 |
| `spec/scenarios` | 前后端共用的场景用例（流程、表单、一步步的命令与期望） |
| `docs/decisions` | 设计决定（0001–0008） |
| `infra/mysql` | 开发用的 MySQL（Docker） |

## 本机跑起来

```bash
# 开发库（WSL 里的 Docker），连接变量从 infra/mysql/.env 读，不进仓库
. infra/mysql/live-env.sh
./mvnw -o test -pl java/flow-it                    # 后端的实库测试
FLOW_MYSQL_DATABASE=flow_demo . infra/mysql/live-env.sh && ./mvnw -pl examples/spring-boot -am spring-boot:run
cd typescript && pnpm install
pnpm -C apps/demo seed                            # 给示例后端发布采购、请假两个流程与表单
pnpm -C apps/demo dev                             # 示例前端 http://localhost:5180，/flow 转到 8080
```

现在的进度、已定的取舍与还没做的在 [HANDOFF.md](HANDOFF.md)。
