package org.dpdns.zerodep.flow.model.form;

import org.dpdns.zerodep.flow.model.Condition;
import org.dpdns.zerodep.flow.model.Conditions;
import org.dpdns.zerodep.flow.model.Values;
import org.dpdns.zerodep.flow.model.validate.Problem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 字段清单自身的校验（docs/decisions/0004 的 1.2、1.3），与 TS 模型包的 {@code validate-manifest.ts} 同一套规则，
 * 两边跑 spec/manifests 的样例。问题的形状同流程定义的校验，位置写在 {@link Problem#field()} 上。
 */
public class ManifestValidator {
    /** 内置的字段类型 */
    public static final Set<String> BUILTIN_TYPES = Set.of("text", "number", "decimal", "boolean", "date", "datetime", "select", "people", "file", "list");

    private static final Pattern FIELD_NAME = Pattern.compile("[a-z][A-Za-z0-9]{0,63}");
    private static final Set<String> SYSTEM_NAMES = Set.of("initiator", "startedAt", "instanceId", "businessKey");
    private static final List<String> COUNTS = List.of("minLength", "maxLength", "scale", "maxCount", "maxSize", "minItems", "maxItems");
    private static final Set<String> RANGED = Set.of("number", "decimal", "date", "datetime");

    private final FormManifest manifest;
    private final Predicate<String> isFieldType;
    private final List<Problem> problems = new ArrayList<>();

    private ManifestValidator(FormManifest manifest, Predicate<String> isFieldType) {
        this.manifest = manifest;
        this.isFieldType = isFieldType == null ? type -> false : isFieldType;
    }

    /**
     * 校验字段清单。
     *
     * @param isFieldType 接入方注册的字段类型；null 时不认内置以外的类型
     */
    public static List<Problem> validate(FormManifest manifest, Predicate<String> isFieldType) {
        ManifestValidator validator = new ManifestValidator(manifest, isFieldType);
        validator.visit(manifest, "", false);
        return List.copyOf(validator.problems);
    }

    private void report(String code, String field, String... params) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < params.length; i += 2) map.put(params[i], params[i + 1]);
        problems.add(new Problem(code, Problem.Severity.ERROR, null, null, null, field, map));
    }

    private void visit(FormManifest fields, String prefix, boolean inList) {
        for (Map.Entry<String, FormManifest.Field> entry : fields.fields().entrySet()) {
            String path = prefix + entry.getKey();
            FormManifest.Field field = entry.getValue();
            checkField(field, path, entry.getKey());
            if (field.getType().equals("list")) {
                if (inList) report("list-nested", path);
                else if (field.getColumns() == null || field.getColumns().fields().isEmpty()) report("list-no-columns", path);
                else visit(field.getColumns(), path + ".", true);
            }
        }
    }

    private void checkField(FormManifest.Field field, String path, String name) {
        Map<String, Object> a = field.getAttributes();
        if (!FIELD_NAME.matcher(name).matches()) report("field-name-invalid", path);
        if (!(a.get("label") instanceof String label) || label.isBlank()) report("field-label-missing", path);
        if (!BUILTIN_TYPES.contains(field.getType()) && !isFieldType.test(field.getType())) report("field-type-unknown", path, "type", field.getType());

        for (String attribute : COUNTS) {
            Object value = a.get(attribute);
            if (value != null && !(value instanceof Long l && l >= 0)) report("attribute-invalid", path, "attribute", attribute);
        }
        if (field.getType().equals("people") && a.get("max") != null && !(a.get("max") instanceof Long l && l >= 1)) {
            report("attribute-invalid", path, "attribute", "max");
        }
        if (a.get("pattern") instanceof String pattern && !compiles(pattern)) report("pattern-invalid", path);
        checkRanges(field, path);

        if (field.getType().equals("select") && a.get("options") instanceof List<?> options) {
            if (options.isEmpty()) report("options-empty", path);
            Set<String> seen = new HashSet<>();
            Set<String> repeated = new LinkedHashSet<>();
            for (Object option : options) {
                Object value = option instanceof Map<?, ?> m ? m.get("value") : null;
                String key = (value instanceof String ? "s:" : "n:") + Values.toText(value);
                if (!seen.add(key)) repeated.add(Values.toText(value));
            }
            for (String value : repeated) report("option-duplicate", path, "value", value);
        }

        if (field.getVisibleWhen() != null) checkCondition(field.getVisibleWhen(), path, "visibleWhen");
        if (field.getRequiredWhen() != null) checkCondition(field.getRequiredWhen(), path, "requiredWhen");
        Object defaultValue = a.get("default");
        if (defaultValue != null && !FieldValues.check(field, defaultValue).isEmpty()) report("default-invalid", path);
    }

    private static boolean compiles(String pattern) {
        try {
            Pattern.compile(pattern);
            return true;
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    /** 范围的属性：类型对、下限不大于上限 */
    private void checkRanges(FormManifest.Field field, String path) {
        Map<String, Object> a = field.getAttributes();
        if (a.get("minLength") instanceof Long min && a.get("maxLength") instanceof Long max && min > max) report("range-invalid", path, "attribute", "length");
        if (a.get("minItems") instanceof Long min && a.get("maxItems") instanceof Long max && min > max) report("range-invalid", path, "attribute", "items");
        if (!RANGED.contains(field.getType())) return;
        // 范围本身要是这种类型的合法值（decimal 的上下限是文字，date 的是日期）
        FormManifest.Field plain = new FormManifest.Field(field.getLabel(), field.getType(), false, false, null, null, null, Map.of());
        for (String attribute : List.of("min", "max")) {
            Object bound = a.get(attribute);
            if (bound != null && !FieldValues.check(plain, bound).isEmpty()) report("attribute-invalid", path, "attribute", attribute);
        }
        if (a.get("min") != null && a.get("max") != null && Values.compare(a.get("min"), a.get("max")) > 0) report("range-invalid", path, "attribute", "value");
    }

    /** 表单条件：写完整、不用 fn.、只引用表单字段与 sys. 名字 */
    private void checkCondition(Condition condition, String path, String which) {
        if (!Conditions.isComplete(condition)) report("condition-incomplete", path, "condition", which);
        for (String ref : Conditions.fields(condition)) {
            if (ref.startsWith("fn.")) report("condition-fn-in-form", path, "condition", which, "ref", ref);
            else if (ref.startsWith("sys.")) {
                if (!SYSTEM_NAMES.contains(ref.substring(4))) report("ref-unknown", path, "condition", which, "ref", ref);
            } else if (!ref.startsWith("form.") || manifest.field(ref.substring(5)) == null) {
                report("ref-unknown", path, "condition", which, "ref", ref);
            }
        }
    }
}
