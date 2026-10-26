package org.dpdns.zerodep.flow.core.data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程变量（0005 第 2 节的 {@code variables}）：名字 → 值，定义里声明，发起时给初值，连线上的副作用改它们。只给管理员看。
 */
public final class Variables extends JsonFields {
    private static final Variables EMPTY = new Variables(Map.of());

    private Variables(Map<String, ?> fields) { super(fields); }

    /** 从 JSON 形状的 Map 来 */
    public static Variables of(Map<String, ?> fields) { return fields == null || fields.isEmpty() ? EMPTY : new Variables(fields); }

    /** 没有变量 */
    public static Variables empty() { return EMPTY; }

    /** 盖上另一组值，返回新的一组 */
    public Variables with(Map<String, ?> changes) {
        Map<String, Object> next = new LinkedHashMap<>(asMap());
        next.putAll(changes);
        return new Variables(next);
    }
}
