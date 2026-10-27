package org.dpdns.zerodep.flow.model;

import org.dpdns.zerodep.wire.Json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 条件里两个值怎样比：TS 的 {@code values.ts} 与这里是同一套规则，两个后端与前端对同一个条件得到同一个结果（spec/conditions 的样例两边都跑）。
 * <ul>
 *   <li>数字、大整数、能读成数字的文字（{@code "19.90"}、{@code "1e3"}）按精确的小数比较（{@link BigDecimal#compareTo}），不经浮点数；</li>
 *   <li>其余写成文字后按编码顺序比较（{@link String#compareTo}，不按语言环境，ISO 日期按文字比就是先后）；</li>
 *   <li>等于：数字与数字文字视为相等（表单里的 {@code "5"} 与变量里的 {@code 5}），{@code true} 与 {@code "true"} 也相等。</li>
 * </ul>
 * 值是 wire 的 {@code Json.read} 读出来的形状：Map、List、String、Long、BigDecimal、Boolean、null。
 */
public final class Values {
    private static final Pattern NUMERIC =
        Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?");

    private Values() { }

    /** 能读成数字的值（数字、大整数、数字文字）→ 精确的小数；读不成时为 null */
    public static BigDecimal decimalOf(Object value) {
        if (value instanceof BigDecimal d) return d;
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return BigDecimal.valueOf(((Number) value).longValue());
        }
        if (value instanceof BigInteger i) return new BigDecimal(i);
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            return Double.isFinite(d) ? BigDecimal.valueOf(d) : null;
        }
        if (value instanceof String s) {
            String text = s.trim();
            return NUMERIC.matcher(text).matches() ? new BigDecimal(text) : null;
        }
        return null;
    }

    /** 值写成文字：null 是空字符串，对象、数组写成 JSON */
    public static String toText(Object value) {
        if (value == null) return "";
        if (value instanceof Map<?, ?> || value instanceof Collection<?>) return Json.write(value);   // 与 JSON.stringify 一致
        // 去掉末尾的零：与 JS 把数字写成文字一致（1234.50 读进来是 1234.5）
        if (value instanceof BigDecimal d) return d.stripTrailingZeros().toPlainString();
        return String.valueOf(value);
    }

    /** 两个值比大小：负数、0、正数 */
    public static int compare(Object a, Object b) {
        BigDecimal x = decimalOf(a);
        BigDecimal y = decimalOf(b);
        if (x != null && y != null) return Integer.signum(x.compareTo(y));
        return Integer.signum(toText(a).compareTo(toText(b)));
    }

    /** 等于：两个都不是空、不是对象或数组时按 {@link #compare} 比 */
    public static boolean same(Object a, Object b) {
        if (a == b) return true;
        // 对象、数组只在是同一个对象时相等，同 JS 的 ===
        if (a == null || b == null || isComposite(a) || isComposite(b)) return false;
        return compare(a, b) == 0;
    }

    /** 没有值：null、空字符串、空数组 */
    public static boolean isBlank(Object value) {
        return value == null
            || (value instanceof String s && s.isEmpty())
            || (value instanceof Collection<?> c && c.isEmpty());
    }

    private static boolean isComposite(Object value) {
        return value instanceof Map<?, ?> || value instanceof Collection<?>;
    }
}
