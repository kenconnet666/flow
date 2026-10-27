package org.dpdns.zerodep.flow.core.engine.form;

import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.Execution;
import org.dpdns.zerodep.flow.core.engine.LoadedDefinition;
import org.dpdns.zerodep.flow.core.form.FieldErrors;
import org.dpdns.zerodep.flow.core.form.FormAction;
import org.dpdns.zerodep.flow.core.form.FormContext;
import org.dpdns.zerodep.flow.core.form.FormHandler;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.model.error.ErrorDetail;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 在固定的时机调表单的后端代码（{@link FormHandler}）：发起页的初始值、提交时的校验与计算与写业务表、给前端调的接口。
 * 表单没有后端代码时什么都不做。后端代码抛的不是业务错误码的异常包成 {@code flow.extension-failed}。
 */
public class FormCode {
    private FormCode() { }

    /**
     * 提交（发起、处理）：表单已经按清单与字段权限合并、校验过；依次 validate → compute（覆盖算出的字段）→ submit，
     * 都在流转的事务里。返回最后的值。
     */
    public static FormValues submit(Execution run, LoadedDefinition definition, String node, boolean start, FormValues values) {
        Optional<FormHandler> handler = handlerOf(run.getContext(), definition);
        if (handler.isEmpty()) return values;
        FormContext ctx = new FormContext(run.getActor(), run.getInstance().getProcessKey(), run.getInstance().getId(), node, start);
        FieldErrors errors = call(definition, () -> handler.get().validate(values, ctx));
        if (!errors.isEmpty()) {
            List<ErrorDetail> details = errors.getMessages().entrySet().stream()
                .map(e -> new ErrorDetail("custom", e.getKey(), null, null, null, Map.of("message", e.getValue()))).toList();
            throw new FlowException(FlowErrorCode.VALIDATION_FAILED, "the form backend code rejected " + details.size() + " field(s)", details);
        }
        FormValues computed = call(definition, () -> handler.get().compute(values, ctx));
        // 后端算的值也按清单的字段类型读（写成文字、数字都认）
        FormValues result = computed.isEmpty() ? values : values.with(FormValues.of(definition.getManifest().typed(computed.asMap())));
        call(definition, () -> {
            handler.get().submit(result, ctx);
            return null;
        });
        return result;
    }

    /** 发起页的初始值：后端代码的 load（没有后端代码时是空的） */
    public static FormValues load(EngineContext context, LoadedDefinition definition, Actor actor) {
        Optional<FormHandler> handler = handlerOf(context, definition);
        if (handler.isEmpty()) return FormValues.empty();
        FormContext ctx = new FormContext(actor, definition.model().getKey(), null, definition.getGraph().start().getId(), true);
        FormValues loaded = call(definition, () -> handler.get().load(ctx));
        return loaded.isEmpty() ? loaded : FormValues.of(definition.getManifest().typed(loaded.asMap()));
    }

    /** 前端调后端代码的一个接口；没有这个接口时报 {@code flow.not-found} */
    public static Object action(EngineContext context, Long formVersionId, String name, Map<String, Object> args, FormContext ctx) {
        FormHandler handler = context.getFormHandlers().of(formVersionId)
            .orElseThrow(() -> new FlowException(FlowErrorCode.NOT_FOUND, Map.of("formAction", name)));
        FormAction action = handler.actions().get(name);
        if (action == null) throw new FlowException(FlowErrorCode.NOT_FOUND, Map.of("formAction", name));
        try {
            return action.call(args, ctx);
        } catch (FlowException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FlowException(FlowErrorCode.EXTENSION_FAILED, "form action " + name + " failed", Map.of("kind", "formAction", "name", name),
                List.of(), e);
        }
    }

    private static Optional<FormHandler> handlerOf(EngineContext context, LoadedDefinition definition) {
        return definition.hasForm() ? context.getFormHandlers().of(definition.form().getId()) : Optional.empty();
    }

    private static <T> T call(LoadedDefinition definition, java.util.function.Supplier<T> work) {
        try {
            return work.get();
        } catch (FlowException e) {
            throw e;
        } catch (RuntimeException e) {
            String form = definition.form().getFormKey();
            throw new FlowException(FlowErrorCode.EXTENSION_FAILED, "form " + form + " backend code failed", Map.of("kind", "form", "name", form),
                List.of(), e);
        }
    }
}
