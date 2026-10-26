package org.dpdns.zerodep.flow.spring.http;

import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.view.InstanceDetail;
import org.dpdns.zerodep.flow.core.store.DefinitionStore;
import org.dpdns.zerodep.flow.core.store.FormStore;
import org.dpdns.zerodep.flow.model.error.ApiResult;
import org.dpdns.zerodep.flow.model.error.ErrorDetail;
import org.dpdns.zerodep.flow.model.runtime.TodoItem;
import org.dpdns.zerodep.query.exec.Page;
import org.dpdns.zerodep.wire.Json;
import org.dpdns.zerodep.wire.Wire;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 响应体：0008 的形状 {@code { code, message, params, errors, data }} 写成 JSON 文字。
 * data 是 typed JSON（{@link Wire#encode}、{@link Page#toWire}）：编号、小数、时间写成文字，根上带 {@code $types}，
 * 前端的请求层（vue-tsx-pro 的 {@code createHttpClient}）拆包时据此还原。
 */
final class ResponseBody {
    private ResponseBody() { }

    /** 命令的结果：{@code { instance, workItems, closed, events }} */
    static Object of(CommandResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("instance", result.instance());
        data.put("workItems", result.created());
        data.put("closed", result.closed());
        data.put("events", result.events());
        return Wire.encode(data);
    }

    /** 一页待办：同 query 的表格接口 {@code { rows, total, page, size, totalPages }}，每行是 {@code { workItem, title, summary }} */
    static Object of(Page<TodoItem> page) { return page.toWire(); }

    /** 一页别的行（实例……）：同 query 的表格接口 */
    static Object page(Page<?> page) { return page.toWire(); }

    /** 发布的流程版本：{@code { id, key, version }}（定义本身就是请求体，不再发回） */
    static Object of(DefinitionStore.StoredDefinition definition) {
        return Map.of("id", definition.id(), "key", definition.processKey(), "version", definition.version());
    }

    /** 发布的表单版本：{@code { id, key, version }} */
    static Object of(FormStore.StoredForm form) {
        return Map.of("id", form.id(), "key", form.formKey(), "version", form.version());
    }

    /** 查询的结果本身（发起列表、发起页、待办详情、实例详情）：记录按属性名写成对象 */
    static Object view(Object value) { return Wire.encode(value); }

    /** 整个响应体写成 JSON 文字；没有的部分省掉 */
    static String write(ApiResult<?> result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", result.code());
        if (result.message() != null) body.put("message", result.message());
        if (result.params() != null && !result.params().isEmpty()) body.put("params", result.params());
        if (result.errors() != null && !result.errors().isEmpty()) body.put("errors", errors(result.errors()));
        if (result.data() != null) body.put("data", result.data());
        return Json.write(body);
    }

    /** 错误明细的每一项只写有的部分 */
    private static List<Map<String, Object>> errors(List<ErrorDetail> details) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ErrorDetail d : details) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", d.code());
            if (d.field() != null) item.put("field", d.field());
            if (d.node() != null) item.put("node", d.node());
            if (d.edge() != null) item.put("edge", d.edge());
            if (d.lane() != null) item.put("lane", d.lane());
            if (!d.params().isEmpty()) item.put("params", d.params());
            out.add(item);
        }
        return out;
    }
}
