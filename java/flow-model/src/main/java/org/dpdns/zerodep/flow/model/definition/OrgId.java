package org.dpdns.zerodep.flow.model.definition;

import java.math.BigDecimal;

/**
 * 接入方组织表里的编号（用户、角色、部门，一般是自增主键）。定义与表单的值里写成正整数；
 * 超出 JavaScript 安全整数的编号写成只有数字的文字。同 TS 的 {@code OrgId} 与 {@code isOrgId}。
 */
public final class OrgId {
    private OrgId() { }

    /** 是不是一个组织的编号：正整数，或只有数字的文字 */
    public static boolean isValid(Object value) {
        if (value instanceof Long || value instanceof Integer) return ((Number) value).longValue() > 0;
        if (value instanceof BigDecimal d) return d.signum() > 0 && d.stripTrailingZeros().scale() <= 0;
        return value instanceof String text && text.matches("[1-9][0-9]{0,18}");
    }
}
