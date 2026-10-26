package org.dpdns.zerodep.flow.core.data;

import org.dpdns.zerodep.flow.model.Values;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 形状由接入方定义的一组字段（表单的值、流程变量）：名字 → JSON 形状的值（文字、数字、布尔、列表、对象）。不可变。
 * 按名字取值用 {@link #get}，要文字、小数时用 {@link #text}、{@link #decimal}；带点的名字按路径往对象里取（{@code buyer.name}）。
 */
public abstract class JsonFields {
    private final Map<String, Object> fields;

    JsonFields(Map<String, ?> fields) {
        this.fields = fields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    /** 一个字段的值；没有时为 null。名字带点时按路径取 */
    public Object get(String path) {
        Object value = fields;
        for (String step : path.split("\\.")) {
            if (!(value instanceof Map<?, ?> map)) return null;
            value = map.get(step);
        }
        return value;
    }

    /** 字段的值写成文字（同模型包的 {@code Values.toText}）；没有时为 null */
    public String text(String path) {
        Object value = get(path);
        return value == null ? null : Values.toText(value);
    }

    /** 字段的值当作精确的小数；没有或不是数字时为 null */
    public BigDecimal decimal(String path) { return Values.decimalOf(get(path)); }

    /** 字段的值当作一组文字（{@code people} 这类多选的值）；单个值是一项，空值是空列表 */
    public List<String> texts(String path) {
        Object value = get(path);
        if (value instanceof List<?> items) return items.stream().filter(v -> !Values.isBlank(v)).map(Values::toText).toList();
        return Values.isBlank(value) ? List.of() : List.of(Values.toText(value));
    }

    /** 全部字段，只读；交给模型包的规则与存储用 */
    public Map<String, Object> asMap() { return fields; }

    /** 没有字段 */
    public boolean isEmpty() { return fields.isEmpty(); }

    @Override
    public boolean equals(Object other) {
        return other != null && other.getClass() == getClass() && fields.equals(((JsonFields) other).fields);
    }

    @Override
    public int hashCode() { return Objects.hash(getClass(), fields); }

    @Override
    public String toString() { return getClass().getSimpleName() + fields; }
}
