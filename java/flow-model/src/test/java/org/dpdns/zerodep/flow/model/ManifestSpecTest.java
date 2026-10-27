package org.dpdns.zerodep.flow.model;

import org.dpdns.zerodep.flow.model.form.FieldValues;
import org.dpdns.zerodep.flow.model.form.FormManifest;
import org.dpdns.zerodep.flow.model.form.ManifestValidator;
import org.dpdns.zerodep.flow.model.validate.Problem;
import org.dpdns.zerodep.wire.Wire;
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

/** 字段清单的校验与值的校验：spec/manifests、spec/values 的样例与 TS 一侧共用 */
@DisplayName("字段清单与值")
class ManifestSpecTest {
    @TestFactory
    @DisplayName("spec/manifests")
    Stream<DynamicTest> manifests() {
        return Spec.list("manifests").stream().map(file -> {
            Map<?, ?> sample = Spec.read("manifests/" + file);
            return DynamicTest.dynamicTest(file + "：" + sample.get("name"), () -> {
                Map<?, ?> context = (Map<?, ?>) sample.get("context");
                List<?> types = context == null ? null : (List<?>) context.get("fieldTypes");
                List<Problem> actual = ManifestValidator.validate(FormManifest.parse(sample.get("manifest")), types == null ? null : types::contains);
                assertEquals(expected((List<?>) sample.get("expect")), normalize(actual));
            });
        });
    }

    @TestFactory
    @DisplayName("spec/values/field-values.json")
    Stream<DynamicTest> values() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Object item : (List<?>) Spec.read("values/field-values.json").get("cases")) {
            Map<?, ?> sample = (Map<?, ?>) Wire.decode(item);   // 值同线上：typed JSON 还原成 BigDecimal、LocalDate……
            tests.add(DynamicTest.dynamicTest((String) sample.get("name"), () -> {
                FormManifest.Field field = FormManifest.parse(Map.of("f", sample.get("field"))).field("f");
                List<List<String>> actual = FieldValues.check(field, sample.get("value")).stream().map(e -> List.of(e.path(), e.code())).toList();
                assertEquals(sample.get("expect"), actual);
            }));
        }
        return tests.stream();
    }

    private static List<String> normalize(List<Problem> problems) {
        List<String> out = new ArrayList<>();
        for (Problem p : problems) {
            out.add(Values.toText(Arrays.asList(p.code(), p.severity().name().toLowerCase(Locale.ROOT), p.field(), p.params().isEmpty() ? null : p.params())));
        }
        out.sort(null);
        return out;
    }

    private static List<String> expected(List<?> problems) {
        List<String> out = new ArrayList<>();
        for (Object item : problems) {
            Map<?, ?> p = (Map<?, ?>) item;
            out.add(Values.toText(Arrays.asList(p.get("code"), p.get("severity"), p.get("field"), p.get("params"))));
        }
        out.sort(null);
        return out;
    }
}
