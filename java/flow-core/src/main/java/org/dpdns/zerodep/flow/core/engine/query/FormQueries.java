package org.dpdns.zerodep.flow.core.engine.query;

import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.engine.DefinitionCache;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.FlowErrors;
import org.dpdns.zerodep.flow.core.engine.LoadedDefinition;
import org.dpdns.zerodep.flow.core.entity.FormEntity;
import org.dpdns.zerodep.flow.core.entity.FormVersionEntity;
import org.dpdns.zerodep.flow.core.entity.ProcessEntity;
import org.dpdns.zerodep.flow.core.view.form.FormDocument;
import org.dpdns.zerodep.flow.core.view.form.FormSummary;
import org.dpdns.zerodep.flow.core.view.form.FormUsage;
import org.dpdns.zerodep.flow.core.view.form.FormVersionVO;
import org.dpdns.zerodep.flow.core.view.form.FormView;
import org.dpdns.zerodep.flow.model.definition.NodeFormAccess;
import org.dpdns.zerodep.flow.model.form.FormAccess.FieldState;
import org.dpdns.zerodep.flow.model.form.FormAccess;
import org.dpdns.zerodep.wire.Json;
import org.dpdns.zerodep.wire.Wire;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** 表单的查询：管理端的表单列表、版本、某个版本的全部内容；以及表单在一个节点上的样子（发起页、待办详情、实例详情共用）。 */
public class FormQueries {
    private final EngineContext context;
    private final DefinitionCache definitions;

    public FormQueries(EngineContext context) {
        this.context = context;
        this.definitions = context.getDefinitions();
    }

    /** 管理端的表单列表：停用的也在，按分类、名称排 */
    public List<FormSummary> forms() {
        return context.getDb().queryable(FormEntity.class).orderBy(f -> { f.category().asc(); f.name().asc(); }).toList().stream()
            .map(f -> new FormSummary(f.getFormKey(), f.getName(), f.getCategory(), f.getStatus(), f.getLatestVersion())).toList();
    }

    /** 最新发布的版本引用了这张表单的流程，按名称排 */
    public List<FormUsage> usages(String formKey) {
        form(formKey);
        return context.getDb().queryable(ProcessEntity.class).where(p -> p.latest().formKey().eq(formKey)).orderBy(p -> p.name().asc())
            .toList().stream().map(p -> new FormUsage(p.getProcessKey(), p.getName(), p.getLatestVersion())).toList();
    }

    /** 表单的全部版本，新的在前 */
    public List<FormVersionVO> formVersions(String formKey) {
        form(formKey);
        return context.getDb().queryable(FormVersionEntity.class).where(v -> v.formKey().eq(formKey)).orderBy(v -> v.version().desc())
            .select(FormVersionVO.class).toList();
    }

    /** 表单某个版本的全部内容（编辑时载入，不含编译结果）；version 为 null 时取最新的 */
    public FormDocument formDocument(String formKey, Integer version) {
        FormEntity form = form(formKey);
        int wanted = version == null ? form.getLatestVersion() : version;
        FormVersionEntity source = context.getDb().queryable(FormVersionEntity.class)
            .where(v -> { v.formKey().eq(formKey); v.version().eq(wanted); })
            .select(v -> v.FETCHER.version().manifest().clientSource().serverLanguage().serverSource())
            .singleNotNull(() -> FlowErrors.notFound("form", formKey + "@" + wanted));
        return new FormDocument(form.getFormKey(), form.getName(), form.getCategory(), source.getVersion(), source.getManifest(),
            source.getClientSource(), source.getServerLanguage(), source.getServerSource());
    }

    /** 表单本身；没有时报 {@code flow.not-found} */
    private FormEntity form(String key) {
        return context.getDb().queryable(FormEntity.class).where(f -> f.formKey().eq(key)).singleNotNull(() -> FlowErrors.notFound("form", key));
    }

    /**
     * 表单在一个节点上的样子：清单、前端代码、节点上的权限，值里去掉节点上隐藏的字段。
     * 只按节点权限去掉（{@code hiddenBy = ACCESS}）：显示条件不成立的字段由前端按实时的值判断。
     */
    public FormView formView(LoadedDefinition definition, NodeFormAccess access, boolean isStart, FormValues values,
                              Function<String, Object> read) {
        Map<String, FieldState> states = FormAccess.fieldStates(definition.getManifest(), access, isStart, read);
        Map<String, Object> visible = new LinkedHashMap<>();
        values.asMap().forEach((name, value) -> {
            FieldState state = states.get(name);
            if (state == null || state.getHiddenBy() != FormAccess.HiddenBy.ACCESS) visible.put(name, value);
        });
        FormVersionEntity version = definition.form();
        // 源码与编译结果可能很大，只在要渲染表单时读
        FormVersionEntity client = context.getDb().queryable(FormVersionEntity.class).whereById(version.getId())
            .select(v -> v.FETCHER.clientSource().clientCompiled().compilerVersion())
            .singleNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> manifest = (Map<String, Object>) Wire.decode(Json.read(version.getManifest()));
        return new FormView(version.getId(), version.getFormKey(), version.getVersion(), manifest,
            client.getClientSource(), client.getClientCompiled(), client.getCompilerVersion(), FormView.accessJson(access), isStart, visible);
    }
}
