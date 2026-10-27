package org.dpdns.zerodep.flow.core.engine.query;

import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.engine.DefinitionCache;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.FlowErrors;
import org.dpdns.zerodep.flow.core.engine.LoadedDefinition;
import org.dpdns.zerodep.flow.core.engine.assignee.CandidateRules;
import org.dpdns.zerodep.flow.core.engine.assignee.PickPreview;
import org.dpdns.zerodep.flow.core.entity.ProcessEntity;
import org.dpdns.zerodep.flow.core.entity.ProcessVersionEntity;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.view.definition.ProcessSummary;
import org.dpdns.zerodep.flow.core.view.definition.ProcessVersionVO;
import org.dpdns.zerodep.flow.core.view.definition.StartView;
import org.dpdns.zerodep.flow.core.view.definition.StartableProcess;
import org.dpdns.zerodep.flow.core.view.form.FormView;
import org.dpdns.zerodep.flow.core.view.workitem.PickView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** 流程的查询：我能发起的、发起页、管理端的流程列表与版本、某个版本的定义原文。 */
public class ProcessQueries {
    private final EngineContext context;
    private final DefinitionCache definitions;
    private final FormQueries forms;

    public ProcessQueries(EngineContext context) {
        this.context = context;
        this.definitions = context.getDefinitions();
        this.forms = new FormQueries(context);
    }

    /** 我能发起的流程：启用着、发起人名单里有我（名单只认写死的值），按分类、名称排 */
    public List<StartableProcess> startable(Actor actor) {
        List<StartableProcess> out = new ArrayList<>();
        List<ProcessEntity> active = definitions.processes().where(p -> p.status().eq(ProcessEntity.ACTIVE))
            .orderBy(p -> { p.category().asc(); p.name().asc(); }).toList();
        for (ProcessEntity process : active) {
            if (!CandidateRules.isStarter(actor, definitions.latest(process).model().getStarters())) continue;
            out.add(new StartableProcess(process.getProcessKey(), process.getName(), process.getCategory(), process.getLatestVersion()));
        }
        return out;
    }

    /** 发起页：流程与开始节点上的表单（没写权限的字段默认能改）；流程停用、我不能发起时报 {@code flow.forbidden} */
    public StartView startView(Actor actor, String key) {
        ProcessEntity process = definitions.processes().where(p -> p.processKey().eq(key))
            .singleNotNull(() -> FlowErrors.notFound("process", key));
        if (!ProcessEntity.ACTIVE.equals(process.getStatus())) throw FlowErrors.forbidden("process", key, "disabled");
        LoadedDefinition definition = definitions.latest(process);
        if (!CandidateRules.isStarter(actor, definition.model().getStarters())) throw FlowErrors.forbidden("process", key, "not-a-starter");
        Function<String, Object> read = name -> "sys.initiator".equals(name) ? actor.getId() : null;
        FormView form = definition.hasForm() ? forms.formView(definition, definition.getGraph().start().getForm(), true, FormValues.empty(), read) : null;
        List<PickView> picks = new PickPreview(definition.getGraph(), context).after(definition.getGraph().start().getId(), null, read);
        return new StartView(new StartableProcess(key, process.getName(), process.getCategory(), process.getLatestVersion()), form, picks);
    }

    /** 管理端的流程列表：全部流程（停用的也在），按分类、名称排 */
    public List<ProcessSummary> processes() {
        return processEntities().stream()
            .map(p -> new ProcessSummary(p.getProcessKey(), p.getName(), p.getCategory(), p.getStatus(), p.getLatestVersion())).toList();
    }

    /** 流程的全部版本，新的在前 */
    public List<ProcessVersionVO> versions(String processKey) {
        process(processKey);
        return context.getDb().queryable(ProcessVersionEntity.class).where(v -> v.processKey().eq(processKey)).orderBy(v -> v.version().desc())
            .select(ProcessVersionVO.class).toList();
    }

    /** 某个版本的定义（0005 的 JSON 文字，发布时的原样）；version 为 null 时取最新的 */
    public String definitionJson(String processKey, Integer version) {
        return context.getDb().queryable(ProcessVersionEntity.class)
            .where(v -> { v.processKey().eq(processKey); if (version != null) v.version().eq(version); })
            .orderBy(v -> v.version().desc())
            .selectColumn(v -> v.model())
            .firstNotNull(() -> FlowErrors.notFound("definition", processKey + (version == null ? "" : "@" + version)));
    }

    /** 全部流程，按分类、名称排 */
    private List<ProcessEntity> processEntities() {
        return context.getDb().queryable(ProcessEntity.class).orderBy(p -> { p.category().asc(); p.name().asc(); }).toList();
    }

    /** 流程本身；没有时报 {@code flow.not-found} */
    private ProcessEntity process(String key) {
        return context.getDb().queryable(ProcessEntity.class).where(p -> p.processKey().eq(key)).singleNotNull(() -> FlowErrors.notFound("process", key));
    }
}
