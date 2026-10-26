package org.dpdns.zerodep.flow.core.data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户给的 JSON（表单的值、流程变量、字段清单）里的数字按 JavaScript 的数存：放得进 int 的整数是 Integer，其余是 Double。
 * <p>为什么：读 JSON 时整数是 Long、小数是 BigDecimal，而下发给前端的 typed JSON 把 Long、BigDecimal 标成 bigint、decimal
 * （那是给编号、金额列用的），前端拿到的数量就成了 {@code 3n}。这些 JSON 本来就是前端按 JavaScript 的数写的，按同样的数存，
 * 来回一趟不变。要精确的小数用表单的 {@code decimal} 类型（值是文字，0004 的 1.3），超出安全整数的编号写成文字。
 */
public final class JsonNumbers {
    private JsonNumbers() { }

    /** 把 JSON 树里的数字换成 Integer 或 Double；Map、List 换成只读的副本，其余原样 */
    public static Object normalize(Object json) {
        if (json instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((key, value) -> out.put(String.valueOf(key), normalize(value)));
            return Collections.unmodifiableMap(out);
        }
        if (json instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) out.add(normalize(item));
            return Collections.unmodifiableList(out);
        }
        if (json instanceof Long || json instanceof BigInteger || json instanceof BigDecimal) return number((Number) json);
        return json;
    }

    /** 一个数：整数且放得进 int 时是 Integer，其余是 Double */
    private static Object number(Number n) {
        BigDecimal exact = n instanceof BigDecimal d ? d : new BigDecimal(n.toString());
        if (exact.signum() == 0 || exact.stripTrailingZeros().scale() <= 0) {
            try {
                return exact.intValueExact();
            } catch (ArithmeticException tooBig) {
                // 放不进 int 的整数按 double 存，同 JavaScript
            }
        }
        return exact.doubleValue();
    }
}
