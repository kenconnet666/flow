package org.dpdns.zerodep.flow.model;

import org.dpdns.zerodep.flow.model.definition.Definitions;
import org.dpdns.zerodep.flow.model.definition.ProcessDefinition;
import org.dpdns.zerodep.flow.model.form.FormManifest;
import org.dpdns.zerodep.flow.model.validate.Problem;
import org.dpdns.zerodep.flow.model.validate.ProcessValidator;
import org.dpdns.zerodep.flow.model.validate.ValidationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 定义的校验：spec/definitions 的样例与 TS 一侧共用，问题列表（不看先后）要完全相同 */
@DisplayName("定义的校验")
class ValidatorSpecTest {
    @TestFactory
    @DisplayName("spec/definitions")
    Stream<DynamicTest> sharedSamples() {
        return Spec.list("definitions").stream().map(file -> {
            Map<?, ?> sample = Spec.read("definitions/" + file);
            return DynamicTest.dynamicTest(file + "：" + sample.get("name"), () -> {
                ProcessDefinition definition = Definitions.parse(sample.get("definition"));
                List<Problem> actual = ProcessValidator.validate(definition, contextOf(sample));
                assertEquals(normalizeExpected((List<?>) sample.get("expect")), normalize(actual));
            });
        });
    }

    /** 样例里的 context → 校验上下文；规则同 TS 一侧的 contextOf */
    static ValidationContext contextOf(Map<?, ?> sample) {
        FormManifest form = sample.get("form") == null ? null : FormManifest.parse(sample.get("form"));
        Map<?, ?> c = (Map<?, ?>) sample.get("context");
        Map<?, ?> registered = c == null ? null : (Map<?, ?>) c.get("registered");
        List<?> processes = c == null ? null : (List<?>) c.get("processes");
        Map<?, ?> calls = c == null ? null : (Map<?, ?>) c.get("calls");
        List<?> calendars = c == null ? null : (List<?>) c.get("calendars");
        return new ValidationContext() {
            @Override public FormManifest form() { return form; }

            @Override public Boolean isRegistered(ExtensionKind kind, String name) {
                if (registered == null) return null;
                List<?> names = (List<?>) registered.get(kind.name().toLowerCase(Locale.ROOT));
                return names == null || names.contains(name);
            }

            @Override public Boolean processExists(String key) { return processes == null ? null : processes.contains(key); }

            @Override public List<String> callsOf(String key) {
                if (calls == null) return null;
                List<?> next = (List<?>) calls.get(key);
                return next == null ? List.of() : next.stream().map(String::valueOf).toList();
            }

            @Override public Boolean calendarExists(String name) { return calendars == null ? null : calendars.contains(name); }
        };
    }

    /** 每个问题写成固定键序的 JSON 再排序，同 TS 一侧的 normalize */
    private static List<String> normalize(List<Problem> problems) {
        List<String> out = new ArrayList<>();
        for (Problem p : problems) {
            out.add(Values.toText(Arrays.asList(p.getCode(), p.getSeverity().name().toLowerCase(Locale.ROOT), p.getNode(), p.getEdge(), p.getLane(),
                p.getParams().isEmpty() ? null : p.getParams())));
        }
        out.sort(null);
        return out;
    }

    private static List<String> normalizeExpected(List<?> problems) {
        List<String> out = new ArrayList<>();
        for (Object item : problems) {
            Map<?, ?> p = (Map<?, ?>) item;
            out.add(Values.toText(Arrays.asList(p.get("code"), p.get("severity"), p.get("node"), p.get("edge"), p.get("lane"), p.get("params"))));
        }
        out.sort(null);
        return out;
    }
}
