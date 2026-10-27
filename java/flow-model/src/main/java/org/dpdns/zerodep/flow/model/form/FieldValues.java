package org.dpdns.zerodep.flow.model.form;

import org.dpdns.zerodep.flow.model.Values;
import org.dpdns.zerodep.flow.model.definition.OrgId;
import org.dpdns.zerodep.wire.Wire;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 一个字段的值合不合字段清单（docs/decisions/0004 的 1.3），与 TS 模型包的 {@code field-values.ts} 同一套规则，
 * 两边跑 spec/values 的样例。提交时（{@link FormAccess#mergeSubmission}）与字段清单校验默认值时都用它。
 * 只查形状与范围；{@code options: { source }} 的字典、接入方注册的字段类型由后端另查。
 */
public final class FieldValues {
    /** 小数的文字：不收科学计数法（{@code 1e3}），能不能读成数与小数位交给 BigDecimal */
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)");

    private FieldValues() { }

    /**
     * 值的一个问题。
     *
     * @param path 相对这个字段的路径：字段自己是空串，明细表的格子是 {@code [0].price}
     * @param code 原因：{@code type}、{@code minLength}、{@code maxLength}、{@code pattern}、{@code min}、{@code max}、{@code integer}、
     *             {@code scale}、{@code option}、{@code peopleType}、{@code maxCount}、{@code maxSize}、{@code minItems}、{@code maxItems}
     */
    public record ValueError(String path, String code) { }

    /** 一个值合不合这个字段；空值（null、空文字、空数组）不查，必填由调用方另查 */
    public static List<ValueError> check(FormManifest.Field field, Object value) {
        List<ValueError> errors = new ArrayList<>();
        if (Values.isBlank(value)) return errors;
        Map<String, Object> a = field.attributes();
        switch (field.type()) {
            case "text" -> {
                if (!(value instanceof String text)) return type();
                Integer min = integer(a.get("minLength"));
                Integer max = integer(a.get("maxLength"));
                if (min != null && text.length() < min) errors.add(new ValueError("", "minLength"));
                if (max != null && text.length() > max) errors.add(new ValueError("", "maxLength"));
                if (a.get("pattern") instanceof String p && !Pattern.compile(p).matcher(text).matches()) {
                    errors.add(new ValueError("", "pattern"));
                }
            }
            case "number" -> {
                if (!(value instanceof Long || value instanceof Integer || value instanceof BigDecimal || value instanceof Double)) return type();
                BigDecimal number = Values.decimalOf(value);
                if (Boolean.TRUE.equals(a.get("integer")) && number.stripTrailingZeros().scale() > 0) errors.add(new ValueError("", "integer"));
                range(value, a, errors);
            }
            case "decimal" -> {
                if (!(value instanceof String text) || !DECIMAL.matcher(text.trim()).matches()) return type();
                Integer scale = integer(a.get("scale"));
                if (scale != null && Math.max(0, new BigDecimal(text.trim()).stripTrailingZeros().scale()) > scale) {
                    errors.add(new ValueError("", "scale"));   // 末尾的零不算
                }
                range(value, a, errors);
            }
            case "boolean" -> {
                if (!(value instanceof Boolean)) return type();
            }
            case "date", "datetime" -> {
                if (!(field.type().equals("date") ? isDate(value) : isDateTime(value))) return type();
                range(value, a, errors);
            }
            case "select" -> {
                List<?> items = field.multiple() ? (value instanceof List<?> l ? l : null) : List.of(value);
                if (items == null || items.stream().anyMatch(i -> !(i instanceof String) && !(i instanceof Number))) return type();
                if (a.get("options") instanceof List<?> options
                    && items.stream().anyMatch(item -> options.stream().noneMatch(o -> o instanceof Map<?, ?> m && sameOption(m.get("value"), item)))) {
                    errors.add(new ValueError("", "option"));
                }
            }
            case "people" -> {
                if (!(value instanceof List<?> people) || people.stream().anyMatch(p -> !(p instanceof Map<?, ?> m)
                    || !OrgId.isValid(m.get("id")) || !(m.get("type") instanceof String))) return type();
                if (a.get("types") instanceof List<?> types && people.stream().anyMatch(p -> !types.contains(((Map<?, ?>) p).get("type")))) {
                    errors.add(new ValueError("", "peopleType"));
                }
                Integer max = a.get("max") instanceof Long l ? Math.toIntExact(l) : null;
                if (max != null && people.size() > max) errors.add(new ValueError("", "maxCount"));
            }
            case "file" -> {
                if (!(value instanceof List<?> files) || files.stream().anyMatch(f -> !(f instanceof Map<?, ?> m) || !(m.get("id") instanceof String)
                    || !(m.get("name") instanceof String) || !(m.get("size") instanceof Number))) return type();
                Integer maxCount = integer(a.get("maxCount"));
                if (maxCount != null && files.size() > maxCount) errors.add(new ValueError("", "maxCount"));
                Object maxSize = a.get("maxSize");
                if (maxSize != null && files.stream().anyMatch(f -> Values.compare(((Map<?, ?>) f).get("size"), maxSize) > 0)) {
                    errors.add(new ValueError("", "maxSize"));
                }
            }
            case "list" -> {
                if (!(value instanceof List<?> rows) || rows.stream().anyMatch(r -> !(r instanceof Map<?, ?>))) return type();
                Integer minItems = integer(a.get("minItems"));
                Integer maxItems = integer(a.get("maxItems"));
                if (minItems != null && rows.size() < minItems) errors.add(new ValueError("", "minItems"));
                if (maxItems != null && rows.size() > maxItems) errors.add(new ValueError("", "maxItems"));
                if (field.columns() != null) {
                    for (int i = 0; i < rows.size(); i++) {
                        Map<?, ?> row = (Map<?, ?>) rows.get(i);
                        for (Map.Entry<String, FormManifest.Field> column : field.columns().fields().entrySet()) {
                            for (ValueError e : check(column.getValue(), row.get(column.getKey()))) {
                                errors.add(new ValueError("[" + i + "]." + column.getKey() + e.path(), e.code()));
                            }
                        }
                    }
                }
            }
            default -> {
                // 接入方注册的字段类型：由注册的实现查
            }
        }
        return errors;
    }

    private static List<ValueError> type() {
        List<ValueError> errors = new ArrayList<>();
        errors.add(new ValueError("", "type"));
        return errors;
    }

    /** 选项的值相等：文字与文字、数字与数字（同 JS 的 ===），数字和文字不相等 */
    private static boolean sameOption(Object option, Object item) {
        if (option instanceof String || item instanceof String) return option != null && option.equals(item);
        return option instanceof Number && item instanceof Number && Values.compare(option, item) == 0;
    }

    private static void range(Object value, Map<String, Object> a, List<ValueError> errors) {
        if (a.get("min") != null && Values.compare(value, a.get("min")) < 0) errors.add(new ValueError("", "min"));
        if (a.get("max") != null && Values.compare(value, a.get("max")) > 0) errors.add(new ValueError("", "max"));
    }

    /** 非负整数的属性；不是整数时为 null（字段清单的校验另外报） */
    private static Integer integer(Object value) {
        return value instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE ? Math.toIntExact(l) : null;
    }

    /** 日期：线上的写法 {@code 2026-10-24}，要是真实存在的日子 */
    private static boolean isDate(Object value) { return value instanceof String s && parses(LocalDate.class, s); }

    /** 日期时间：线上的写法，换算成 UTC、带 {@code Z}（{@code 2026-10-24T08:00:00.123Z}） */
    private static boolean isDateTime(Object value) { return value instanceof String s && s.endsWith("Z") && parses(Instant.class, s); }

    private static boolean parses(Class<?> type, String text) {
        try {
            Wire.parse(type, text);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
