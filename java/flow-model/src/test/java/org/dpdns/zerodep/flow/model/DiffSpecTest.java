package org.dpdns.zerodep.flow.model;

import org.dpdns.zerodep.flow.model.definition.ProcessDiff;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 两个版本的差别：spec/diff 的样例与 TS 一侧共用 */
@DisplayName("两个版本的差别")
class DiffSpecTest {
    @TestFactory
    @DisplayName("spec/diff/versions.json")
    Stream<DynamicTest> versions() {
        return ((List<?>) Spec.read("diff/versions.json").get("cases")).stream().map(item -> {
            Map<?, ?> sample = (Map<?, ?>) item;
            return DynamicTest.dynamicTest((String) sample.get("name"), () -> {
                ProcessDiff diff = ProcessDiff.of(sample.get("before"), sample.get("after"), Boolean.TRUE.equals(sample.get("layout")));
                assertEquals(sample.get("expect"), json(diff));
            });
        });
    }

    /** 差别写成与样例同形的 JSON 树 */
    private static Map<String, Object> json(ProcessDiff diff) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("process", diff.process());
        out.put("lanes", items(diff.lanes()));
        out.put("nodes", items(diff.nodes()));
        out.put("edges", items(diff.edges()));
        if (diff.layout() != null) out.put("layout", Map.of("nodes", diff.layout().nodes(), "edges", diff.layout().edges()));
        return out;
    }

    private static Map<String, Object> items(ProcessDiff.ItemDiff items) {
        return Map.of("added", items.added(), "removed", items.removed(),
            "changed", items.changed().stream().map(c -> Map.of("id", c.id(), "keys", c.keys())).toList());
    }
}
