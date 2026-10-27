package org.dpdns.zerodep.flow.core.engine.form;

import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.extension.Extensions;
import org.dpdns.zerodep.flow.core.extension.FieldType;
import org.dpdns.zerodep.flow.model.Values;
import org.dpdns.zerodep.flow.model.definition.NodeFormAccess;
import org.dpdns.zerodep.flow.model.error.ErrorDetail;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.dpdns.zerodep.flow.model.form.FormAccess;
import org.dpdns.zerodep.flow.model.form.FormManifest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 收表单（0004 的 4.5）：按节点上的字段权限，能改的字段取提交的值，其余用存着的值，再查必填与值的类型。
 * 规则都在模型包（{@link FormAccess}），前端与两个后端用同一份；接入方注册的字段类型（{@link FieldType}）由它自己查。
 */
public class FormSubmission {
    private FormSubmission() { }

    /**
     * 合并出新的表单值；有问题时报 {@code flow.validation-failed}，errors 里是按字段的问题。
     *
     * @param access    这个节点上的字段权限（开始节点或人工节点上写的）
     * @param isStart   是不是发起：发起时没写权限的字段默认能改
     * @param stored    存着的值（发起时是空的）
     * @param submitted 提交上来的值
     * @param read      取 {@code sys.}、流程变量这类引用（显示条件里可能用到）
     */
    public static FormValues merge(Extensions extensions, FormManifest manifest, NodeFormAccess access, boolean isStart, FormValues stored,
                                   FormValues submitted, Function<String, Object> read) {
        // 显示条件按提交后的值算：同一次提交里改了的字段，立刻影响别的字段显不显示
        Map<String, Object> afterSubmit = new HashMap<>(stored.asMap());
        afterSubmit.putAll(submitted.asMap());
        Function<String, Object> readAfterSubmit = name ->
            name.startsWith("form.") ? afterSubmit.get(name.substring("form.".length())) : read.apply(name);

        Map<String, FormAccess.FieldState> states = FormAccess.fieldStates(manifest, access, isStart, readAfterSubmit);
        FormAccess.Submission submission = FormAccess.mergeSubmission(manifest, states, stored.asMap(), submitted.asMap());
        List<ErrorDetail> problems = new ArrayList<>(submission.getErrors().stream().map(ErrorDetail::of).toList());
        problems.addAll(customTypeProblems(extensions, manifest, submission.getValues()));
        if (!problems.isEmpty()) throw new FlowException(FlowErrorCode.VALIDATION_FAILED, "the form has " + problems.size() + " problem(s)", problems);
        return FormValues.of(submission.getValues());
    }

    /**
     * 暂存的值：只留提交里这个节点上能改的字段，按类型、长度、范围查（不查必填：还没填完）；有问题时同样报 {@code flow.validation-failed}。
     */
    public static FormValues draft(Extensions extensions, FormManifest manifest, NodeFormAccess access, boolean isStart, FormValues stored,
                                   FormValues submitted, Function<String, Object> read) {
        Map<String, FormAccess.FieldState> states = FormAccess.fieldStates(manifest, access, isStart, read);
        FormAccess.Submission submission = FormAccess.mergeSubmission(manifest, states, stored.asMap(), submitted.asMap());
        List<ErrorDetail> problems = new ArrayList<>(submission.getErrors().stream().filter(e -> !"required".equals(e.getCode())).map(ErrorDetail::of).toList());
        problems.addAll(customTypeProblems(extensions, manifest, submitted.asMap()));
        if (!problems.isEmpty()) throw new FlowException(FlowErrorCode.VALIDATION_FAILED, "the draft has " + problems.size() + " problem(s)", problems);
        Map<String, Object> editable = new LinkedHashMap<>();
        submitted.asMap().forEach((name, value) -> {
            FormAccess.FieldState state = states.get(name);
            if (state != null && state.isEditable()) editable.put(name, value);
        });
        return FormValues.of(editable);
    }

    /** 注册的字段类型（顶层字段）按它自己的规则查；空值不查 */
    private static List<ErrorDetail> customTypeProblems(Extensions extensions, FormManifest manifest, Map<String, Object> values) {
        List<ErrorDetail> problems = new ArrayList<>();
        manifest.fields().forEach((name, field) -> {
            FieldType type = extensions.fieldType(field.getType());
            Object value = values.get(name);
            if (type == null || Values.isBlank(value)) return;
            for (String code : type.check(field.getAttributes(), value)) problems.add(ErrorDetail.field(name, code));
        });
        return problems;
    }
}
