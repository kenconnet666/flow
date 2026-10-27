package org.dpdns.zerodep.flow.core.engine.handler;

import lombok.Data;
import org.dpdns.zerodep.flow.core.command.FormPublication;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.Extensions;
import org.dpdns.zerodep.flow.core.engine.FlowErrors;
import org.dpdns.zerodep.flow.core.engine.form.FormCompiler;
import org.dpdns.zerodep.flow.core.entity.DefinitionEntity;
import org.dpdns.zerodep.flow.core.entity.DraftEntity;
import org.dpdns.zerodep.flow.core.entity.FormEntity;
import org.dpdns.zerodep.flow.core.entity.FormVersionEntity;
import org.dpdns.zerodep.flow.core.entity.ProcessEntity;
import org.dpdns.zerodep.flow.core.spi.Actor;
import org.dpdns.zerodep.flow.core.view.definition.DefinitionProblem;
import org.dpdns.zerodep.flow.core.view.definition.ProcessSummary;
import org.dpdns.zerodep.flow.core.view.form.FormSummary;
import org.dpdns.zerodep.flow.model.definition.DefinitionSchema;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 发布流程定义与表单（0006 的 3.5）：先按模型包的规则校验，有错误时不发布，把问题列表放进错误明细；通过了定版本号、存一份不可变的版本。
 * 版本号：第一次发布时建上流程（表单）本身、是 1，之后最新版本号加一——只在最新版本还是读到的那个时改到，两个人同时发布时后到的失败。
 * 草稿、复制、导入导出、比较版本随设计器补上。
 */
public class Publishing {
    private final EngineContext context;
    private final DesignDraftHandler drafts;

    public Publishing(EngineContext context) {
        this.context = context;
        this.drafts = new DesignDraftHandler(context);
    }

    /**
     * 发布一个流程定义（0005 的 JSON 文字）。引用的表单没写版本时定在当前最新的版本上，之后表单再发布也不影响这个流程版本。
     * 校验有错误时报 {@code flow.definition-invalid}，警告不拦。
     */
    public DefinitionEntity publishDefinition(Actor actor, String json, String note) {
        ProcessDefinition model = parseDefinition(json);
        DefinitionProblem disabled = disabledForm(model);
        if (disabled != null) {
            throw new FlowException(FlowErrorCode.DEFINITION_INVALID, "the form " + model.getForm().getKey() + " is disabled",
                List.of(new ErrorDetail(disabled.getCode(), disabled.getField(), null, null, null, disabled.getParams())));
        }
        FormVersionEntity form = model.getForm() == null ? null : formOf(model.getForm());
        FormManifest manifest = form == null ? null : FormManifest.parse(Json.read(form.getManifest()));
        List<Problem> errors = errorsOnly(ProcessValidator.validate(model, new PublishContext(manifest, context.getExtensions())));
        if (!errors.isEmpty()) {
            throw new FlowException(FlowErrorCode.DEFINITION_INVALID, "the definition has " + errors.size() + " error(s)",
                errors.stream().map(ErrorDetail::of).toList());
        }
        return context.transaction(() -> {
            Instant now = context.getClock().instant();
            int version = nextProcessVersion(actor, model, now);
            DefinitionEntity row = new DefinitionEntity();
            row.setProcessKey(model.getKey());
            row.setVersion(version);
            row.setModel(json);
            row.setFormKey(form == null ? null : form.getFormKey());
            row.setFormVersion(form == null ? null : form.getVersion());
            row.setNote(note);
            row.setPublishedBy(actor.getId());
            row.setPublishedByName(actor.getName());
            row.setPublishedAt(now);
            context.getDb().insertable(row).executeRows(true);
            // 发布了，设计中的草稿就不再要
            drafts.delete(DraftEntity.PROCESS, model.getKey());
            return row;
        });
    }

    /** 流程本身：没有时建上（版本 1），有时最新版本号加一并换上名称、分类；返回新的版本号 */
    private int nextProcessVersion(Actor actor, ProcessDefinition model, Instant now) {
        ProcessEntity process = context.getDb().queryable(ProcessEntity.class).where(p -> p.processKey().eq(model.getKey())).singleOrNull();
        if (process == null) {
            ProcessEntity created = new ProcessEntity();
            created.setProcessKey(model.getKey());
            created.setName(model.getName());
            created.setCategory(model.getCategory());
            created.setStatus(ProcessEntity.ACTIVE);
            created.setLatestVersion(1);
            created.setRev(0);
            created.setCreatedAt(now);
            created.setUpdatedBy(actor.getId());
            context.getDb().insertable(created).executeRows();
            return 1;
        }
        int current = process.getLatestVersion();
        context.getDb().updatable(ProcessEntity.class)
            .setColumns(p -> {
                p.latestVersion().set(current + 1);
                p.name().set(model.getName());
                p.category().set(model.getCategory());
                p.updatedBy().set(actor.getId());
                p.rev().increment();
            })
            .where(p -> { p.processKey().eq(model.getKey()); p.latestVersion().eq(current); })
            .executeRows(1, "process " + model.getKey() + " was published by someone else at the same time");
        return current + 1;
    }

