package org.dpdns.zerodep.flow.it.scenario;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 场景里的期望怎样比（README 的"期望"）：只比写出来的字段；列表一样长、逐项比；{@code null} 表示为空或没有；
 * 期望是一串文字、实际是一串事件（带 type 的对象）时按事件种类比；数字按数值比。
 */
class Expectation {
    private Expectation() { }

    /** 不符时报 AssertionError，消息里带出不符的路径 */
    static void check(String path, Object expected, Object actual) {
        if (expected == null) {
            if (actual != null) fail(path, "expected nothing but was " + actual);
        } else if (expected instanceof Map<?, ?> fields) {
            if (!(actual instanceof Map<?, ?> actualFields)) {
                fail(path, "expected an object but was " + actual);
                return;
            }
            for (Map.Entry<?, ?> e : fields.entrySet()) check(path + "." + e.getKey(), e.getValue(), actualFields.get(e.getKey()));
        } else if (expected instanceof List<?> items) {
            if (!(actual instanceof List<?> actualItems) || actualItems.size() != items.size()) {
                fail(path, "expected " + items.size() + " item(s) " + items + " but was " + actual);
                return;
            }
            for (int i = 0; i < items.size(); i++) check(path + "[" + i + "]", items.get(i), event(items.get(i), actualItems.get(i)));
        } else if (expected instanceof Number n && actual instanceof Number m) {
            if (new BigDecimal(n.toString()).compareTo(new BigDecimal(m.toString())) != 0) fail(path, "expected " + n + " but was " + m);
        } else if (!expected.equals(actual)) {
            fail(path, "expected " + expected + " but was " + actual);
        }
    }

    /** 期望写的是事件种类（文字）、实际是一条流转记录时，拿它的种类来比 */
    private static Object event(Object expected, Object actual) {
        return expected instanceof String && actual instanceof Map<?, ?> event && event.containsKey("type") ? event.get("type") : actual;
    }

    private static void fail(String path, String message) { throw new AssertionError(path + ": " + message); }
}
