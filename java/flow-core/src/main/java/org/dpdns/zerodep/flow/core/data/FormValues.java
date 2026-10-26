package org.dpdns.zerodep.flow.core.data;

import java.util.Map;

/**
 * 实例上表单的值（0006 第 2 节的 {@code FormValues}，0004 的 1.3）：字段名 → 值，字段由表单的字段清单声明。
 * <pre>{@code
 * FormValues form = detail.form();
 * BigDecimal amount = form.decimal("amount");
 * List<String> reviewers = form.texts("reviewers");
 * }</pre>
 */
public final class FormValues extends JsonFields {
    private static final FormValues EMPTY = new FormValues(Map.of());

    private FormValues(Map<String, ?> fields) { super(fields); }

    /** 从 JSON 形状的 Map 来 */
    public static FormValues of(Map<String, ?> fields) { return fields == null || fields.isEmpty() ? EMPTY : new FormValues(fields); }

    /** 没有字段 */
    public static FormValues empty() { return EMPTY; }
}