    /**
     * 校验一份定义，不发布：错误与警告都列出来（设计、编辑定义时用）。JSON 写错、形状不对时只有一条 {@code malformed}。
     */
    public List<DefinitionProblem> validateDefinition(String json) {
        ProcessDefinition model;
        try {
            model = Definitions.parse(json);
        } catch (DefinitionSchema.Newer e) {
            return List.of(new DefinitionProblem("schema-newer", "error", null, null, null, null, Map.of("schema", String.valueOf(e.getVersion()))));
        } catch (IllegalArgumentException e) {
            return List.of(new DefinitionProblem("malformed", "error", null, null, null, null, Map.of("reason", String.valueOf(e.getMessage()))));
        }
        FormManifest manifest = null;
        if (model.getForm() != null) {
            FormVersionEntity form = findForm(model.getForm());
            if (form == null) {
                return List.of(new DefinitionProblem("form-unknown", "error", null, null, null, "form", Map.of("key", model.getForm().getKey())));
            }
            manifest = FormManifest.parse(Json.read(form.getManifest()));
        }
        List<DefinitionProblem> problems = new ArrayList<>();
        DefinitionProblem disabled = disabledForm(model);
        if (disabled != null) problems.add(disabled);
        ProcessValidator.validate(model, new PublishContext(manifest, context.getExtensions())).stream()
            .map(p -> new DefinitionProblem(p.getCode(), p.getSeverity().name().toLowerCase(Locale.ROOT), p.getNode(), p.getEdge(), p.getLane(),
                p.getField(), p.getParams()))
            .forEach(problems::add);
        return problems;
    }

    /** 引用的表单停用了（0004 的 5.3：停用后新发布的流程不能再选它）：{@code form-disabled}；没停用、没引用表单时为 null */
    private DefinitionProblem disabledForm(ProcessDefinition model) {
        if (model.getForm() == null) return null;
        boolean off = context.getDb().queryable(FormEntity.class)
            .where(f -> { f.formKey().eq(model.getForm().getKey()); f.status().eq(FormEntity.DISABLED); })
            .any();
        return off ? new DefinitionProblem("form-disabled", "error", null, null, null, "form", Map.of("key", model.getForm().getKey())) : null;
    }

    /** 停用、启用流程：停用后不能再发起，进行中的照常走完 */
    public ProcessSummary setStatus(String processKey, boolean active) {
        String status = active ? ProcessEntity.ACTIVE : ProcessEntity.DISABLED;
        long changed = context.getDb().updatable(ProcessEntity.class)
            .setColumns(p -> { p.status().set(status); p.rev().increment(); })
            .where(p -> p.processKey().eq(processKey))
            .executeRows();
        if (changed == 0) throw FlowErrors.notFound("process", processKey);
        ProcessEntity p = context.getDb().queryable(ProcessEntity.class).where(r -> r.processKey().eq(processKey)).singleNotNull();
        return new ProcessSummary(p.getProcessKey(), p.getName(), p.getCategory(), p.getStatus(), p.getLatestVersion());
    }

    /** 停用、启用表单：停用后新发布的流程不能再选它，已经引用它的流程照常用 */
    public FormSummary setFormStatus(String formKey, boolean active) {
        String status = active ? FormEntity.ACTIVE : FormEntity.DISABLED;
        long changed = context.getDb().updatable(FormEntity.class)
            .setColumns(f -> { f.status().set(status); f.rev().increment(); })
            .where(f -> f.formKey().eq(formKey))
            .executeRows();
        if (changed == 0) throw FlowErrors.notFound("form", formKey);
        FormEntity f = context.getDb().queryable(FormEntity.class).where(r -> r.formKey().eq(formKey)).singleNotNull();
        return new FormSummary(f.getFormKey(), f.getName(), f.getCategory(), f.getStatus(), f.getLatestVersion());
    }

    /** 发布一张表单：先校验字段清单本身（0004 的 1.2），有错误时报 {@code flow.validation-failed} */
    public FormVersionEntity publishForm(Actor actor, FormPublication form) {
        FormManifest manifest = parseManifest(form.getManifest());
        checkBackendCode(form, context.getFormHandlers().isServerCode());
        List<Problem> errors = errorsOnly(ManifestValidator.validate(manifest, context.getExtensions()::hasFieldType));
        if (!errors.isEmpty()) {
            throw new FlowException(FlowErrorCode.VALIDATION_FAILED, "the form manifest has " + errors.size() + " error(s)",
                errors.stream().map(ErrorDetail::of).toList());
        }
        return context.transaction(() -> {
            Instant now = context.getClock().instant();
            FormVersionEntity row = new FormVersionEntity();
            row.setFormKey(form.getKey());
            row.setVersion(nextFormVersion(actor, form, now));
            row.setManifest(form.getManifest());
            row.setClientSource(form.getClientSource());
            row.setClientCompiled(form.getClientCompiled());
            row.setCompilerVersion(form.getCompilerVersion());
            row.setServerLanguage(form.getServerLanguage());
            row.setServerSource(form.getServerSource());
            row.setNote(form.getNote());
            row.setPublishedBy(actor.getId());
            row.setPublishedByName(actor.getName());
            row.setPublishedAt(now);
            context.getDb().insertable(row).executeRows(true);
            drafts.delete(DraftEntity.FORM, form.getKey());
            return row;
        });
    }

