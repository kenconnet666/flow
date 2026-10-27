package org.dpdns.zerodep.flow.model.form;

import lombok.Data;
import org.dpdns.zerodep.flow.model.Condition;
import org.dpdns.zerodep.flow.model.Conditions;
import org.dpdns.zerodep.wire.Wire;
import org.dpdns.zerodep.wire.WireType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表单的字段清单（docs/decisions/0004 的 1.3）：表单源码里 {@code defineFields({...})} 的字面量，保存时提取成 JSON 存下。
 * 与 TS 模型包的 {@code manifest.ts} 同一份形状；字段按清单里的先后。
 */
public class FormManifest {
    private final Map<String, Field> fields;

    private FormManifest(Map<String, Field> fields) { this.fields = Collections.unmodifiableMap(fields); }

    /**
     * 一个字段。类型专有的属性按 JSON 原样放在 {@code attributes} 里（{@code minLength}、{@code scale}、{@code options}……），
     * 引擎用到的几样有自己的字段。
     */
    @Data
    public static class Field {
        /** 显示名 */
        private final String label;
        /** 类型：内置的十种，或接入方注册的 */
        private final String type;
        /** 必填 */
        private final boolean required;
        /** select 能不能多选 */
        private final boolean multiple;
        /** 显示条件；null 时总是显示 */
        private final Condition visibleWhen;
        /** 必填条件；null 时只看 required */
        private final Condition requiredWhen;
        /** list 的列；不是 list 时为 null */
        private final FormManifest columns;
        /** 字段的全部属性：JSON 原样，只有 decimal、date、datetime 的 {@code default}、{@code min}、{@code max} 读成与值同样的强类型（BigDecimal、LocalDate、Instant；见 {@link #VALUE_KINDS}） */
        private final Map<String, Object> attributes;
    }

    /**
     * 值是强类型的字段类型在线上（json-wire）是哪一种：提交的值由 wire 还原成对应的 Java 类型，清单里人写的
     * {@code default}、{@code min}、{@code max} 是文字，解析清单时按它读成同样的类型。
     */
    public static final Map<String, WireType> VALUE_KINDS = Map.of("decimal", WireType.DECIMAL, "date", WireType.DATE, "datetime", WireType.TIMESTAMPTZ);

    /** 带值的属性 */
    private static final List<String> VALUE_ATTRIBUTES = List.of("default", "min", "max");

    /** 从 JSON 树读字段清单；形状不对时报 IllegalArgumentException，消息里带路径 */
    public static FormManifest parse(Object tree) {
        return parse(tree, "$");
    }

    private static FormManifest parse(Object tree, String path) {
        if (!(tree instanceof Map<?, ?> map)) throw new IllegalArgumentException(path + ": a form manifest must be an object");
        Map<String, Field> fields = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String name = String.valueOf(entry.getKey());
            String at = path + "." + name;
            if (!(entry.getValue() instanceof Map<?, ?> attributes)) throw new IllegalArgumentException(at + ": a field must be an object");
            if (!(attributes.get("type") instanceof String type)) throw new IllegalArgumentException(at + ".type: must be a string");
            Object label = attributes.get("label");
            Object visible = attributes.get("visibleWhen");
            Object requiredWhen = attributes.get("requiredWhen");
            Object columns = attributes.get("fields");
            Map<String, Object> raw = typedValues(type, attributes);
            fields.put(name, new Field(
                label == null ? name : label.toString(), type,
                Boolean.TRUE.equals(attributes.get("required")), Boolean.TRUE.equals(attributes.get("multiple")),
                visible == null ? null : Conditions.parse(visible), requiredWhen == null ? null : Conditions.parse(requiredWhen),
                "list".equals(type) && columns != null ? parse(columns, at + ".fields") : null, raw));
        }
        return new FormManifest(fields);
    }

    /** 强类型字段的 default、min、max 从文字读成值的类型（读不出时留着文字，清单的校验报 default-invalid、attribute-invalid） */
    private static Map<String, Object> typedValues(String type, Map<?, ?> attributes) {
        Map<String, Object> out = new LinkedHashMap<>();
        attributes.forEach((key, value) -> out.put(String.valueOf(key), value));
        for (String attribute : VALUE_ATTRIBUTES) out.computeIfPresent(attribute, (key, value) -> typed(type, value));
        return Collections.unmodifiableMap(out);
    }

    /**
     * 定义里写的值（调用流程的 {@code input}、外部事件的 {@code output} 写进表单的）按字段类型读成强类型：人写的 JSON 没有 {@code $types}，
     * decimal、date、datetime 的文字读成 BigDecimal、LocalDate、Instant；明细表逐行逐列读。不认识的字段、读不出的文字原样（提交时报 type）。
     */
    public Map<String, Object> typed(Map<String, Object> values) {
        Map<String, Object> out = new LinkedHashMap<>();
        values.forEach((name, value) -> {
            Field field = fields.get(name);
            out.put(name, field == null ? value : typed(field, value));
        });
        return out;
    }

    private static Object typed(Field field, Object value) {
        if (field.getColumns() != null && value instanceof List<?> rows) {
            List<Object> out = new ArrayList<>();
            for (Object row : rows) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cells = row instanceof Map<?, ?> map ? field.getColumns().typed((Map<String, Object>) map) : null;
                out.add(cells == null ? row : cells);
            }
            return out;
        }
        return typed(field.getType(), value);
    }

    /** 一个值：强类型字段的文字读成值的类型，读不出时原样 */
    private static Object typed(String type, Object value) {
        WireType kind = VALUE_KINDS.get(type);
        if (kind == null || !(value instanceof String text)) return value;
        try {
            return Wire.read(kind, text);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    /** 全部顶层字段，按清单里的先后 */
    public Map<String, Field> fields() { return fields; }

    /** 按路径找字段：{@code amount}，或明细表的列 {@code lines.price}；没有时为 null */
    public Field field(String path) {
        int dot = path.indexOf('.');
        if (dot < 0) return fields.get(path);
        Field list = fields.get(path.substring(0, dot));
        return list != null && list.getColumns() != null ? list.getColumns().field(path.substring(dot + 1)) : null;
    }

    /** 全部字段的路径，明细表的列写成 {@code 列表.列} */
    public List<String> paths() {
        List<String> paths = new ArrayList<>();
        for (Map.Entry<String, Field> entry : fields.entrySet()) {
            paths.add(entry.getKey());
            if (entry.getValue().getColumns() != null) {
                for (String column : entry.getValue().getColumns().fields().keySet()) paths.add(entry.getKey() + "." + column);
            }
        }
        return paths;
    }
}
