# Nitro 示例后端

一个最小的 Nitro 应用（Nuxt 的服务端就是 Nitro）接上流程引擎，与 `examples/spring-boot` 做的事相同、接口相同：
示例前端 `apps/demo` 接哪个都行。Nuxt 应用里同样的文件放在 `server/` 下。

## 应用要写的

| 文件 | 做什么 |
| --- | --- |
| `server/flow.ts` | 建引擎：连接池、组织架构（写死的几个人）、当前操作人（请求头 `X-User`，**只为演示**）、通知（打日志）、自动节点的动作 `demo.push` |
| `server/routes/flow/[...path].ts` | 引擎的 HTTP 接口：`createFlowHandler({ engine, actor })`，挂在 `/flow` |
| `server/plugins/flow.ts` | 启动时建表（`migrateFlowSchema`），建好后定时做到期的作业（`startFlowJobs`），关闭时停掉 |

## 跑起来

1. 准备一个 MySQL 8 的库，设好环境变量 `FLOW_MYSQL_URL`（`jdbc:mysql://主机:端口/库`，同 Java 示例的写法）、`FLOW_MYSQL_USER`、`FLOW_MYSQL_PASSWORD`。
   表已由 Java 一侧的 Flyway 建好时设 `FLOW_MIGRATE=false`：两个后端读写同一套表。
2. 在 `typescript/` 下 `pnpm install`、`pnpm build`（引擎各包按编译结果引用），然后：

```bash
pnpm -C apps/demo-server dev
```

接口在 `http://localhost:8080/flow`，用法见 `examples/spring-boot/README.md` 的"接口"一节；发布示例的表单与流程用 `pnpm -C apps/demo seed`。
