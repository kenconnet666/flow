package org.dpdns.zerodep.flow.spring.http;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.model.error.ApiResult;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.dpdns.zerodep.wire.Json;
import org.dpdns.zerodep.wire.Wire;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 引擎自带的 HTTP 接口（docs/decisions/0006 的 7.3，{@code flow.http.enabled=true} 时才有）。两个后端的路径、参数、返回值相同：
 * <pre>
 * POST {前缀}/commands/start      { key, form, variables, businessKey, title, priority }
 * POST {前缀}/commands/claim      { workItemId }
 * POST {前缀}/commands/save-draft { workItemId, form, comment }
 * POST {前缀}/commands/complete   { workItemId, outcome, comment, form, picks, returnTarget }
 * POST {前缀}/commands/withdraw   { instanceId, reason }
 * POST {前缀}/commands/transfer、delegate、add-signers、remove-signers、release、remind、comment、cc、read-cc、signal、assign、withdraw-step
 * POST {前缀}/admin/terminate、suspend、resume、jump、reassign、retry-job、skip-job
 * GET  {前缀}/queries/startable
 * GET  {前缀}/queries/start/{key}
 * GET  {前缀}/queries/todo?page=1&amp;pageSize=20、done、started、cc、designations
 * GET  {前缀}/queries/work-items/{id}
 * GET  {前缀}/queries/instances/{id}
 * GET  {前缀}/queries/instances?status=&amp;processKey=&amp;keyword=、processes、incidents?instanceId=（管理端）
 * GET  {前缀}/queries/forms、forms/{key}/versions、forms/{key}/document?version=（管理端）
 * POST {前缀}/definitions/validate、definitions/publish、definitions/disable、definitions/enable、forms/publish、forms/disable、forms/enable
 * </pre>
 * 命令（POST）的请求头带 {@code Idempotency-Key} 时幂等：同一个键再来时返回第一次的响应体，不再做一遍。
 * HTTP 一律 200，成败看响应体的 {@code code}（0008）：{@code ok}，或 {@code flow.} 的业务错误码；返回普通的对象，
 * 写成 typed JSON 由接入方集成的 wire 做（见 {@link ResponseBody}）；
 * 认不出的异常是 {@code flow.internal-error}，并记日志。操作人取引擎的 {@code ActorProvider}；登录与接口的保护由应用的安全框架负责。
 *
 * <p>接口按路径分在四个控制器里：{@link FlowCommandController}、{@link FlowQueryController}、{@link FlowAdminController}、
 * {@link FlowDesignController}；这里是它们共用的：引擎、回响应体（{@link #respond}）、幂等的命令（{@link #command}）。
 */
abstract class FlowHttpSupport {
    private static final System.Logger LOG = System.getLogger(FlowHttpSupport.class.getName());

    /** 引擎；操作人由它的 ActorProvider 取 */
    protected final FlowEngine<Actor> engine;

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected FlowHttpSupport(FlowEngine engine) { this.engine = engine; }

    /**
     * 命令：请求头带 {@code Idempotency-Key} 时幂等地做（同一个键再来时返回第一次的响应体，见 {@code FlowEngine.idempotent}），
     * 否则同 {@link #respond}。
     */
    protected Map<String, Object> command(String name, Supplier<Object> action) {
        String key = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getHeader("Idempotency-Key");
        if (key == null || key.isBlank()) return respond(action);
        try {
            // 第一次的响应体按 typed JSON 存成文字，再来时读回原来的值（同样交给接入方的 wire 编码）
            String stored = engine.idempotent(key, name, () -> Json.write(Wire.encode(ResponseBody.of(ApiResult.ok(action.get())))));
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) Wire.decode(Json.read(stored));
            return body;
        } catch (RuntimeException e) {
            return respond(() -> { throw e; });
        }
    }

    /** 执行并写成响应体：成功是 {@code ok} 加数据，出错按业务错误码写；HTTP 都是 200 */
    protected static Map<String, Object> respond(Supplier<Object> action) {
        ApiResult<Object> result;
        try {
            result = ApiResult.ok(action.get());
        } catch (RuntimeException e) {
            result = ApiResult.fail(e);
            if (!(e instanceof FlowException)) LOG.log(System.Logger.Level.ERROR, "flow request failed", e);
        }
        return ResponseBody.of(result);
    }
}