    /** 表单的后端代码（Java）编不过、或关掉了在线后端代码时不能发布：问题带行列，标在编辑器里（字段 serverSource） */
    private static void checkBackendCode(FormPublication form, boolean allowed) {
        if (!"java".equals(form.getServerLanguage()) || form.getServerSource() == null || form.getServerSource().isBlank()) return;
        if (!allowed) {
            throw new FlowException(FlowErrorCode.VALIDATION_FAILED, "form backend code is disabled",
                List.of(new ErrorDetail("server-code-disabled", "serverSource", null, null, null, Map.of())));
        }
        FormCompiler.Result result = new FormCompiler().compile(form.getServerSource());
        if (result.isOk()) return;
        throw new FlowException(FlowErrorCode.VALIDATION_FAILED, "the form backend code does not compile",
            result.getProblems().stream()
                .map(p -> new ErrorDetail("compile-error", "serverSource", null, null, null,
                    Map.of("line", String.valueOf(p.getLine()), "column", String.valueOf(p.getColumn()), "message", p.getMessage())))
                .toList());
    }

    /** 表单本身：同 {@link #nextProcessVersion} */
    private int nextFormVersion(Actor actor, FormPublication form, Instant now) {
        FormEntity existing = context.getDb().queryable(FormEntity.class).where(f -> f.formKey().eq(form.getKey())).singleOrNull();
        if (existing == null) {
            FormEntity created = new FormEntity();
            created.setFormKey(form.getKey());
            created.setName(form.getName());
            created.setCategory(form.getCategory());
            created.setStatus(FormEntity.ACTIVE);
            created.setLatestVersion(1);
            created.setRev(0);
            created.setCreatedAt(now);
            created.setUpdatedBy(actor.getId());
            context.getDb().insertable(created).executeRows();
            return 1;
        }
        int current = existing.getLatestVersion();
        context.getDb().updatable(FormEntity.class)
            .setColumns(f -> {
                f.latestVersion().set(current + 1);
                f.name().set(form.getName());
                f.category().set(form.getCategory());
                f.updatedBy().set(actor.getId());
                f.rev().increment();
            })
            .where(f -> { f.formKey().eq(form.getKey()); f.latestVersion().eq(current); })
            .executeRows(1, "form " + form.getKey() + " was published by someone else at the same time");
        return current + 1;
    }

    /** 读定义的 JSON；形状不对（缺键、类型不对、JSON 写错）时报 {@code flow.definition-invalid}，消息里带出错的位置 */
    private static ProcessDefinition parseDefinition(String json) {
        try {
            return Definitions.parse(json);
        } catch (DefinitionSchema.Newer e) {
            throw new FlowException(FlowErrorCode.DEFINITION_INVALID, e.getMessage(), List.of(new ErrorDetail("schema-newer", null, null, null, null,
                Map.of("schema", String.valueOf(e.getVersion())))));
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

    /** 定义引用的表单：写了版本取那个版本，没写取最新的；没有时报 {@code flow.not-found} */
    private FormVersionEntity formOf(ProcessDefinition.FormRef ref) {
        FormVersionEntity form = findForm(ref);
        if (form == null) throw FlowErrors.notFound("form", ref.getVersion() == null ? ref.getKey() : ref.getKey() + "@" + ref.getVersion());
        return form;
    }

    /** 同上，没有时为 null（只读编号、标识、版本号与字段清单） */
    private FormVersionEntity findForm(ProcessDefinition.FormRef ref) {
        return context.getDb().queryable(FormVersionEntity.class)
            .where(f -> { f.formKey().eq(ref.getKey()); if (ref.getVersion() != null) f.version().eq(ref.getVersion()); })
            .orderBy(f -> f.version().desc())
            .select(f -> f.FETCHER.id().formKey().version().manifest())
            .firstOrNull();
    }

    private static List<Problem> errorsOnly(List<Problem> problems) {
        return problems.stream().filter(p -> p.getSeverity() == Problem.Severity.ERROR).toList();
    }

    /**
     * 发布时的校验上下文：给出表单的字段清单（查 {@code form.} 引用）与注册了的扩展（{@link Extensions}）。
     */
    @Data
    private static class PublishContext implements ValidationContext {
        private final FormManifest form;
        private final Extensions extensions;

        @Override
        public Boolean isRegistered(ExtensionKind kind, String name) {
            return switch (kind) {
                case RESOLVER -> extensions.hasResolver(name);
                case ACTION -> extensions.hasAction(name);
                case GUARD -> extensions.hasGuard(name);
                case NODE -> extensions.hasNode(name);
                case COMPLETION -> extensions.hasCountersignRule(name);
                case COMPUTED -> extensions.hasComputed(name);
            };
        }
    }
}
