package org.dpdns.zerodep.flow.model;

import org.dpdns.zerodep.flow.model.definition.Definitions;
import org.dpdns.zerodep.flow.model.definition.ProcessEdge;
import org.dpdns.zerodep.flow.model.definition.ProcessGraph;
import org.dpdns.zerodep.flow.model.definition.Routing;
import org.dpdns.zerodep.flow.model.form.FormAccess;
import org.dpdns.zerodep.flow.model.form.FormManifest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 选边、副作用、字段权限与提交合并：spec/routing、spec/access 的样例与 TS 一侧共用 */
@DisplayName("选边与字段权限")
class RoutingAccessSpecTest {
    @TestFactory
    @DisplayName("spec/routing/select-edges.json")
    Stream<DynamicTest> routing() {
        Map<?, ?> spec = Spec.read("routing/select-edges.json");
        List<DynamicTest> tests = new ArrayList<>();
        for (Object item : (List<?>) spec.get("cases")) {
            Map<?, ?> sample = (Map<?, ?>) item;
            Map<?, ?> values = sample.containsKey("values") ? (Map<?, ?>) sample.get("values") : Map.of();
            tests.add(DynamicTest.dynamicTest((String) sample.get("name"), () -> {
                Map<?, ?> file = Spec.read((String) sample.get("definitionFile"));
                ProcessGraph graph = ProcessGraph.of(Definitions.parse(file.get("definition")));
                List<ProcessEdge> edges = Routing.selectEdges(graph, (String) sample.get("node"), (String) sample.get("outcome"), values::get);
                assertEquals(sample.get("expect"), edges.stream().map(ProcessEdge::id).toList());
            }));
        }
        for (Object item : (List<?>) spec.get("effects")) {
            Map<?, ?> sample = (Map<?, ?>) item;
            Map<?, ?> values = sample.containsKey("values") ? (Map<?, ?>) sample.get("values") : Map.of();
            tests.add(DynamicTest.dynamicTest("副作用：" + sample.get("name"), () -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> variables = (Map<String, Object>) sample.get("variables");
                Map<String, Object> actual = Routing.applyEffects(Definitions.parseEffects(sample.get("effects")), variables, values::get);
                assertTrue(same(actual, sample.get("expect")), String.valueOf(actual));
            }));
        }
        return tests.stream();
    }

    @TestFactory
    @DisplayName("spec/access/field-access.json")
    Stream<DynamicTest> access() {
        Map<?, ?> spec = Spec.read("access/field-access.json");
        FormManifest manifest = FormManifest.parse(spec.get("manifest"));
        List<DynamicTest> tests = new ArrayList<>();
        for (Object item : (List<?>) spec.get("cases")) {
            Map<?, ?> sample = (Map<?, ?>) item;
            tests.add(DynamicTest.dynamicTest((String) sample.get("name"), () -> {
                Map<String, FormAccess.FieldState> states = states(manifest, sample);
                Map<String, Object> actual = new LinkedHashMap<>();
                states.forEach((path, s) -> actual.put(path, s.visible()
                    ? List.of(s.visible(), s.editable(), s.required())
                    : List.of(s.visible(), s.editable(), s.required(), s.hiddenBy().name().toLowerCase())));
                assertEquals(sample.get("states"), actual);
            }));
        }
        for (Object item : (List<?>) spec.get("merges")) {
            Map<?, ?> sample = (Map<?, ?>) item;
            tests.add(DynamicTest.dynamicTest("合并：" + sample.get("name"), () -> {
                @SuppressWarnings("unchecked")
                FormAccess.Submission result = FormAccess.mergeSubmission(manifest, states(manifest, sample),
                    (Map<String, Object>) sample.get("stored"), (Map<String, Object>) sample.get("submitted"));
                assertEquals(sample.get("expectValues"), result.values());
                List<Map<String, String>> errors = result.errors().stream().map(e -> Map.of("field", e.field(), "code", e.code())).toList();
                assertEquals(sample.get("expectErrors"), errors);
            }));
        }
        return tests.stream();
    }

    private static Map<String, FormAccess.FieldState> states(FormManifest manifest, Map<?, ?> sample) {
        Map<?, ?> values = (Map<?, ?>) sample.get("values");
        return FormAccess.fieldStates(manifest, Definitions.parseFormAccess(sample.get("access")),
            Boolean.TRUE.equals(sample.get("isStart")), values::get);
    }

    /** 两份变量相等：值按 Values.same 比，数组、对象逐项比；同 TS 一侧的 sameVariables */
    private static boolean same(Object actual, Object expected) {
        if (expected instanceof List<?> e) {
            if (!(actual instanceof List<?> a) || a.size() != e.size()) return false;
            for (int i = 0; i < e.size(); i++) if (!same(a.get(i), e.get(i))) return false;
            return true;
        }
        if (expected instanceof Map<?, ?> e) {
            Map<?, ?> a = actual instanceof Map<?, ?> m ? m : Map.of();
            Set<Object> keys = new HashSet<>(a.keySet());
            keys.addAll(e.keySet());
            for (Object k : keys) if (!same(a.get(k), e.get(k))) return false;
            return true;
        }
        return expected == null ? actual == null : Values.same(actual, expected);
    }
}
