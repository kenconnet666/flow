package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.command.FormPublication;
import org.dpdns.zerodep.flow.core.entity.FormEntity;
import org.dpdns.zerodep.flow.core.entity.FormVersionEntity;
import org.dpdns.zerodep.flow.core.entity.ProcessEntity;
import org.dpdns.zerodep.flow.core.entity.ProcessVersionEntity;
import org.dpdns.zerodep.flow.core.extension.Extensions;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.view.DefinitionProblem;
import org.dpdns.zerodep.flow.core.view.FormSummary;
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
public final class Publishing {
    private final EngineContext context;

    public Publishing(EngineContext context) { this.context = context; }

    /**
     * 发布一个流程定义（0005 的 JSON 文字）。引用的表单没写版本时定在当前最新的版本上，之后表单再发布也不影响这个流程版本。
     * 校验有错误时报 {@code flow.definition-invalid}，警告不拦。
     */
    public ProcessVersionEntity publishDefinition(Actor actor, String json, String note) {
        ProcessDefinition model = parseDefinition(json);
        DefinitionProblem disabled = disabledForm(model);
        if (disabled != null) {
            throw new FlowException(FlowErrorCode.DEFINITION_INVALID, "the form " + model.form().key() + " is disabled",
                List.of(new ErrorDetail(disabled.code(), disabled.field(), null, null, null, disabled.params())));
        }
        FormVersionEntity form = model.form() == null ? null : formOf(model.form());
        FormManifest manifest = form == null ? null : FormManifest.parse(Json.read(form.getManifest()));
        List<Problem> errors = errorsOnly(ProcessValidator.validate(model, new PublishContext(manifest, context.extensions())));
        if (!errors.isEmpty()) {
            throw new FlowException(FlowErrorCode.DEFINITION_INVALID, "the definition has " + errors.size() + " error(s)",
                errors.stream().map(ErrorDetail::of).toList());
        }
        return context.transaction(() -> {
            Instant now = context.clock().instant();
            int version = nextProcessVersion(actor, model, now);
            ProcessVersionEntity row = new ProcessVersionEntity();
            row.setProcessKey(model.key());
            row.setVersion(version);
            row.setModel(json);
            row.setFormKey(form == null ? null : form.getFormKey());
            row.setFormVersion(form == null ? null : form.getVersion());
            row.setNote(note);
            row.setPublishedBy(actor.id());
            row.setPublishedByName(actor.name());
            row.setPublishedAt(now);
            context.db().insertable(row).executeRows(true);
            return row;
        });
    }

