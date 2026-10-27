package org.dpdns.zerodep.flow.spring.http;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.engine.Queries.InstanceSearch;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.model.error.ApiResult;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.dpdns.zerodep.wire.Json;
import org.dpdns.zerodep.wire.Wire;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 引擎自带的 HTTP 接口（docs/decisions/0006 的 7.3，{@code flow.http.enabled=true} 时才有）。两个后端的路径、参数、返回值相同：
 * <pre>
 * POST {前缀}/commands/start      { key, form, variables, businessKey, title, priority }
 * POST {前缀}/commands/claim      { workItemId }
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
 */
@RestController
@RequestMapping("${flow.http.path:/flow}")
public class FlowHttpController {
    private static final System.Logger LOG = System.getLogger(FlowHttpController.class.getName());

    private final FlowEngine<Actor> engine;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public FlowHttpController(FlowEngine engine) { this.engine = engine; }

    @PostMapping("/commands/start")
    public Map<String, Object> start(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/start", () -> ResponseBody.of(engine.start(RequestBody.parse(body).start())));
    }

    @PostMapping("/commands/claim")
    public Map<String, Object> claim(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/claim", () -> ResponseBody.of(engine.claim(RequestBody.parse(body).claim())));
    }

    @PostMapping("/commands/complete")
    public Map<String, Object> complete(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/complete", () -> ResponseBody.of(engine.complete(RequestBody.parse(body).complete())));
    }

    @PostMapping("/commands/withdraw")
    public Map<String, Object> withdraw(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/withdraw", () -> ResponseBody.of(engine.withdraw(RequestBody.parse(body).withdraw())));
    }

    // ───────── 协作与管理员命令（0006 的 3.1–3.4）；管理员命令谁能做由 PermissionPolicy 定 ─────────

    @PostMapping("/commands/transfer")
    public Map<String, Object> transfer(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/transfer", () -> ResponseBody.of(engine.transfer(RequestBody.parse(body).transfer())));
    }

    @PostMapping("/commands/delegate")
    public Map<String, Object> delegate(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/delegate", () -> ResponseBody.of(engine.delegate(RequestBody.parse(body).delegate())));
    }

    @PostMapping("/commands/add-signers")
    public Map<String, Object> addSigners(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/add-signers", () -> ResponseBody.of(engine.addSigners(RequestBody.parse(body).addSigners())));
    }

    @PostMapping("/commands/remove-signers")
    public Map<String, Object> removeSigners(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/remove-signers", () -> ResponseBody.of(engine.removeSigners(RequestBody.parse(body).removeSigners())));
    }

    @PostMapping("/commands/release")
    public Map<String, Object> release(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/release", () -> ResponseBody.of(engine.release(RequestBody.parse(body).release())));
    }

    @PostMapping("/commands/remind")
    public Map<String, Object> remind(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/remind", () -> ResponseBody.of(engine.remind(RequestBody.parse(body).remind())));
    }

    @PostMapping("/commands/comment")
    public Map<String, Object> comment(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/comment", () -> ResponseBody.of(engine.comment(RequestBody.parse(body).comment())));
    }

    @PostMapping("/admin/terminate")
    public Map<String, Object> terminate(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("admin/terminate", () -> ResponseBody.of(engine.terminate(RequestBody.parse(body).terminate())));
    }

    @PostMapping("/admin/suspend")
    public Map<String, Object> suspend(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("admin/suspend", () -> ResponseBody.of(engine.suspend(RequestBody.parse(body).suspend())));
    }

    @PostMapping("/admin/resume")
    public Map<String, Object> resume(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("admin/resume", () -> ResponseBody.of(engine.resume(RequestBody.parse(body).resume())));
    }

    @PostMapping("/admin/jump")
    public Map<String, Object> jump(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("admin/jump", () -> ResponseBody.of(engine.jump(RequestBody.parse(body).jump())));
    }

    @PostMapping("/admin/reassign")
    public Map<String, Object> reassign(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("admin/reassign", () -> ResponseBody.of(engine.reassign(RequestBody.parse(body).reassign())));
    }

    /** 发布流程定义：请求体就是 0005 的定义 JSON，发布说明放在参数 note 里 */
    @PostMapping("/definitions/publish")
    public Map<String, Object> publishDefinition(@org.springframework.web.bind.annotation.RequestBody(required = false) String body,
                                                    @RequestParam(name = "note", required = false) String note) {
        return command("definitions/publish", () -> ResponseBody.of(engine.publishDefinition(body, note)));
    }

