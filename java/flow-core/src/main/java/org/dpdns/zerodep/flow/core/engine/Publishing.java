package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.command.FormPublication;
import org.dpdns.zerodep.flow.core.extension.Extensions;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.store.DefinitionStore.StoredDefinition;
import org.dpdns.zerodep.flow.core.store.DefinitionStore.StoredProcess;
import org.dpdns.zerodep.flow.core.store.DefinitionStore;
import org.dpdns.zerodep.flow.core.store.FormStore.StoredForm;
import org.dpdns.zerodep.flow.core.store.FormStore;
import org.dpdns.zerodep.flow.core.view.DefinitionProblem;
import org.dpdns.zerodep.flow.core.view.ProcessSummary;
import org.dpdns.zerodep.flow.model.definition.Definitions;
import org.dpdns.zerodep.flow.model.definition.ProcessDefinition;
import org.dpdns.zerodep.flow.model.error.ErrorDetail;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.dpdns.zerodep.flow.model.form.FormManifest;
import org.dpdns.zerodep.flow.model.form.ManifestValidator;
import org.dpdns.zerodep.flow.model.validate.Problem;
import org.dpdns.zerodep.flow.model.validate.ProcessValidator;
import org.dpdns.zerodep.flow.model.validate.ValidationContext;
import org.dpdns.zerodep.wire.Json;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 发布流程定义与表单（0006 的 3.5）：先按模型包的规则校验，有错误时不发布，把问题列表放进错误明细；通过了交给存储定版本号。
 * 草稿、复制、导入导出、比较版本随设计器补上。
 */
public final class Publishing {
    private final EngineContext context;

    public Publishing(EngineContext context) { this.context = context; }

    /**
     * 发布一个流程定义（0005 的 JSON 文字）。引用的表单没写版本时定在当前最新的版本上，之后表单再发布也不影响这个流程版本。
     * 校验有错误时报 {@code flow.definition-invalid}，警告不拦。
     */
    public StoredDefinition publishDefinition(Actor actor, String json, String note) {
        ProcessDefinition model = parseDefinition(json);
        StoredForm form = model.form() == null ? null : formOf(model.form());
        FormManifest manifest = form == null ? null : FormManifest.parse(Json.read(form.manifest()));
        List<Problem> errors = errorsOnly(ProcessValidator.validate(model, new PublishContext(manifest, context.extensions())));
        if (!errors.isEmpty()) {
            throw new FlowException(FlowErrorCode.DEFINITION_INVALID, "the definition has " + errors.size() + " error(s)",
                errors.stream().map(ErrorDetail::of).toList());
        }
        return context.stores().definitions().publish(new DefinitionStore.Publication(model.key(), model.name(), model.category(), json,
            form == null ? null : form.formKey(), form == null ? null : form.version(), note, actor.id(), actor.name(),
            context.clock().instant()));
    }

    /**
     * 校验一份定义，不发布：错误与警告都列出来（设计、编辑定义时用）。JSON 写错、形状不对时只有一条 {@code malformed}。
     */
    public List<DefinitionProblem> validateDefinition(String json) {
        ProcessDefinition model;
        try {
            model = Definitions.parse(json);
        } catch (IllegalArgumentException e) {
            return List.of(new DefinitionProblem("malformed", "error", null, null, null, null, Map.of("reason", String.valueOf(e.getMessage()))));
        }
        FormManifest manifest = null;
        if (model.form() != null) {
            var form = model.form().version() == null ? context.stores().forms().latest(model.form().key())
                : context.stores().forms().version(model.form().key(), model.form().version());
            if (form.isEmpty()) {
                return List.of(new DefinitionProblem("form-unknown", "error", null, null, null, "form", Map.of("key", model.form().key())));
            }
            manifest = FormManifest.parse(Json.read(form.get().manifest()));
        }
        return ProcessValidator.validate(model, new PublishContext(manifest, context.extensions())).stream()
            .map(p -> new DefinitionProblem(p.code(), p.severity().name().toLowerCase(Locale.ROOT), p.node(), p.edge(), p.lane(),
                p.field(), p.params()))
            .toList();
    }