    /** 流程本身：没有时建上（版本 1），有时最新版本号加一并换上名称、分类；返回新的版本号 */
    private int nextProcessVersion(Actor actor, ProcessDefinition model, Instant now) {
        ProcessEntity process = context.db().queryable(ProcessEntity.class).where(p -> p.processKey().eq(model.key())).singleOrNull();
        if (process == null) {
            ProcessEntity created = new ProcessEntity();
            created.setProcessKey(model.key());
            created.setName(model.name());
            created.setCategory(model.category());
            created.setStatus(ProcessEntity.ACTIVE);
            created.setLatestVersion(1);
            created.setRev(0);
            created.setCreatedAt(now);
            created.setUpdatedBy(actor.id());
            context.db().insertable(created).executeRows();
            return 1;
        }
        int current = process.getLatestVersion();
        context.db().updatable(ProcessEntity.class)
            .setColumns(p -> {
                p.latestVersion().set(current + 1);
                p.name().set(model.name());
                p.category().set(model.category());
                p.updatedBy().set(actor.id());
                p.rev().increment();
            })
            .where(p -> { p.processKey().eq(model.key()); p.latestVersion().eq(current); })
            .executeRows(1, "process " + model.key() + " was published by someone else at the same time");
        return current + 1;
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
            FormVersionEntity form = findForm(model.form());
            if (form == null) {
                return List.of(new DefinitionProblem("form-unknown", "error", null, null, null, "form", Map.of("key", model.form().key())));
            }
            manifest = FormManifest.parse(Json.read(form.getManifest()));
        }
        List<DefinitionProblem> problems = new ArrayList<>();
        DefinitionProblem disabled = disabledForm(model);
        if (disabled != null) problems.add(disabled);
        ProcessValidator.validate(model, new PublishContext(manifest, context.extensions())).stream()
            .map(p -> new DefinitionProblem(p.code(), p.severity().name().toLowerCase(Locale.ROOT), p.node(), p.edge(), p.lane(),
                p.field(), p.params()))
            .forEach(problems::add);
        return problems;
    }

    /** 引用的表单停用了（0004 的 5.3：停用后新发布的流程不能再选它）：{@code form-disabled}；没停用、没引用表单时为 null */
    private DefinitionProblem disabledForm(ProcessDefinition model) {
        if (model.form() == null) return null;
        boolean off = context.db().queryable(FormEntity.class)
            .where(f -> { f.formKey().eq(model.form().key()); f.status().eq(FormEntity.DISABLED); })
            .any();
        return off ? new DefinitionProblem("form-disabled", "error", null, null, null, "form", Map.of("key", model.form().key())) : null;
    }

    /** 停用、启用流程：停用后不能再发起，进行中的照常走完 */
    public ProcessSummary setStatus(String processKey, boolean active) {
        String status = active ? ProcessEntity.ACTIVE : ProcessEntity.DISABLED;
        long changed = context.db().updatable(ProcessEntity.class)
            .setColumns(p -> { p.status().set(status); p.rev().increment(); })
            .where(p -> p.processKey().eq(processKey))
            .executeRows();
        if (changed == 0) throw FlowErrors.notFound("process", processKey);
        ProcessEntity p = context.db().queryable(ProcessEntity.class).where(r -> r.processKey().eq(processKey)).singleNotNull();
        return new ProcessSummary(p.getProcessKey(), p.getName(), p.getCategory(), p.getStatus(), p.getLatestVersion());
    }

    /** 停用、启用表单：停用后新发布的流程不能再选它，已经引用它的流程照常用 */
    public FormSummary setFormStatus(String formKey, boolean active) {
        String status = active ? FormEntity.ACTIVE : FormEntity.DISABLED;
        long changed = context.db().updatable(FormEntity.class)
            .setColumns(f -> { f.status().set(status); f.rev().increment(); })
            .where(f -> f.formKey().eq(formKey))
            .executeRows();
        if (changed == 0) throw FlowErrors.notFound("form", formKey);
        FormEntity f = context.db().queryable(FormEntity.class).where(r -> r.formKey().eq(formKey)).singleNotNull();
        return new FormSummary(f.getFormKey(), f.getName(), f.getCategory(), f.getStatus(), f.getLatestVersion());
    }

    /** 发布一张表单：先校验字段清单本身（0004 的 1.2），有错误时报 {@code flow.validation-failed} */
    public FormVersionEntity publishForm(Actor actor, FormPublication form) {
        FormManifest manifest = parseManifest(form.manifest());
        List<Problem> errors = errorsOnly(ManifestValidator.validate(manifest, ManifestValidator.BUILTIN_TYPES::contains));
        if (!errors.isEmpty()) {
            throw new FlowException(FlowErrorCode.VALIDATION_FAILED, "the form manifest has " + errors.size() + " error(s)",
                errors.stream().map(ErrorDetail::of).toList());
        }
        return context.transaction(() -> {
            Instant now = context.clock().instant();
            FormVersionEntity row = new FormVersionEntity();
            row.setFormKey(form.key());
            row.setVersion(nextFormVersion(actor, form, now));
            row.setManifest(form.manifest());
            row.setClientSource(form.clientSource());
            row.setClientCompiled(form.clientCompiled());
            row.setCompilerVersion(form.compilerVersion());
            row.setServerLanguage(form.serverLanguage());
            row.setServerSource(form.serverSource());
            row.setNote(form.note());
            row.setPublishedBy(actor.id());
            row.setPublishedByName(actor.name());
            row.setPublishedAt(now);
            context.db().insertable(row).executeRows(true);
            return row;
        });
    }

    /** 表单本身：同 {@link #nextProcessVersion} */
    private int nextFormVersion(Actor actor, FormPublication form, Instant now) {
        FormEntity existing = context.db().queryable(FormEntity.class).where(f -> f.formKey().eq(form.key())).singleOrNull();
        if (existing == null) {
            FormEntity created = new FormEntity();
            created.setFormKey(form.key());
            created.setName(form.name());
            created.setCategory(form.category());
            created.setStatus(FormEntity.ACTIVE);
            created.setLatestVersion(1);
            created.setRev(0);
            created.setCreatedAt(now);
            created.setUpdatedBy(actor.id());
            context.db().insertable(created).executeRows();
            return 1;
        }
        int current = existing.getLatestVersion();
        context.db().updatable(FormEntity.class)
            .setColumns(f -> {
                f.latestVersion().set(current + 1);
                f.name().set(form.name());
                f.category().set(form.category());
                f.updatedBy().set(actor.id());
                f.rev().increment();
            })
            .where(f -> { f.formKey().eq(form.key()); f.latestVersion().eq(current); })
            .executeRows(1, "form " + form.key() + " was published by someone else at the same time");
        return current + 1;
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

    /** 定义引用的表单：写了版本取那个版本，没写取最新的；没有时报 {@code flow.not-found} */
    private FormVersionEntity formOf(ProcessDefinition.FormRef ref) {
        FormVersionEntity form = findForm(ref);
        if (form == null) throw FlowErrors.notFound("form", ref.version() == null ? ref.key() : ref.key() + "@" + ref.version());
        return form;
    }

    /** 同上，没有时为 null（只读编号、标识、版本号与字段清单） */
    private FormVersionEntity findForm(ProcessDefinition.FormRef ref) {
        return context.db().queryable(FormVersionEntity.class)
            .where(f -> { f.formKey().eq(ref.key()); if (ref.version() != null) f.version().eq(ref.version()); })
            .orderBy(f -> f.version().desc())
            .select(f -> f.FETCHER.id().formKey().version().manifest())
            .firstOrNull();
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
