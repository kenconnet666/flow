package org.dpdns.zerodep.flow.model.form;

import org.dpdns.zerodep.flow.model.Conditions;
import org.dpdns.zerodep.flow.model.Values;
import org.dpdns.zerodep.flow.model.definition.FieldAccess;
import org.dpdns.zerodep.flow.model.definition.NodeFormAccess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 表单字段在一个节点上的最终状态（docs/decisions/0004 的 4.2），以及提交时怎样合并（4.5），与 TS 模型包的 {@code access.ts}
 * 同一套规则，两边跑 spec/access 的样例。
 * <p>合算的顺序：节点上 {@code hidden} → 隐藏；{@code visibleWhen} 不成立 → 隐藏（值丢掉）；{@code readonly} → 只读、不算必填；
 * {@code required} → 必填；{@code editable} → 按清单的 {@code required}、{@code requiredWhen}。
 * 明细表的列没单独写时跟着明细表；明细表隐藏时列都隐藏；明细表能改时才能增删行。
 */
public final class FormAccess {
    private FormAccess() { }

    /** 不显示的原因 */
    public enum HiddenBy {
        /** 节点上的权限：值照旧存着，只是这个人看不到 */
        ACCESS,
        /** 显示条件不成立：值丢掉 */
        CONDITION
    }

    /**
     * 一个字段在这个节点上的状态。
     *
     * @param visible  显示
     * @param editable 能改；明细表能改时才能增删行
     * @param required 必填
     * @param hiddenBy 不显示的原因；显示时为 null
     */
    public record FieldState(boolean visible, boolean editable, boolean required, HiddenBy hiddenBy) {
        static final FieldState BY_ACCESS = new FieldState(false, false, false, HiddenBy.ACCESS);
        static final FieldState BY_CONDITION = new FieldState(false, false, false, HiddenBy.CONDITION);
    }

    /**
     * 每个字段（明细表的列写成 {@code 列表.列}）在这个节点上的状态，按清单里的先后。
     *
     * @param access  节点上的权限；可以是 null。不在待办里看表单的人传流程级的 formView（0004 的 4.3）
     * @param isStart 是不是发起节点：没写 default 时发起节点 editable、其他节点 readonly
     * @param read    条件里按字段名取值
     */
    public static Map<String, FieldState> fieldStates(FormManifest manifest, NodeFormAccess access, boolean isStart, Function<String, Object> read) {
        Map<String, FieldState> states = new LinkedHashMap<>();
        FieldAccess fallback = access != null && access.defaultAccess() != null ? access.defaultAccess()
            : isStart ? FieldAccess.EDITABLE : FieldAccess.READONLY;
        Map<String, FieldAccess> own = access == null ? Map.of() : access.fields();
        for (Map.Entry<String, FormManifest.Field> entry : manifest.fields().entrySet()) {
            String name = entry.getKey();
            FieldAccess fieldAccess = own.getOrDefault(name, fallback);
            FieldState state = stateOf(entry.getValue(), fieldAccess, read);
            states.put(name, state);
            FormManifest columns = entry.getValue().columns();
            if (columns == null) continue;
            for (Map.Entry<String, FormManifest.Field> column : columns.fields().entrySet()) {
                String path = name + "." + column.getKey();
                states.put(path, state.visible() ? stateOf(column.getValue(), own.getOrDefault(path, fieldAccess), read) : state);
            }
        }
        return Collections.unmodifiableMap(states);
    }

    private static FieldState stateOf(FormManifest.Field field, FieldAccess access, Function<String, Object> read) {
        if (access == FieldAccess.HIDDEN) return FieldState.BY_ACCESS;
        if (field.visibleWhen() != null && !Conditions.evaluate(field.visibleWhen(), read)) return FieldState.BY_CONDITION;
        if (access == FieldAccess.READONLY) return new FieldState(true, false, false, null);
        boolean required = access == FieldAccess.REQUIRED || field.required()
            || (field.requiredWhen() != null && Conditions.evaluate(field.requiredWhen(), read));
        return new FieldState(true, true, required, null);
    }

    /**
     * 提交时一个字段的问题。
     *
     * @param field 字段路径；明细表的列带行号：{@code lines[0].price}
     * @param code  {@code required}：必填没填
     */
    public record SubmissionError(String field, String code) { }

    /**
     * 合并的结果。
     *
     * @param values 合并后要存下的表单的值
     * @param errors 问题
     */
    public record Submission(Map<String, Object> values, List<SubmissionError> errors) { }

    /** 一个字段合并后的值：能改的取提交的，看得到但不能改、或节点上隐藏的用存着的，显示条件不成立的丢掉 */
    private static Object pick(FieldState state, Object stored, Object submitted) {
        if (state.hiddenBy() == HiddenBy.CONDITION) return null;
        return state.editable() ? submitted : stored;
    }

    /**
     * 收提交（0004 的 4.5）：只取能改的字段，其余用存着的值；被显示条件藏起来的字段值丢掉（存成 null）；再查必填。
     * 明细表能增删行时取提交上来的行，不能改的列按行号用存着的值；不能增删行时行数用存着的，只取能改的列。
     */
    public static Submission mergeSubmission(FormManifest manifest, Map<String, FieldState> states, Map<String, Object> stored,
                                             Map<String, Object> submitted) {
        Map<String, Object> values = new LinkedHashMap<>();
        List<SubmissionError> errors = new ArrayList<>();
        for (Map.Entry<String, FormManifest.Field> entry : manifest.fields().entrySet()) {
            String name = entry.getKey();
            FieldState state = states.getOrDefault(name, FieldState.BY_ACCESS);
            FormManifest columns = entry.getValue().columns();
            Object value = columns != null && state.visible()
                ? mergeRows(name, columns, state, states, stored.get(name), submitted.get(name), errors)
                : pick(state, stored.get(name), submitted.get(name));
            values.put(name, value);
            if (state.required() && Values.isBlank(value)) errors.add(new SubmissionError(name, "required"));
        }
        return new Submission(Collections.unmodifiableMap(values), List.copyOf(errors));
    }

    private static List<Map<String, Object>> mergeRows(String name, FormManifest columns, FieldState state, Map<String, FieldState> states,
                                                       Object stored, Object submitted, List<SubmissionError> errors) {
        List<?> storedRows = stored instanceof List<?> l ? l : List.of();
        List<?> submittedRows = submitted instanceof List<?> l ? l : List.of();
        int count = state.editable() ? submittedRows.size() : storedRows.size();
        List<Map<String, Object>> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Map<?, ?> storedRow = i < storedRows.size() && storedRows.get(i) instanceof Map<?, ?> m ? m : Map.of();
            Map<?, ?> submittedRow = i < submittedRows.size() && submittedRows.get(i) instanceof Map<?, ?> m ? m : Map.of();
            Map<String, Object> row = new LinkedHashMap<>();
            for (String column : columns.fields().keySet()) {
                FieldState columnState = states.getOrDefault(name + "." + column, FieldState.BY_ACCESS);
                Object value = pick(columnState, storedRow.get(column), submittedRow.get(column));
                row.put(column, value);
                if (columnState.required() && Values.isBlank(value)) errors.add(new SubmissionError(name + "[" + i + "]." + column, "required"));
            }
            rows.add(Collections.unmodifiableMap(row));
        }
        return Collections.unmodifiableList(rows);
    }
}
