package org.dpdns.zerodep.flow.spring.http;

import org.dpdns.zerodep.flow.core.command.ClaimCommand;
import org.dpdns.zerodep.flow.core.command.CompleteCommand;
import org.dpdns.zerodep.flow.core.command.FormPublication;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.command.WithdrawCommand;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.Variables;
import org.dpdns.zerodep.flow.model.error.ErrorDetail;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.dpdns.zerodep.wire.Json;

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
    ClaimCommand claim() { return new ClaimCommand(text("workItemId")); }

    /** {@code { workItemId, outcome, comment, form }} */
    CompleteCommand complete() {
        return new CompleteCommand(text("workItemId"), text("outcome"), text("comment"), FormValues.of(object("form")));
    }

    /** {@code { instanceId, reason }} */
    WithdrawCommand withdraw() { return new WithdrawCommand(text("instanceId"), text("reason")); }

    /** {@code { key, name, category, manifest, clientSource, clientCompiled, compilerVersion, serverLanguage, serverSource, note }}；manifest 是对象 */
    FormPublication formPublication() {
        return new FormPublication(text("key"), text("name"), text("category"), Json.write(object("manifest")), text("clientSource"),
            text("clientCompiled"), text("compilerVersion"), text("serverLanguage"), text("serverSource"), text("note"));
    }

    private String text(String name) {
        Object value = fields.get(name);
        if (value == null) return null;
        if (value instanceof String s) return s;
        // 编号可能被写成数字：照样认
        if (value instanceof Number n) return n.toString();
        throw invalid(name, "expected-text");
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
