package org.dpdns.zerodep.flow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 条件求值：spec/conditions 的样例与 TS 一侧共用 */
@DisplayName("条件求值")
class ConditionsTest {
    @TestFactory
    @DisplayName("spec/conditions/rules.json")
    Stream<DynamicTest> sharedSamples() {
        Map<?, ?> spec = Spec.read("conditions/rules.json");
        Map<?, ?> shared = (Map<?, ?>) spec.get("values");
        List<DynamicTest> tests = new ArrayList<>();
        for (Object item : (List<?>) spec.get("cases")) {
            Map<?, ?> sample = (Map<?, ?>) item;
            Map<?, ?> values = sample.containsKey("values") ? (Map<?, ?>) sample.get("values") : shared;
            Condition condition = Conditions.parse(sample.get("condition"));
            tests.add(DynamicTest.dynamicTest((String) sample.get("name"),
                () -> assertEquals(sample.get("expect"), Conditions.evaluate(condition, values::get))));
        }
        for (Object item : (List<?>) spec.get("complete")) {
            Map<?, ?> sample = (Map<?, ?>) item;
            Condition condition = Conditions.parse(sample.get("condition"));
            tests.add(DynamicTest.dynamicTest("写完整：" + sample.get("name"),
                () -> assertEquals(sample.get("expect"), Conditions.isComplete(condition))));
        }
        return tests.stream();
    }

    @Test
    @DisplayName("大整数与长小数按精确的小数比")
    void exactDecimals() {
        assertEquals(0, Values.compare(new BigInteger("12345678901234567890"), "12345678901234567890.0"));
        assertTrue(Values.compare("0.1", new BigDecimal("0.10000000000000001")) < 0);
    }

    @Test
    @DisplayName("不是数字时按编码顺序比")
    void codePointOrder() {
        assertTrue(Values.compare("B", "a") < 0);
        assertTrue(Values.compare("2026-09-30", "2026-10-01") < 0);
    }

    @Test
    @DisplayName("形状不对的条件报错时带路径")
    void parseErrors() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> Conditions.parse(Map.of("all", List.of(Map.of("field", "a", "op", "like")))));
        assertTrue(e.getMessage().startsWith("$.all[0].op"), e.getMessage());
    }

    @Test
    @DisplayName("用到的字段不重复")
    void fields() {
        Condition c = Conditions.parse(Map.of("any", List.of(
            Map.of("field", "form.a", "op", "exists"),
            Map.of("not", Map.of("field", "form.a", "op", "eq", "value", 1)),
            Map.of("field", "round", "op", "gt", "value", 1L))));
        assertEquals(List.of("form.a", "round"), new ArrayList<>(Conditions.fields(c)));
    }
}
