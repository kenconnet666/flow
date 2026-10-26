# 0008 业务错误码

- 状态：已定（用户 2026-10-26：码按 A，带命名空间的文字）。错误一律是业务级别的错误码，HTTP 一律 200；flow、query、vue-tsx-pro 都守；错误码由合适的包提供，各处互相对应（用户 2026-10-26）
- 影响：0006 第 8 节（原来按代号映射 404 / 403 / 409 / 422 / 500，改为一律 200）；query 两端的异常；vue-tsx-pro 的 `http`

## 1. 响应的形状

HTTP 一律 200（网关、登录这些我们管不到的层面除外），成功与失败都看响应体里的码：

```jsonc
// 成功
{ "code": "ok", "data": { ... } }
// 失败
{ "code": "flow.work-item-not-open", "message": "待办已处理", "params": { "workItem": "123" } }
// 校验类的失败带明细
{ "code": "flow.validation-failed", "message": "表单校验不通过", "errors": [{ "field": "amount", "code": "max" }] }
```

| 字段 | 说明 |
| --- | --- |
| `code` | 成功是 `ok`；失败是业务错误码（第 2 节） |
| `message` | 后端给的说明，只作兜底；前端优先按 `code` 从语言包取文案，`params` 填进去 |
| `params` | 文案里要填的参数 |
| `errors` | 按字段、按问题的明细：表单校验（0004 的 `SubmissionError`）、发布校验（0005 的问题列表） |
| `data` | 成功时的数据 |

## 2. 错误码的写法（已定：A）

| 做法 | 例子 | 好处 | 代价 |
| --- | --- | --- | --- |
| **A. 带命名空间的文字（建议）** | `flow.work-item-not-open`、`query.unexpected-row-count`、`http.timeout` | 一眼看懂；按包分命名空间，各包自己加码不会撞；和 0005 的问题代号、0004 的值问题同一种写法 | 比数字长 |
| B. 数字 | `0` 成功；`41001` 待办已处理（前两位是包，后三位是序号） | 国内很多系统这样写，接入方的网关、日志可能已经按数字处理 | 要维护号段表；读日志要查表；新包要分配号段 |

## 3. 谁提供哪些码

每个包在自己的仓库里放一份清单（JSON），Java 与 TS 的常量从它来、测试核对两边一致；前端的语言包按同一份清单给文案。

| 命名空间 | 由谁提供 | 码 |
| --- | --- | --- |
| `flow.` | flow 的模型包（`FlowErrorCode`，Java 与 TS） | 0006 第 8 节的那些：`not-found`、`forbidden`、`not-candidate`、`already-claimed`、`work-item-not-open`、`instance-not-active`、`concurrent-modification`、`guard-rejected`、`validation-failed`、`definition-invalid`、`extension-failed`，加 `internal-error`（没认出来的异常） |
| `query.` | query 的核心包（Java 与 TS 的异常各带 `code()`） | `unexpected-row-count`、`empty-result`、`too-many-rows`、`execution-failed`、`request-invalid`（表格请求读不懂）、`batch-write-failed`、`transaction-closed`、`invalid-row` |
| `http.` | vue-tsx-pro 的 `http`（只在前端产生，后端不发） | `network`（连不上）、`timeout`、`aborted`、`status`（网关返回了非 200）、`parse`（响应不是 JSON 或没有 `code`） |

- **互相对应**：同一个错误在 Java、TS、前端是同一个码。flow 的 HTTP 接口遇到 query 的异常时原样用它的 `query.` 码，不另起名字。
- **文案**：`flow.` 的文案在 flow-ui 的语言包，`query.`、`http.` 的在 vue-tsx-pro 的语言包；后端的 `message` 只作兜底。

## 4. 各仓库要改的

| 仓库 | 改什么 |
| --- | --- |
| flow | 0006 第 8 节去掉 HTTP 状态码一列；模型包加 `FlowErrorCode`；两个后端的 HTTP 接口一律 200 加上面的形状；`FlowException` / `FlowError` 带码 |
| query | 两端的异常加 `code()` / `code`；`spec/` 加一份错误码清单两端核对；表格请求读不懂时抛带 `query.request-invalid` 的异常 |
| vue-tsx-pro | `http` 内置按上面的形状拆包（`code` 不是 `ok` 就抛带码的错误，`HttpError` 加 `code`、`params`、`errors`）；网络、超时、取消、非 200、解析失败给 `http.` 码；语言包加 `http.`、`query.` 的文案；`useTableQuery` 等按码显示 |
