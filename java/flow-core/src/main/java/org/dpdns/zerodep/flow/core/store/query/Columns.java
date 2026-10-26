package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.RunState;
import org.dpdns.zerodep.flow.core.data.Variables;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance.CurrentNode;
import org.dpdns.zerodep.query.type.Converter;
import org.dpdns.zerodep.wire.Json;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表的列与引擎里的类型之间的转换器（query 的 {@link Converter}，写在 {@link FlowTables} 的 {@code @Column(converter = …)} 上）。
 * 有了它们，读写的行直接就是引擎的记录（{@code selectAs(InstanceRecord.class)}），存储里不用再一个个字段地换。
 * <ul>
 *   <li>{@link Utc}：时间——引擎里是 {@link Instant}，库里是 UTC 的 DATETIME(3)（0001 的"UTC 时间"）；</li>
 *   <li>其余：JSON 列——引擎里是有名字的类型，库里是 JSON 文字（形状见 docs/decisions/0007）。</li>
 * </ul>
 */
public final class Columns {
    private Columns() { }


    /** 时间：时刻 ↔ UTC 的本地时间 */
    public static final class Utc implements Converter<Instant, LocalDateTime> {
        @Override public LocalDateTime toDatabase(Instant value) { return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
        @Override public Instant fromDatabase(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
    }

    /** 表单的值：{ 字段: 值 } */
    public static final class FormValuesJson implements Converter<FormValues, String> {
        @Override public String toDatabase(FormValues value) { return value == null ? null : Json.write(value.asMap()); }
        @Override public FormValues fromDatabase(String value) { return FormValues.of(object(value)); }
    }

    /** 流程变量：{ 名字: 值 } */
    public static final class VariablesJson implements Converter<Variables, String> {
        @Override public String toDatabase(Variables value) { return value == null ? null : Json.write(value.asMap()); }
        @Override public Variables fromDatabase(String value) { return Variables.of(object(value)); }
    }

    /** 运行状态：{ "rounds": { 节点: 轮次 } } */
    public static final class RunStateJson implements Converter<RunState, String> {
        @Override public String toDatabase(RunState value) { return value == null ? null : Json.write(Map.of("rounds", value.rounds())); }

        @Override
        public RunState fromDatabase(String value) {
            Map<String, Integer> rounds = new LinkedHashMap<>();
            if (object(value).get("rounds") instanceof Map<?, ?> stored) {
                stored.forEach((node, round) -> rounds.put((String) node, ((Number) round).intValue()));
            }
            return new RunState(rounds);
        }
    }

    /** 正在处理的节点：[{ "node": 标识, "name": 名称 }] */
    public static final class CurrentNodesJson implements Converter<List<CurrentNode>, String> {
        @Override
        public String toDatabase(List<CurrentNode> value) {
            if (value == null) return null;
            List<Map<String, String>> items = new ArrayList<>();
            for (CurrentNode c : value) items.add(Map.of("node", c.node(), "name", c.name() == null ? c.node() : c.name()));
            return Json.write(items);
        }

        @Override
        public List<CurrentNode> fromDatabase(String value) {
            if (value == null) return List.of();
            List<CurrentNode> out = new ArrayList<>();
            for (Object item : (List<?>) Json.read(value)) {
                Map<?, ?> node = (Map<?, ?>) item;
                out.add(new CurrentNode((String) node.get("node"), (String) node.get("name")));
            }
            return List.copyOf(out);
        }
    }

    /** 摘要列、流转记录的附加数据：随场合不同的 JSON 对象；空的对象存成 NULL */
    public static final class ObjectJson implements Converter<Map<String, Object>, String> {
        @Override public String toDatabase(Map<String, Object> value) { return value == null || value.isEmpty() ? null : Json.write(value); }
        @Override public Map<String, Object> fromDatabase(String value) { return object(value); }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(String json) {
        return json == null ? Map.of() : (Map<String, Object>) Json.read(json);
    }
}
