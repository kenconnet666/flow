package org.dpdns.zerodep.flow.spring.http;

import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.entity.FormVersionEntity;
import org.dpdns.zerodep.flow.core.entity.ProcessVersionEntity;
import org.dpdns.zerodep.flow.model.error.ApiResult;
import org.dpdns.zerodep.flow.model.error.ErrorDetail;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 响应体：0008 的形状 {@code { code, message, params, errors, data }}。这里只给出普通的对象（Map、record、VO），
 * 写成 typed JSON（编号、小数、时间写成文字，根上带 {@code $types}）由接入方集成的 wire 做（示例见 examples 的 {@code config/wire}），
 * 前端的请求层（vue-tsx-pro 的 {@code createHttpClient}）拆包时据此还原。
 */
class ResponseBody {
    private ResponseBody() { }

    /** 命令的结果：{@code { instance, workItems, closed, events }} */
    static Object of(CommandResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("instance", result.getInstance());
        data.put("workItems", result.getCreated());
        data.put("closed", result.getClosed());
        data.put("events", result.getEvents());
        return data;
    }

    /** 外部事件的结果：{@code { results: [每个往下走的实例的命令结果] }}，每一项同 {@link #of(CommandResult)} */
    static Object results(List<CommandResult> results) {
        return Map.of("results", results.stream().map(ResponseBody::of).toList());
    }

    /** 发布的流程版本：{@code { id, key, version }}（定义本身就是请求体，不再发回） */
    static Object of(ProcessVersionEntity definition) {
        return Map.of("id", definition.getId(), "key", definition.getProcessKey(), "version", definition.getVersion());
    }

    /** 发布的表单版本：{@code { id, key, version }} */
    static Object of(FormVersionEntity form) {
        return Map.of("id", form.getId(), "key", form.getFormKey(), "version", form.getVersion());
    }

    /** 整个响应体；没有的部分省掉 */
    static Map<String, Object> of(ApiResult<?> result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", result.getCode());
        if (result.getMessage() != null) body.put("message", result.getMessage());
        if (result.getParams() != null && !result.getParams().isEmpty()) body.put("params", result.getParams());
        if (result.getErrors() != null && !result.getErrors().isEmpty()) body.put("errors", errors(result.getErrors()));
        if (result.getData() != null) body.put("data", result.getData());
        return body;
    }

    /** 错误明细的每一项只写有的部分 */
    private static List<Map<String, Object>> errors(List<ErrorDetail> details) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ErrorDetail d : details) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", d.getCode());
            if (d.getField() != null) item.put("field", d.getField());
            if (d.getNode() != null) item.put("node", d.getNode());
            if (d.getEdge() != null) item.put("edge", d.getEdge());
            if (d.getLane() != null) item.put("lane", d.getLane());
            if (!d.getParams().isEmpty()) item.put("params", d.getParams());
            out.add(item);
        }
        return out;
    }
}
