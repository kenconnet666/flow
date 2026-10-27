package org.dpdns.zerodep.flow.core.db;

import com.easy.query.core.basic.extension.conversion.ValueConverter;
import com.easy.query.core.metadata.ColumnMetadata;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.RunState;
import org.dpdns.zerodep.flow.core.data.Variables;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity.CurrentNode;
import org.dpdns.zerodep.wire.Json;
import org.dpdns.zerodep.wire.Wire;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 列的转换器（写在实体与 VO 的 {@code @Column(conversion = …)} 上，由 {@link FlowEasyQuery#install} 注册）：
 * 引擎里是有名字的类型，库里是 typed JSON 的文字（形状见 docs/decisions/0007）——同线上的写法，BigDecimal、LocalDate、Long
 * 这些值写成文字、根上的 {@code $types} 记下位置，读回来还是原来的类型（{@code Wire.encode} / {@code Wire.decode}）。
 */
public final class JsonColumns {
    private JsonColumns() { }

    /** 表单的值：{ 字段: 值 } */
    public static final class FormValuesJson implements ValueConverter<FormValues, String> {
        @Override public String serialize(FormValues value, ColumnMetadata column) { return value == null ? null : write(value.asMap()); }
        @Override public FormValues deserialize(String value, ColumnMetadata column) { return FormValues.of(object(value)); }
    }

    /** 流程变量：{ 名字: 值 } */
    public static final class VariablesJson implements ValueConverter<Variables, String> {
        @Override public String serialize(Variables value, ColumnMetadata column) { return value == null ? null : write(value.asMap()); }
        @Override public Variables deserialize(String value, ColumnMetadata column) { return Variables.of(object(value)); }
    }

    /** 运行状态：{ "rounds": { 节点: 轮次 }, "waiting": …, "returns": …, "queues": …, "waits": { 节点: 时刻 } } */
    public static final class RunStateJson implements ValueConverter<RunState, String> {
        @Override
        public String serialize(RunState value, ColumnMetadata column) {
            return value == null ? null
                : Json.write(Map.of("rounds", value.rounds(), "waiting", value.waiting(), "returns", value.returns(), "queues", value.queues(),
                    "waits", texts(value.waits())));
        }

        @Override
        public RunState deserialize(String value, ColumnMetadata column) {
            Map<String, Object> json = object(value);
            return new RunState(counts(json.get("rounds")), counts(json.get("waiting")), names(json.get("returns")), queues(json.get("queues")),
                moments(json.get("waits")));
        }

        /** 节点 → 时刻写成带 Z 的文字 */
        private static Map<String, String> texts(Map<String, Instant> waits) {
            Map<String, String> out = new LinkedHashMap<>();
            waits.forEach((node, at) -> out.put(node, at.toString()));
            return out;
        }

        /** 节点 → 时刻（JSON 里是带 Z 的文字） */
        private static Map<String, Instant> moments(Object stored) {
            Map<String, Instant> out = new LinkedHashMap<>();
            if (stored instanceof Map<?, ?> map) map.forEach((node, at) -> out.put((String) node, Instant.parse(String.valueOf(at))));
            return out;
        }

        /** 节点 → 一串用户编号 */
        private static Map<String, List<Long>> queues(Object stored) {
            Map<String, List<Long>> out = new LinkedHashMap<>();
            if (stored instanceof Map<?, ?> map) {
                map.forEach((node, ids) -> out.put((String) node, ((List<?>) ids).stream().map(id -> ((Number) id).longValue()).toList()));
            }
            return out;
        }

        /** 节点 → 节点 */
        private static Map<String, String> names(Object stored) {
            Map<String, String> out = new LinkedHashMap<>();
            if (stored instanceof Map<?, ?> map) map.forEach((node, to) -> out.put((String) node, (String) to));
            return out;
        }

        /** 节点 → 次数 */
        private static Map<String, Integer> counts(Object stored) {
            Map<String, Integer> out = new LinkedHashMap<>();
            if (stored instanceof Map<?, ?> map) map.forEach((node, n) -> out.put((String) node, ((Number) n).intValue()));
            return out;
        }
    }

    /** 正在处理的节点：[{ "node": 标识, "name": 名称 }] */
    public static final class CurrentNodesJson implements ValueConverter<List<CurrentNode>, String> {
        @Override
        public String serialize(List<CurrentNode> value, ColumnMetadata column) {
            if (value == null) return null;
            List<Map<String, String>> items = new ArrayList<>();
            for (CurrentNode c : value) items.add(Map.of("node", c.node(), "name", c.name() == null ? c.node() : c.name()));
            return Json.write(items);
        }

        @Override
        public List<CurrentNode> deserialize(String value, ColumnMetadata column) {
            if (value == null) return List.of();
            List<CurrentNode> out = new ArrayList<>();
            for (Object item : (List<?>) Json.read(value)) {
                Map<?, ?> node = (Map<?, ?>) item;
                out.add(new CurrentNode((String) node.get("node"), (String) node.get("name")));
            }
            return List.copyOf(out);
        }
    }

    /** 摘要列、流转记录的附加数据、作业的数据：随场合不同的 JSON 对象；空的对象存成 NULL */
    public static final class ObjectJson implements ValueConverter<Map<String, Object>, String> {
        @Override public String serialize(Map<String, Object> value, ColumnMetadata column) { return value == null || value.isEmpty() ? null : write(value); }
        @Override public Map<String, Object> deserialize(String value, ColumnMetadata column) { return object(value); }
    }

    /** 写成 typed JSON 的文字 */
    private static String write(Map<String, Object> value) { return Json.write(Wire.encode(value)); }

    /** 读 typed JSON 的文字：值还原成原来的类型，没标类型的数是 JavaScript 的数 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(String json) {
        return json == null ? Map.of() : (Map<String, Object>) Wire.decode(Json.read(json));
    }
}