    /** 停用、启用流程：停用后不能再发起，进行中的照常走完 */
    public ProcessSummary setStatus(String processKey, boolean active) {
        String status = active ? StoredProcess.ACTIVE : StoredProcess.DISABLED;
        if (!context.stores().definitions().setStatus(processKey, status)) throw FlowErrors.notFound("process", processKey);
        StoredProcess p = context.stores().definitions().process(processKey).orElseThrow();
        return new ProcessSummary(p.processKey(), p.name(), p.category(), p.status(), p.latestVersion());
    }

    /** 发布一张表单：先校验字段清单本身（0004 的 1.2），有错误时报 {@code flow.validation-failed} */
    public StoredForm publishForm(Actor actor, FormPublication form) {
        FormManifest manifest = parseManifest(form.manifest());
        List<Problem> errors = errorsOnly(ManifestValidator.validate(manifest, ManifestValidator.BUILTIN_TYPES::contains));
        if (!errors.isEmpty()) {
            throw new FlowException(FlowErrorCode.VALIDATION_FAILED, "the form manifest has " + errors.size() + " error(s)",
                errors.stream().map(ErrorDetail::of).toList());
        }
        return context.stores().forms().publish(new FormStore.Publication(form.key(), form.name(), form.category(), form.manifest(),
            form.clientSource(), form.clientCompiled(), form.compilerVersion(), form.serverLanguage(), form.serverSource(), form.note(),
            actor.id(), actor.name(), context.clock().instant()));
    }

    /** 读定义的 JSON；形状不对（缺键、类型不对、JSON 写错）时报 {@code flow.definition-invalid}，消息里带出错的位置 */
    private static ProcessDefinition parseDefinition(String json) {
        try {
            return Definitions.parse(json);
        } catch (IllegalArgumentException e) {
            throw new FlowException(FlowErrorCode.DEFINITION_INVALID, e.getMessage(), List.of(new ErrorDetail("malformed", null, null, null, null,
                Map.of("reason", String.valueOf(e.getMessage())))));
        }
    }

    /** 读字段清单的 JSON；形状不对时报 {@code flow.validation-failed} */
    private static FormManifest parseManifest(String json) {
        try {
            return FormManifest.parse(Json.read(json));
        } catch (IllegalArgumentException e) {
            throw new FlowException(FlowErrorCode.VALIDATION_FAILED, e.getMessage(), List.of(new ErrorDetail("malformed", "manifest", null, null,
                null, Map.of("reason", String.valueOf(e.getMessage())))));
        }
    }

    /** 定义引用的表单：写了版本取那个版本，没写取最新的 */
    private StoredForm formOf(ProcessDefinition.FormRef ref) {
        if (ref.version() == null) {
            return context.stores().forms().latest(ref.key()).orElseThrow(() -> FlowErrors.notFound("form", ref.key()));
        }
        return context.stores().forms().version(ref.key(), ref.version())
            .orElseThrow(() -> FlowErrors.notFound("form", ref.key() + "@" + ref.version()));
    }

    private static List<Problem> errorsOnly(List<Problem> problems) {
        return problems.stream().filter(p -> p.severity() == Problem.Severity.ERROR).toList();
    }

    /**
     * 发布时的校验上下文：给出表单的字段清单（查 {@code form.} 引用）与注册了的解析器、动作、守卫（{@link Extensions}）。
     * 计算值、会签规则现在还没有注册口，定义里用到就报"未注册"；自定义节点类型不知道，不查。
     */
    private record PublishContext(FormManifest form, Extensions extensions) implements ValidationContext {
        @Override
        public Boolean isRegistered(ExtensionKind kind, String name) {
            return switch (kind) {
                case RESOLVER -> extensions.hasResolver(name);
                case ACTION -> extensions.hasAction(name);
                case GUARD -> extensions.hasGuard(name);
                case NODE -> null;
                default -> false;
            };
        }
    }
}
