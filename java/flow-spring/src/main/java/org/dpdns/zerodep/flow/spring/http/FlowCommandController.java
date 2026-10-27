package org.dpdns.zerodep.flow.spring.http;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.spi.Actor;
import org.dpdns.zerodep.flow.core.spi.ActorProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 引擎自带的 HTTP 接口：命令（{@code /commands/*}，POST，带 {@code Idempotency-Key} 时幂等）。路径与参数的总表见 {@link FlowHttpSupport}。 */
@RestController
@RequestMapping("${flow.http.path:/flow}")
public class FlowCommandController extends FlowHttpSupport {
    public FlowCommandController(FlowEngine<?> engine, ObjectProvider<ActorProvider<?>> actors) { super(engine, actors); }

    @PostMapping("/commands/start")
    public Map<String, Object> start(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/start", () -> ResponseBody.of(engine.start(actor(), RequestBody.parse(body).start())));
    }

    /** 发起前存草稿：{@code { draftId?, key, form, businessKey }}，返回草稿（实例的样子） */
    @PostMapping("/commands/save-start-draft")
    public Map<String, Object> saveStartDraft(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/save-start-draft", () -> engine.saveStartDraft(actor(), RequestBody.parse(body).saveStartDraft()));
    }

    /** 放弃发起前的草稿：{@code { draftId }}，返回 {@code { discarded }} */
    @PostMapping("/commands/discard-start-draft")
    public Map<String, Object> discardStartDraft(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/discard-start-draft", () -> Map.of("discarded", engine.discardStartDraft(actor(), RequestBody.parse(body).id("draftId"))));
    }

    @PostMapping("/commands/claim")
    public Map<String, Object> claim(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/claim", () -> ResponseBody.of(engine.claim(actor(), RequestBody.parse(body).claim())));
    }

    @PostMapping("/commands/complete")
    public Map<String, Object> complete(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/complete", () -> ResponseBody.of(engine.complete(actor(), RequestBody.parse(body).complete())));
    }

    @PostMapping("/commands/save-draft")
    public Map<String, Object> saveDraft(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/save-draft", () -> ResponseBody.of(engine.saveDraft(actor(), RequestBody.parse(body).saveDraft())));
    }

    @PostMapping("/commands/request-reassign")
    public Map<String, Object> requestReassign(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/request-reassign", () -> ResponseBody.of(engine.requestReassign(actor(), RequestBody.parse(body).requestReassign())));
    }

    /** 批量认领：{@code { workItemIds }}，每个待办一个结果 */
    @PostMapping("/commands/batch-claim")
    public Map<String, Object> batchClaim(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/batch-claim", () -> Map.of("results", engine.batchClaim(actor(), RequestBody.parse(body).workItemIds())));
    }

    /** 批量处理：{@code { workItemIds, outcome, comment }}，outcome 不写时各按第一个"同意"类的结果 */
    @PostMapping("/commands/batch-complete")
    public Map<String, Object> batchComplete(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        RequestBody request = RequestBody.parse(body);
        return command("commands/batch-complete",
            () -> Map.of("results", engine.batchComplete(actor(), request.workItemIds(), request.text("outcome"), request.text("comment"))));
    }

    @PostMapping("/commands/withdraw")
    public Map<String, Object> withdraw(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/withdraw", () -> ResponseBody.of(engine.withdraw(actor(), RequestBody.parse(body).withdraw())));
    }

    // ───────── 协作与管理员命令（0006 的 3.1–3.4）；管理员命令谁能做由 PermissionPolicy 定 ─────────

    @PostMapping("/commands/transfer")
    public Map<String, Object> transfer(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/transfer", () -> ResponseBody.of(engine.transfer(actor(), RequestBody.parse(body).transfer())));
    }

    @PostMapping("/commands/delegate")
    public Map<String, Object> delegate(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/delegate", () -> ResponseBody.of(engine.delegate(actor(), RequestBody.parse(body).delegate())));
    }

    @PostMapping("/commands/add-signers")
    public Map<String, Object> addSigners(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/add-signers", () -> ResponseBody.of(engine.addSigners(actor(), RequestBody.parse(body).addSigners())));
    }

    @PostMapping("/commands/remove-signers")
    public Map<String, Object> removeSigners(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/remove-signers", () -> ResponseBody.of(engine.removeSigners(actor(), RequestBody.parse(body).removeSigners())));
    }

    @PostMapping("/commands/release")
    public Map<String, Object> release(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/release", () -> ResponseBody.of(engine.release(actor(), RequestBody.parse(body).release())));
    }

    @PostMapping("/commands/remind")
    public Map<String, Object> remind(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/remind", () -> ResponseBody.of(engine.remind(actor(), RequestBody.parse(body).remind())));
    }

    @PostMapping("/commands/comment")
    public Map<String, Object> comment(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/comment", () -> ResponseBody.of(engine.comment(actor(), RequestBody.parse(body).comment())));
    }

    /** 外部事件：{@code { instanceId 或 businessKey, event, outcome, data }}，返回 {@code { results: [每个往下走的实例这一步的结果] }} */
    @PostMapping("/commands/signal")
    public Map<String, Object> signal(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/signal", () -> ResponseBody.results(engine.signal(actor(), RequestBody.parse(body).signal())));
    }

    /** 撤回刚处理的一步：{@code { workItemId, reason }} */
    @PostMapping("/commands/withdraw-step")
    public Map<String, Object> withdrawStep(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/withdraw-step", () -> ResponseBody.of(engine.withdrawStep(actor(), RequestBody.parse(body).withdrawStep())));
    }

    /** 收件箱负责人指定承办人：{@code { workItemId, userId }} */
    @PostMapping("/commands/assign")
    public Map<String, Object> assign(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/assign", () -> ResponseBody.of(engine.assign(actor(), RequestBody.parse(body).assign())));
    }

    /** 抄送：{@code { instanceId, users, message }} */
    @PostMapping("/commands/cc")
    public Map<String, Object> cc(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/cc", () -> ResponseBody.of(engine.cc(actor(), RequestBody.parse(body).cc())));
    }

    /** 打开了自己的待办：{@code { workItemId }}，返回 {@code { read: 是否标了 }} */
    @PostMapping("/commands/read-work-item")
    public Map<String, Object> readWorkItem(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/read-work-item", () -> Map.of("read", engine.readWorkItem(actor(), RequestBody.parse(body).readWorkItem())));
    }

    /** 读了抄送：{@code { instanceId }}，返回 {@code { read: 标了几条 }} */
    @PostMapping("/commands/read-cc")
    public Map<String, Object> readCc(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("commands/read-cc", () -> Map.of("read", engine.readCc(actor(), RequestBody.parse(body).readCc())));
    }
}