    /** 停用表单：{@code { key }} */
    @PostMapping("/forms/disable")
    public Map<String, Object> disableForm(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("forms/disable", () -> engine.setFormActive(RequestBody.parse(body).key(), false));
    }

    /** 启用表单：{@code { key }} */
    @PostMapping("/forms/enable")
    public Map<String, Object> enableForm(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("forms/enable", () -> engine.setFormActive(RequestBody.parse(body).key(), true));
    }

    /** 管理端的表单列表 */
    @GetMapping("/queries/forms")
    public Map<String, Object> forms() {
        return respond(() -> engine.forms());
    }

    /** 表单的全部版本 */
    @GetMapping("/queries/forms/{key}/versions")
    public Map<String, Object> formVersions(@PathVariable("key") String key) {
        return respond(() -> engine.formVersions(key));
    }

    /** 表单某个版本的源码与字段清单（version 不写取最新的） */
    @GetMapping("/queries/forms/{key}/document")
    public Map<String, Object> formDocument(@PathVariable("key") String key, @RequestParam(name = "version", required = false) Integer version) {
        return respond(() -> engine.formDocument(key, version));
    }

    /** 发布表单 */
    @PostMapping("/forms/publish")
    public Map<String, Object> publishForm(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("forms/publish", () -> ResponseBody.of(engine.publishForm(RequestBody.parse(body).formPublication())));
    }

    /** 我能发起的流程 */
    @GetMapping("/queries/startable")
    public Map<String, Object> startable() {
        return respond(() -> engine.startable());
    }

    /** 发起页：流程与开始节点上的表单 */
    @GetMapping("/queries/start/{key}")
    public Map<String, Object> startView(@PathVariable("key") String key) {
        return respond(() -> engine.startView(key));
    }

    /** 我的待办；参数名同 vue-tsx-pro 的表格请求（page、pageSize） */
    @GetMapping("/queries/todo")
    public Map<String, Object> todo(@RequestParam(name = "page", defaultValue = "1") int page,
                                       @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return respond(() -> engine.todo(page, pageSize));
    }

    /** 我已办 */
    @GetMapping("/queries/done")
    public Map<String, Object> done(@RequestParam(name = "page", defaultValue = "1") int page,
                                       @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return respond(() -> engine.done(page, pageSize));
    }

    /** 外部事件：{@code { instanceId 或 businessKey, event, outcome, data }}，返回 {@code { results: [每个往下走的实例这一步的结果] }} */
    @PostMapping("/commands/signal")
    public Map<String, Object> signal(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/signal", () -> ResponseBody.results(engine.signal(RequestBody.parse(body).signal())));
    }

    /** 管理端搜实例：{@code ?status=&processKey=&keyword=&startedBy=&page=&pageSize=} */
    @GetMapping("/queries/instances")
    public Map<String, Object> instances(@RequestParam(name = "status", required = false) String status,
                                        @RequestParam(name = "processKey", required = false) String processKey,
                                        @RequestParam(name = "keyword", required = false) String keyword,
                                        @RequestParam(name = "startedBy", required = false) Long startedBy,
                                        @RequestParam(name = "page", defaultValue = "1") int page,
                                        @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return respond(() -> engine.instances(new InstanceSearch(status, processKey, keyword, startedBy), page, pageSize));
    }

    /** 校验定义、不发布：请求体是 0005 的定义 JSON，返回 {@code { problems: [...] }} */
    @PostMapping("/definitions/validate")
    public Map<String, Object> validateDefinition(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return respond(() -> Map.of("problems", engine.validateDefinition(body == null ? "" : body)));
    }

    /** 停用流程：{@code { key }} */
    @PostMapping("/definitions/disable")
    public Map<String, Object> disableProcess(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("definitions/disable", () -> engine.setProcessActive(RequestBody.parse(body).key(), false));
    }

    /** 启用流程：{@code { key }} */
    @PostMapping("/definitions/enable")
    public Map<String, Object> enableProcess(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("definitions/enable", () -> engine.setProcessActive(RequestBody.parse(body).key(), true));
    }

