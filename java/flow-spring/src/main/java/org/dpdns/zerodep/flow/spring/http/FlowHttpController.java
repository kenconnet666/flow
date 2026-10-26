package org.dpdns.zerodep.flow.spring.http;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.store.InstanceStore.InstanceSearch;
import org.dpdns.zerodep.flow.model.error.ApiResult;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.dpdns.zerodep.query.exec.CodedException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * POST {前缀}/commands/complete   { workItemId, outcome, comment, form }
 * POST {前缀}/commands/withdraw   { instanceId, reason }
 * POST {前缀}/commands/transfer、delegate、add-signers、remove-signers、release、remind、comment、cc、read-cc、signal、assign、withdraw-step
 * POST {前缀}/admin/terminate、suspend、resume、jump、reassign、retry-job、skip-job
 * GET  {前缀}/queries/startable
 * GET  {前缀}/queries/start/{key}
 * GET  {前缀}/queries/todo?page=1&amp;pageSize=20、done、started、cc、designations
 * GET  {前缀}/queries/work-items/{id}
 * GET  {前缀}/queries/instances/{id}
 * GET  {前缀}/queries/instances?status=&amp;processKey=&amp;keyword=、processes、incidents?instanceId=（管理端）
 * </pre>
 * 命令（POST）的请求头带 {@code Idempotency-Key} 时幂等：同一个键再来时返回第一次的响应体，不再做一遍。
 * HTTP 一律 200，成败看响应体的 {@code code}（0008）：{@code ok}，或 {@code flow.}、{@code query.} 的业务错误码；
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
    public ResponseEntity<String> start(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/start", () -> ResponseBody.of(engine.start(RequestBody.parse(body).start())));
    }

    @PostMapping("/commands/claim")
    public ResponseEntity<String> claim(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/claim", () -> ResponseBody.of(engine.claim(RequestBody.parse(body).claim())));
    }

    @PostMapping("/commands/complete")
    public ResponseEntity<String> complete(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/complete", () -> ResponseBody.of(engine.complete(RequestBody.parse(body).complete())));
    }

    @PostMapping("/commands/withdraw")
    public ResponseEntity<String> withdraw(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/withdraw", () -> ResponseBody.of(engine.withdraw(RequestBody.parse(body).withdraw())));
    }

    // ───────── 协作与管理员命令（0006 的 3.1–3.4）；管理员命令谁能做由 PermissionPolicy 定 ─────────

    @PostMapping("/commands/transfer")
    public ResponseEntity<String> transfer(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/transfer", () -> ResponseBody.of(engine.transfer(RequestBody.parse(body).transfer())));
    }

    @PostMapping("/commands/delegate")
    public ResponseEntity<String> delegate(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/delegate", () -> ResponseBody.of(engine.delegate(RequestBody.parse(body).delegate())));
    }

    @PostMapping("/commands/add-signers")
    public ResponseEntity<String> addSigners(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/add-signers", () -> ResponseBody.of(engine.addSigners(RequestBody.parse(body).addSigners())));
    }

    @PostMapping("/commands/remove-signers")
    public ResponseEntity<String> removeSigners(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/remove-signers", () -> ResponseBody.of(engine.removeSigners(RequestBody.parse(body).removeSigners())));
    }

    @PostMapping("/commands/release")
    public ResponseEntity<String> release(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/release", () -> ResponseBody.of(engine.release(RequestBody.parse(body).release())));
    }

    @PostMapping("/commands/remind")
    public ResponseEntity<String> remind(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/remind", () -> ResponseBody.of(engine.remind(RequestBody.parse(body).remind())));
    }

    @PostMapping("/commands/comment")
    public ResponseEntity<String> comment(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/comment", () -> ResponseBody.of(engine.comment(RequestBody.parse(body).comment())));
    }

    @PostMapping("/admin/terminate")
    public ResponseEntity<String> terminate(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("admin/terminate", () -> ResponseBody.of(engine.terminate(RequestBody.parse(body).terminate())));
    }

    @PostMapping("/admin/suspend")
    public ResponseEntity<String> suspend(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("admin/suspend", () -> ResponseBody.of(engine.suspend(RequestBody.parse(body).suspend())));
    }

    @PostMapping("/admin/resume")
    public ResponseEntity<String> resume(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("admin/resume", () -> ResponseBody.of(engine.resume(RequestBody.parse(body).resume())));
    }

    @PostMapping("/admin/jump")
    public ResponseEntity<String> jump(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("admin/jump", () -> ResponseBody.of(engine.jump(RequestBody.parse(body).jump())));
    }

    @PostMapping("/admin/reassign")
    public ResponseEntity<String> reassign(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("admin/reassign", () -> ResponseBody.of(engine.reassign(RequestBody.parse(body).reassign())));
    }

    /** 发布流程定义：请求体就是 0005 的定义 JSON，发布说明放在参数 note 里 */
    @PostMapping("/definitions/publish")
    public ResponseEntity<String> publishDefinition(@org.springframework.web.bind.annotation.RequestBody(required = false) String body,
                                                    @RequestParam(name = "note", required = false) String note) {
        return command("definitions/publish", () -> ResponseBody.of(engine.publishDefinition(body, note)));
    }

    /** 发布表单 */
    @PostMapping("/forms/publish")
    public ResponseEntity<String> publishForm(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("forms/publish", () -> ResponseBody.of(engine.publishForm(RequestBody.parse(body).formPublication())));
    }

    /** 我能发起的流程 */
    @GetMapping("/queries/startable")
    public ResponseEntity<String> startable() {
        return respond(() -> ResponseBody.view(engine.startable()));
    }

    /** 发起页：流程与开始节点上的表单 */
    @GetMapping("/queries/start/{key}")
    public ResponseEntity<String> startView(@PathVariable("key") String key) {
        return respond(() -> ResponseBody.view(engine.startView(key)));
    }

    /** 我的待办；参数名同 vue-tsx-pro 的表格请求（page、pageSize） */
    @GetMapping("/queries/todo")
    public ResponseEntity<String> todo(@RequestParam(name = "page", defaultValue = "1") int page,
                                       @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return respond(() -> ResponseBody.of(engine.todo(page, pageSize)));
    }

    /** 我已办 */
    @GetMapping("/queries/done")
    public ResponseEntity<String> done(@RequestParam(name = "page", defaultValue = "1") int page,
                                       @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return respond(() -> ResponseBody.of(engine.done(page, pageSize)));
    }

    /** 外部事件：{@code { instanceId 或 businessKey, event, outcome, data }}，返回 {@code { results: [每个往下走的实例这一步的结果] }} */
    @PostMapping("/commands/signal")
    public ResponseEntity<String> signal(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/signal", () -> ResponseBody.view(Map.of("results", engine.signal(RequestBody.parse(body).signal()))));
    }

    /** 管理端搜实例：{@code ?status=&processKey=&keyword=&startedBy=&page=&pageSize=} */
    @GetMapping("/queries/instances")
    public ResponseEntity<String> instances(@RequestParam(name = "status", required = false) String status,
                                            @RequestParam(name = "processKey", required = false) String processKey,
                                            @RequestParam(name = "keyword", required = false) String keyword,
                                            @RequestParam(name = "startedBy", required = false) Long startedBy,
                                            @RequestParam(name = "page", defaultValue = "1") int page,
                                            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return respond(() -> ResponseBody.page(engine.instances(new InstanceSearch(status, processKey, keyword, startedBy), page, pageSize)));
    }

    /** 校验定义、不发布：请求体是 0005 的定义 JSON，返回 {@code { problems: [...] }} */
    @PostMapping("/definitions/validate")
    public ResponseEntity<String> validateDefinition(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return respond(() -> ResponseBody.view(Map.of("problems", engine.validateDefinition(body == null ? "" : body))));
    }

    /** 停用流程：{@code { key }} */
    @PostMapping("/definitions/disable")
    public ResponseEntity<String> disableProcess(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("definitions/disable", () -> ResponseBody.view(engine.setProcessActive(RequestBody.parse(body).processKey(), false)));
    }

    /** 启用流程：{@code { key }} */
    @PostMapping("/definitions/enable")
    public ResponseEntity<String> enableProcess(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("definitions/enable", () -> ResponseBody.view(engine.setProcessActive(RequestBody.parse(body).processKey(), true)));
    }

    /** 流程的全部版本 */
    @GetMapping("/queries/processes/{key}/versions")
    public ResponseEntity<String> versions(@PathVariable("key") String key) {
        return respond(() -> ResponseBody.view(engine.versions(key)));
    }

    /** 某个版本的定义 JSON（version 不写取最新的）：返回 {@code { json }}，json 是发布时的原文 */
    @GetMapping("/queries/processes/{key}/definition")
    public ResponseEntity<String> definition(@PathVariable("key") String key, @RequestParam(name = "version", required = false) Integer version) {
        return respond(() -> ResponseBody.view(Map.of("json", engine.definitionJson(key, version))));
    }

    /** 管理端的流程列表 */
    @GetMapping("/queries/processes")
    public ResponseEntity<String> processes() {
        return respond(() -> ResponseBody.view(engine.processes()));
    }

    /** 异常：重试用完的作业；带 instanceId 时只看这个实例的 */
    @GetMapping("/queries/incidents")
    public ResponseEntity<String> incidents(@RequestParam(name = "instanceId", required = false) Long instanceId,
                                            @RequestParam(name = "page", defaultValue = "1") int page,
                                            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return respond(() -> ResponseBody.page(engine.incidents(instanceId, page, pageSize)));
    }

    /** 撤回刚处理的一步：{@code { workItemId, reason }} */
    @PostMapping("/commands/withdraw-step")
    public ResponseEntity<String> withdrawStep(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/withdraw-step", () -> ResponseBody.of(engine.withdrawStep(RequestBody.parse(body).withdrawStep())));
    }

    /** 收件箱负责人指定承办人：{@code { workItemId, userId }} */
    @PostMapping("/commands/assign")
    public ResponseEntity<String> assign(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/assign", () -> ResponseBody.of(engine.assign(RequestBody.parse(body).assign())));
    }

    /** 等我指定承办人的待办 */
    @GetMapping("/queries/designations")
    public ResponseEntity<String> designations(@RequestParam(name = "page", defaultValue = "1") int page,
                                               @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return respond(() -> ResponseBody.of(engine.designations(page, pageSize)));
    }

    /** 抄送我的 */
    @GetMapping("/queries/cc")
    public ResponseEntity<String> ccList(@RequestParam(name = "page", defaultValue = "1") int page,
                                         @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return respond(() -> ResponseBody.page(engine.ccList(page, pageSize)));
    }

    /** 抄送：{@code { instanceId, users, message }} */
    @PostMapping("/commands/cc")
    public ResponseEntity<String> cc(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/cc", () -> ResponseBody.of(engine.cc(RequestBody.parse(body).cc())));
    }

    /** 读了抄送：{@code { instanceId }}，返回 {@code { read: 标了几条 }} */
    @PostMapping("/commands/read-cc")
    public ResponseEntity<String> readCc(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/read-cc", () -> ResponseBody.view(Map.of("read", engine.readCc(RequestBody.parse(body).readCc()))));
    }

    /** 失败的作业重新来过（管理员）：{@code { jobId }} */
    @PostMapping("/admin/retry-job")
    public ResponseEntity<String> retryJob(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("admin/retry-job", () -> ResponseBody.view(Map.of("revived", engine.retryJob(RequestBody.parse(body).retryJob()))));
    }

    /** 跳过失败的作业（管理员）：{@code { jobId, outcome, reason }} */
    @PostMapping("/admin/skip-job")
    public ResponseEntity<String> skipJob(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("admin/skip-job", () -> ResponseBody.of(engine.skipJob(RequestBody.parse(body).skipJob())));
    }

    /** 我发起的 */
    @GetMapping("/queries/started")
    public ResponseEntity<String> started(@RequestParam(name = "page", defaultValue = "1") int page,
                                          @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return respond(() -> ResponseBody.page(engine.started(page, pageSize)));
    }

    /** 待办详情：表单、处理结果、我能不能认领与处理 */
    @GetMapping("/queries/work-items/{id}")
    public ResponseEntity<String> workItem(@PathVariable("id") Long id) {
        return respond(() -> ResponseBody.view(engine.workItem(id)));
    }

    /** 实例详情 */
    @GetMapping("/queries/instances/{id}")
    public ResponseEntity<String> instance(@PathVariable("id") Long id) {
        return respond(() -> ResponseBody.view(engine.instance(id)));
    }

    /**
     * 命令：请求头带 {@code Idempotency-Key} 时幂等地做（同一个键再来时返回第一次的响应体，见 {@code FlowEngine.idempotent}），
     * 否则同 {@link #respond}。
     */
    private ResponseEntity<String> command(String name, Supplier<Object> action) {
        String key = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getHeader("Idempotency-Key");
        if (key == null || key.isBlank()) return respond(action);
        try {
            String body = engine.idempotent(key, name, () -> ResponseBody.write(ApiResult.ok(action.get())));
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
        } catch (RuntimeException e) {
            return respond(() -> { throw e; });
        }
    }

    /** 执行并写成响应体：成功是 {@code ok} 加数据，出错按业务错误码写；HTTP 都是 200 */
    private static ResponseEntity<String> respond(Supplier<Object> action) {
        ApiResult<Object> result;
        try {
            result = ApiResult.ok(action.get());
        } catch (RuntimeException e) {
            result = ApiResult.fail(e, FlowHttpController::codeOf);
            if (!(e instanceof FlowException) && !(e instanceof CodedException)) LOG.log(System.Logger.Level.ERROR, "flow request failed", e);
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseBody.write(result));
    }

    /** query 的异常带自己的业务错误码（{@code query.}），原样用（0008 第 3 节） */
    private static String codeOf(Throwable e) {
        return e instanceof CodedException coded ? coded.code().code() : null;
    }
}
