package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.model.Values;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 把定义、表单里写的用户、角色、部门编号换成 {@code Long}（接入方组织表的自增主键）。
 * <pre>
 *   写法                                    换成
 *   1001 / 1001.0 / "1001"                  1001
 *   { "id": 1001, "type": "user", ... }     1001（people 类型的值，一个人）
 *   [1001, { "id": 1002, ... }]             [1001, 1002]（people 类型的值是列表）
 *   null / "" / []                          没有
 * </pre>
 * 写的不是整数时报 {@code flow.definition-invalid}：多半是定义里把角色写成了名字。
 */
final class OrgIds {
    private OrgIds() { }

    /** 一个编号，或一组编号；空的值不算 */
    static List<Long> all(Object value) {
        if (value instanceof Collection<?> items) return items.stream().map(OrgIds::one).filter(Objects::nonNull).toList();
        Long id = one(value);
        return id == null ? List.of() : List.of(id);
    }

    /** 一个编号；空的值是 null */
    static Long one(Object value) {
        if (Values.isBlank(value)) return null;
        if (value instanceof Map<?, ?> person) return one(person.get("id"));
        try {
            if (value instanceof Long l) return l;
            if (value instanceof Integer i) return i.longValue();
            if (value instanceof BigDecimal d) return d.longValueExact();
            if (value instanceof Double d) return new BigDecimal(d).longValueExact();
            return Long.valueOf(Values.toText(value).trim());
        } catch (ArithmeticException | NumberFormatException e) {
            throw FlowErrors.badOrgId(value);
        }
    }
}
