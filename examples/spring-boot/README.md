# Spring Boot 示例

一个最小的 Spring Boot 应用接上流程引擎：引入 `flow-spring`，写组织架构与当前操作人，在 `application.yml` 里打开建表与 HTTP 接口。

## 应用要写的

| 文件 | 做什么 |
| --- | --- |
| `ExampleUser` | 自己的用户类型，实现引擎的 `Actor`（编号、名字、部门、角色） |
| `ExampleOrgDirectory` | 组织架构：按编号查名字（引擎没有默认的组织架构） |
| `ExampleActors` | 当前操作人：示例从请求头 `X-User` 取，**只为演示**；用 Spring Security 且 principal 实现了 `Actor` 时不用写 |
| `application.yml` | 数据源；`flow.flyway.enabled` 启动时建表；`flow.http.enabled` 打开接口 |

## 跑起来

1. 准备一个 MySQL 8 的空库，设好环境变量 `FLOW_MYSQL_URL`、`FLOW_MYSQL_USER`、`FLOW_MYSQL_PASSWORD`。
2. 在仓库根目录：

```bash
./mvnw -pl examples/spring-boot -am spring-boot:run
```

3. 发布表单与流程：用 `spec/scenarios` 里的采购流程，文件原样作为请求体（在仓库根目录执行）：

```bash
curl -s -X POST localhost:8080/flow/forms/publish -H 'X-User: 1001' -H 'Content-Type: application/json' --data-binary @spec/scenarios/forms/purchase-form.json
curl -s -X POST 'localhost:8080/flow/definitions/publish?note=v1' -H 'X-User: 1001' -H 'Content-Type: application/json' --data-binary @spec/scenarios/processes/purchase.json
```

## 接口

HTTP 一律 200，成败看响应体的 `code`（`ok`，或 `flow.…`、`query.…` 的业务错误码），见 docs/decisions/0008。

```bash
curl -s -X POST localhost:8080/flow/commands/start -H 'X-User: 1001' -H 'Content-Type: application/json' \
  -d '{"key":"purchase","form":{"title":"办公桌","amount":"12000.00"}}'
curl -s 'localhost:8080/flow/queries/todo?page=1&pageSize=20' -H 'X-User: 1002'
curl -s -X POST localhost:8080/flow/commands/complete -H 'X-User: 1002' -H 'Content-Type: application/json' \
  -d '{"workItemId":"…","outcome":"pass"}'
```
