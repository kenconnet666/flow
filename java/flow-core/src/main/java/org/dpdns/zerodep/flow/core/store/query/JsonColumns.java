package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.RunState;
import org.dpdns.zerodep.flow.core.data.Variables;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance.CurrentNode;
import org.dpdns.zerodep.wire.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表里 JSON 列的读写：每种 JSON 一对方法，写成什么形状在这里一眼看清（形状见 docs/decisions/0007）。
 * 写用 query wire 的 {@link Json#write}，读用 {@link Json#read}；列为 NULL 时读成空的值。
 */
final class JsonColumns {
    private JsonColumns() { }

    // ───────── 表单的值、流程变量、摘要列：名字 → 值 ─────────

    static String write(FormValues values) { return Json.write(values.asMap()); }

    static FormValues formValues(String json) { return FormValues.of(object(json)); }

    static String write(Variables variables) { return Json.write(variables.asMap()); }

    static Variables variables(String json) { return Variables.of(object(json)); }

    static String writeSummary(Map<String, Object> summary) { return Json.write(summary); }

    static Map<String, Object> summary(String json) { return object(json); }

    // ───────── 运行状态：{ "rounds": { "节点": 轮次 } } ─────────

    static String write(RunState state) { return Json.write(Map.of("rounds", state.rounds())); }

    static RunState runState(String json) {
        Map<String, Integer> rounds = new LinkedHashMap<>();
        if (object(json).get("rounds") instanceof Map<?, ?> stored) {
            for (Map.Entry<?, ?> e : stored.entrySet()) rounds.put((String) e.getKey(), ((Number) e.getValue()).intValue());
        }
        return new RunState(rounds);
    }

    // ───────── 正在处理的节点：[{ "node": 标识, "name": 名称 }] ─────────

    static String writeCurrent(List<CurrentNode> current) {
        List<Map<String, String>> items = new ArrayList<>();
        for (CurrentNode c : current) items.add(Map.of("node", c.node(), "name", c.name() == null ? c.node() : c.name()));
        return Json.write(items);
    }

    static List<CurrentNode> current(String json) {
        if (json == null) return List.of();
        List<CurrentNode> out = new ArrayList<>();
        for (Object item : (List<?>) Json.read(json)) {
            Map<?, ?> node = (Map<?, ?>) item;
            out.add(new CurrentNode((String) node.get("node"), (String) node.get("name")));
        }
        return List.copyOf(out);
    }

    // ───────── 流转记录的附加数据：随事件种类不同 ─────────

    static String writeEventData(Map<String, Object> data) { return data.isEmpty() ? null : Json.write(data); }

    static Map<String, Object> eventData(String json) { return object(json); }

    /** JSON 对象读成 Map；NULL 读成空的 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(String json) {
        return json == null ? Map.of() : (Map<String, Object>) Json.read(json);
    }
}
