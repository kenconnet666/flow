package org.dpdns.zerodep.flow.spring.http;

import org.dpdns.zerodep.flow.core.command.AddSignersCommand;
import org.dpdns.zerodep.flow.core.command.CcCommand;
import org.dpdns.zerodep.flow.core.command.ClaimCommand;
import org.dpdns.zerodep.flow.core.command.CommentCommand;
import org.dpdns.zerodep.flow.core.command.CompleteCommand;
import org.dpdns.zerodep.flow.core.command.DelegateCommand;
import org.dpdns.zerodep.flow.core.command.FormPublication;
import org.dpdns.zerodep.flow.core.command.JumpCommand;
import org.dpdns.zerodep.flow.core.command.ReassignCommand;
import org.dpdns.zerodep.flow.core.command.ReadCcCommand;
import org.dpdns.zerodep.flow.core.command.ReleaseCommand;
import org.dpdns.zerodep.flow.core.command.RemindCommand;
import org.dpdns.zerodep.flow.core.command.RemoveSignersCommand;
import org.dpdns.zerodep.flow.core.command.ResumeCommand;
import org.dpdns.zerodep.flow.core.command.RetryJobCommand;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.command.SuspendCommand;
import org.dpdns.zerodep.flow.core.command.TerminateCommand;
import org.dpdns.zerodep.flow.core.command.TransferCommand;
import org.dpdns.zerodep.flow.core.command.WithdrawCommand;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.Variables;
import org.dpdns.zerodep.flow.model.error.ErrorDetail;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.dpdns.zerodep.wire.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 请求体（JSON 文字）读成命令。用 query wire 的 {@link Json#read} 读，不经过 Web 框架的 Jackson：小数不经 double、不丢精度，
 * 也不管应用用的是 Jackson 2 还是 3。前端按 typed JSON 发（vue-tsx-pro 的 {@code toWire}），根上的 {@code $types} 用不上，忽略。
 * 字段名同命令的参数（docs/decisions/0006 第 3 节）；JSON 写错、该是对象的不是对象时报 {@code flow.validation-failed}。
 */
final class RequestBody {
    private final Map<String, Object> fields;

    private RequestBody(Map<String, Object> fields) { this.fields = fields; }

    /** 读一个请求体；空的请求体当作空对象 */
    @SuppressWarnings("unchecked")
    static RequestBody parse(String text) {
        if (text == null || text.isBlank()) return new RequestBody(Map.of());
        Object tree;
        try {
            tree = Json.read(text);
        } catch (IllegalArgumentException e) {
            throw invalid("body", "malformed-json");
        }
        if (!(tree instanceof Map<?, ?>)) throw invalid("body", "expected-object");
        return new RequestBody((Map<String, Object>) tree);
    }

    /** {@code { key, form, variables, businessKey, title, priority }} */
    StartCommand start() {
        return new StartCommand(text("key"), FormValues.of(object("form")), Variables.of(object("variables")), text("businessKey"),
            text("title"), number("priority"));
    }

    /** {@code { workItemId }} */
    ClaimCommand claim() { return new ClaimCommand(id("workItemId")); }

    /** {@code { workItemId, outcome, comment, form }} */
    CompleteCommand complete() {
        return new CompleteCommand(id("workItemId"), text("outcome"), text("comment"), FormValues.of(object("form")));
    }

    /** {@code { instanceId, reason }} */
    WithdrawCommand withdraw() { return new WithdrawCommand(id("instanceId"), text("reason")); }

    /** {@code { workItemId, userId, reason }} */
    TransferCommand transfer() { return new TransferCommand(id("workItemId"), id("userId"), text("reason")); }

    /** {@code { workItemId, userId, reason }} */
    DelegateCommand delegate() { return new DelegateCommand(id("workItemId"), id("userId"), text("reason")); }

    /** {@code { workItemId, users, mode, reason }} */
    AddSignersCommand addSigners() { return new AddSignersCommand(id("workItemId"), ids("users"), text("mode"), text("reason")); }

    /** {@code { workItemId, users }} */
    RemoveSignersCommand removeSigners() { return new RemoveSignersCommand(id("workItemId"), ids("users")); }

    /** {@code { workItemId, reason }} */
    ReleaseCommand release() { return new ReleaseCommand(id("workItemId"), text("reason")); }

    /** {@code { instanceId, workItemId, message }} */
    RemindCommand remind() { return new RemindCommand(id("instanceId"), id("workItemId"), text("message")); }

    /** {@code { instanceId, text }} */
    CommentCommand comment() { return new CommentCommand(id("instanceId"), text("text")); }

    /** {@code { instanceId, reason }} */
    TerminateCommand terminate() { return new TerminateCommand(id("instanceId"), text("reason")); }

    /** {@code { instanceId, reason }} */
    SuspendCommand suspend() { return new SuspendCommand(id("instanceId"), text("reason")); }

    /** {@code { instanceId }} */
    ResumeCommand resume() { return new ResumeCommand(id("instanceId")); }

    /** {@code { instanceId, node, reason }} */
    JumpCommand jump() { return new JumpCommand(id("instanceId"), text("node"), text("reason")); }

    /** {@code { workItemId, userId, reason }} */
    ReassignCommand reassign() { return new ReassignCommand(id("workItemId"), id("userId"), text("reason")); }

    /** {@code { instanceId, users, message }} */
    CcCommand cc() { return new CcCommand(id("instanceId"), ids("users"), text("message")); }

    /** {@code { jobId }} */
    RetryJobCommand retryJob() { return new RetryJobCommand(id("jobId")); }

    /** {@code { instanceId }} */
    ReadCcCommand readCc() { return new ReadCcCommand(id("instanceId")); }

    /** {@code { key, name, category, manifest, clientSource, clientCompiled, compilerVersion, serverLanguage, serverSource, note }}；manifest 是对象 */
    FormPublication formPublication() {
        return new FormPublication(text("key"), text("name"), text("category"), Json.write(object("manifest")), text("clientSource"),
            text("clientCompiled"), text("compilerVersion"), text("serverLanguage"), text("serverSource"), text("note"));
    }

    private String text(String name) {
        Object value = fields.get(name);
        if (value == null || value instanceof String) return (String) value;
        throw invalid(name, "expected-text");
    }

    /** 编号：前端按 typed JSON 发成文字（大整数不丢精度），写成数字也认 */
    private Long id(String name) {
        Object value = fields.get(name);
        if (value == null) return null;
        return idOf(name, value);
    }

    private static Long idOf(String name, Object value) {
        try {
            if (value instanceof Number n) return Long.valueOf(n.toString());
            if (value instanceof String s) return Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            throw invalid(name, "expected-id");
        }
        throw invalid(name, "expected-id");
    }

    /** 一串编号 */
    private List<Long> ids(String name) {
        Object value = fields.get(name);
        if (value == null) return List.of();
        if (!(value instanceof List<?> items)) throw invalid(name, "expected-list");
        List<Long> out = new ArrayList<>();
        for (Object item : items) out.add(idOf(name, item));
        return out;
    }

    private int number(String name) {
        Object value = fields.get(name);
        if (value == null) return 0;
        if (value instanceof Number n) return n.intValue();
        throw invalid(name, "expected-number");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(String name) {
        Object value = fields.get(name);
        if (value == null) return Map.of();
        if (value instanceof Map<?, ?>) return (Map<String, Object>) value;
        throw invalid(name, "expected-object");
    }

    private static FlowException invalid(String field, String code) {
        return new FlowException(FlowErrorCode.VALIDATION_FAILED, field + ": " + code, List.of(ErrorDetail.field(field, code)));
    }
}
