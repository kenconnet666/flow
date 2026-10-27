package org.dpdns.zerodep.flow.spring.http;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.spi.Actor;
import org.dpdns.zerodep.flow.core.spi.ActorProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 引擎自带的 HTTP 接口：流程定义、表单的发布与停用、设计中的草稿（{@code /definitions/*}、{@code /forms/*}、{@code /drafts/*}）。总表见 {@link FlowHttpSupport}。 */
@RestController
@RequestMapping("${flow.http.path:/flow}")
public class FlowDesignController extends FlowHttpSupport {
    public FlowDesignController(FlowEngine<?> engine, ObjectProvider<ActorProvider<?>> actors) { super(engine, actors); }

    /** 发布流程定义：请求体就是 0005 的定义 JSON，发布说明放在参数 note 里，编辑时看到的最新版本放在 expectedLatest 里（不给不核对） */
    @PostMapping("/definitions/publish")
    public Map<String, Object> publishDefinition(@org.springframework.web.bind.annotation.RequestBody(required = false) String body,
                                                    @RequestParam(name = "note", required = false) String note,
                                                    @RequestParam(name = "expectedLatest", required = false) Integer expectedLatest) {
        return command("definitions/publish", () -> ResponseBody.of(engine.publishDefinition(actor(), body, note, expectedLatest)));
    }

    /** 停用表单：{@code { key }} */
    @PostMapping("/forms/disable")
    public Map<String, Object> disableForm(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("forms/disable", () -> engine.setFormActive(actor(), RequestBody.parse(body).key(), false));
    }

    /** 启用表单：{@code { key }} */
    @PostMapping("/forms/enable")
    public Map<String, Object> enableForm(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("forms/enable", () -> engine.setFormActive(actor(), RequestBody.parse(body).key(), true));
    }

    /** 保存设计中的草稿：{@code { kind, key, name, content, baseVersion, rev }}；别人先存过时报 flow.concurrent-modification */
    @PostMapping("/drafts/save")
    public Map<String, Object> saveDesignDraft(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("drafts/save", () -> engine.saveDesignDraft(actor(), RequestBody.parse(body).saveDesignDraft()));
    }

    /** 放弃草稿：{@code { kind, key }} */
    @PostMapping("/drafts/discard")
    public Map<String, Object> discardDesignDraft(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        RequestBody request = RequestBody.parse(body);
        return command("drafts/discard", () -> {
            engine.discardDesignDraft(actor(), request.text("kind"), request.key());
            return Map.of();
        });
    }

    /** 试编译表单的后端代码：{@code { source }}，返回问题（行列从 1 开始） */
    @PostMapping("/forms/compile")
    public Map<String, Object> compileFormCode(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return respond(() -> engine.compileFormCode(actor(), RequestBody.parse(body).text("source")));
    }

    /** 调表单后端代码给前端的接口：{@code { formVersionId, instanceId, action, args }} */
    @PostMapping("/forms/actions")
    public Map<String, Object> formAction(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        RequestBody request = RequestBody.parse(body);
        return respond(() -> engine.formAction(actor(), request.id("formVersionId"), request.id("instanceId"), request.text("action"), request.object("args")));
    }

    /** 发布表单 */
    @PostMapping("/forms/publish")
    public Map<String, Object> publishForm(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("forms/publish", () -> ResponseBody.of(engine.publishForm(actor(), RequestBody.parse(body).formPublication())));
    }

    /** 校验定义、不发布：请求体是 0005 的定义 JSON，返回 {@code { problems: [...] }} */
    @PostMapping("/definitions/validate")
    public Map<String, Object> validateDefinition(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return respond(() -> Map.of("problems", engine.validateDefinition(actor(), body == null ? "" : body)));
    }

    /** 停用流程：{@code { key }} */
    @PostMapping("/definitions/disable")
    public Map<String, Object> disableProcess(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("definitions/disable", () -> engine.setProcessActive(actor(), RequestBody.parse(body).key(), false));
    }

    /** 启用流程：{@code { key }} */
    @PostMapping("/definitions/enable")
    public Map<String, Object> enableProcess(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("definitions/enable", () -> engine.setProcessActive(actor(), RequestBody.parse(body).key(), true));
    }
}