    /** 流程的全部版本 */
    @GetMapping("/queries/processes/{key}/versions")
    public Map<String, Object> versions(@PathVariable("key") String key) {
        return respond(() -> engine.versions(key));
    }

    /** 某个版本的定义 JSON（version 不写取最新的）：返回 {@code { json }}，json 是发布时的原文 */
    @GetMapping("/queries/processes/{key}/definition")
    public Map<String, Object> definition(@PathVariable("key") String key, @RequestParam(name = "version", required = false) Integer version) {
        return respond(() -> Map.of("json", engine.definitionJson(key, version)));
    }

    /** 管理端的流程列表 */
    @GetMapping("/queries/processes")
    public Map<String, Object> processes() {
        return respond(() -> engine.processes());
    }

    /** 异常：重试用完的作业；带 instanceId 时只看这个实例的 */
    @GetMapping("/queries/incidents")
    public Map<String, Object> incidents(@RequestParam(name = "instanceId", required = false) Long instanceId,
                                        @RequestParam(name = "page", defaultValue = "1") int page,
                                        @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return respond(() -> engine.incidents(instanceId, page, pageSize));
    }

    /** 撤回刚处理的一步：{@code { workItemId, reason }} */
    @PostMapping("/commands/withdraw-step")
    public Map<String, Object> withdrawStep(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/withdraw-step", () -> ResponseBody.of(engine.withdrawStep(RequestBody.parse(body).withdrawStep())));
    }

    /** 收件箱负责人指定承办人：{@code { workItemId, userId }} */
    @PostMapping("/commands/assign")
    public Map<String, Object> assign(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/assign", () -> ResponseBody.of(engine.assign(RequestBody.parse(body).assign())));
    }

    /** 等我指定承办人的待办 */
    @GetMapping("/queries/designations")
    public Map<String, Object> designations(@RequestParam(name = "page", defaultValue = "1") int page,
                                               @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return respond(() -> engine.designations(page, pageSize));
    }

    /** 抄送我的 */
    @GetMapping("/queries/cc")
    public Map<String, Object> ccList(@RequestParam(name = "page", defaultValue = "1") int page,
                                         @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return respond(() -> engine.ccList(page, pageSize));
    }

    /** 抄送：{@code { instanceId, users, message }} */
    @PostMapping("/commands/cc")
    public Map<String, Object> cc(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/cc", () -> ResponseBody.of(engine.cc(RequestBody.parse(body).cc())));
    }

    /** 读了抄送：{@code { instanceId }}，返回 {@code { read: 标了几条 }} */
    @PostMapping("/commands/read-cc")
    public Map<String, Object> readCc(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/read-cc", () -> Map.of("read", engine.readCc(RequestBody.parse(body).readCc())));
    }

    /** 失败的作业重新来过（管理员）：{@code { jobId }} */
    @PostMapping("/admin/retry-job")
    public Map<String, Object> retryJob(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("admin/retry-job", () -> Map.of("revived", engine.retryJob(RequestBody.parse(body).retryJob())));
    }

    /** 跳过失败的作业（管理员）：{@code { jobId, outcome, reason }} */
    @PostMapping("/admin/skip-job")
    public Map<String, Object> skipJob(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("admin/skip-job", () -> ResponseBody.of(engine.skipJob(RequestBody.parse(body).skipJob())));
    }

    /** 我发起的 */
    @GetMapping("/queries/started")
    public Map<String, Object> started(@RequestParam(name = "page", defaultValue = "1") int page,
                                          @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return respond(() -> engine.started(page, pageSize));
    }

    /** 待办详情：表单、处理结果、我能不能认领与处理 */
    @GetMapping("/queries/work-items/{id}")
    public Map<String, Object> workItem(@PathVariable("id") Long id) {
        return respond(() -> engine.workItem(id));
    }

    /** 实例详情 */
    @GetMapping("/queries/instances/{id}")
    public Map<String, Object> instance(@PathVariable("id") Long id) {
        return respond(() -> engine.instance(id));
    }

    /**
     * 命令：请求头带 {@code Idempotency-Key} 时幂等地做（同一个键再来时返回第一次的响应体，见 {@code FlowEngine.idempotent}），
     * 否则同 {@link #respond}。
     */
    private Map<String, Object> command(String name, Supplier<Object> action) {
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
    private static Map<String, Object> respond(Supplier<Object> action) {
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
