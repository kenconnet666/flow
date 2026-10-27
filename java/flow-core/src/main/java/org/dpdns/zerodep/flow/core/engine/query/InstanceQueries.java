package org.dpdns.zerodep.flow.core.engine.query;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import lombok.Data;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.Variables;
import org.dpdns.zerodep.flow.core.engine.DefinitionCache;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.FlowErrors;
import org.dpdns.zerodep.flow.core.engine.LoadedDefinition;
import org.dpdns.zerodep.flow.core.engine.form.References;
import org.dpdns.zerodep.flow.core.engine.handler.AdminHandler;
import org.dpdns.zerodep.flow.core.engine.handler.WithdrawHandler;
import org.dpdns.zerodep.flow.core.engine.job.BuiltinJobs;
import org.dpdns.zerodep.flow.core.engine.store.Archives;
import org.dpdns.zerodep.flow.core.engine.store.Instances;
import org.dpdns.zerodep.flow.core.engine.store.WorkItems;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.entity.FormDataEntity;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.entity.JobEntity;
import org.dpdns.zerodep.flow.core.entity.WorkItemEntity;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.view.Page;
import org.dpdns.zerodep.flow.core.view.form.FormView;
import org.dpdns.zerodep.flow.core.view.instance.FlowEventVO;
import org.dpdns.zerodep.flow.core.view.instance.IncidentView;
import org.dpdns.zerodep.flow.core.view.instance.InstanceDetail;
import org.dpdns.zerodep.flow.core.view.instance.InstanceVO;
import org.dpdns.zerodep.flow.core.view.instance.ProcessDiagram;
import org.dpdns.zerodep.flow.core.view.instance.ProgressView;
import org.dpdns.zerodep.flow.core.view.instance.VariableVO;
import org.dpdns.zerodep.flow.core.view.workitem.WorkItemVO;
import org.dpdns.zerodep.flow.model.definition.FieldAccess;
import org.dpdns.zerodep.flow.model.definition.NodeFormAccess;
import org.dpdns.zerodep.flow.model.definition.ProcessGraph;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;

import java.util.List;
import java.util.Set;

/** 实例的查询：管理端搜实例、我发起的、实例详情、异常（重试用完的作业）。 */
public class InstanceQueries {
    private final EngineContext context;
    private final DefinitionCache definitions;
    private final FormQueries forms;

    public InstanceQueries(EngineContext context) {
        this.context = context;
        this.definitions = context.getDefinitions();
        this.forms = new FormQueries(context);
    }

    /** 管理端搜实例的条件：不写的不限。 */
    @Data
    public static class InstanceSearch {
        /** 状态 */
        private final String status;
        /** 流程 */
        private final String processKey;
        /** 标题里有这几个字 */
        private final String keyword;
        /** 发起人 */
        private final Long startedBy;
        /** 排序：能按 {@link #SEARCH_SORTS} 里的列排 */
        private final SortOrder sort;
    }

    /** 管理端搜实例能排的列 */
    public static final Set<String> SEARCH_SORTS = Set.of("startedAt", "endedAt", "title", "status", "processKey", "priority");
    /** 我发起的能排的列 */
    public static final Set<String> STARTED_SORTS = Set.of("startedAt", "endedAt", "title", "status");

    /** 管理端搜实例：按发起时间从新到旧；有状态、流程时走（租户, 状态, 流程, 发起时间）的索引 */
    public Page<InstanceVO> instances(InstanceSearch search, int page, int size) {
        return Page.of(context.getDb().queryable(InstanceEntity.class)
            .where(i -> {
                if (search.getStatus() != null && !search.getStatus().isBlank()) i.status().eq(search.getStatus());
                else i.status().ne(InstanceEntity.DRAFT);
                if (search.getProcessKey() != null && !search.getProcessKey().isBlank()) i.processKey().eq(search.getProcessKey());
                if (search.getKeyword() != null && !search.getKeyword().isBlank()) i.title().like(search.getKeyword().trim());
                if (search.getStartedBy() != null) i.startedBy().eq(search.getStartedBy());
            })
            .orderByObject(!search.getSort().isEmpty(), search.getSort().allow(SEARCH_SORTS))
            .orderBy(i -> { i.startedAt().desc(); i.id().desc(); })
            .select(InstanceVO.class).toPageResult(page, size), page, size);
    }

    /** 外部参与方看的进度：现在在哪个环节、谁在处理、预计什么时候答复 */
    public ProgressView progress(Long instanceId) {
        InstanceEntity instance = Instances.find(context.getDb(), instanceId);
        ProgressView view = new ProgressView();
        view.setTitle(instance.getTitle());
        view.setProcessKey(instance.getProcessKey());
        view.setStatus(instance.getStatus());
        view.setStartedAt(instance.getStartedAt());
        view.setEndedAt(instance.getEndedAt());
        view.setSteps(WorkItems.ofInstance(context.getDb(), instanceId).stream()
            .filter(w -> WorkItemEntity.OPEN.equals(w.getState()))
            .map(w -> {
                ProgressView.Step step = new ProgressView.Step();
                step.setNodeName(w.getNodeName());
                step.setHandlerName(w.getAssigneeName());
                step.setArrivedAt(w.getCreatedAt());
                step.setDueAt(w.getDueAt());
                return step;
            }).toList());
        return view;
    }

    /** 我发起的：按发起时间从新到旧 */
    /** 我发起前存的草稿，最后存的在前（"我发起的"里不含它们） */
    public List<InstanceVO> startDrafts(Actor actor) {
        return context.getDb().queryable(InstanceEntity.class)
            .where(i -> { i.startedBy().eq(actor.getId()); i.status().eq(InstanceEntity.DRAFT); })
            .orderBy(i -> { i.startedAt().desc(); i.id().desc(); })
            .select(InstanceVO.class).toList();
    }

    public Page<InstanceVO> started(Actor actor, ListFilter filter, int page, int size) {
        return Page.of(Archives.from(context.getDb(), InstanceEntity.class, filter.isArchived())
            .where(i -> {
                i.startedBy().eq(actor.getId());
                i.status().ne(InstanceEntity.DRAFT);
                if (filter.hasProcess()) i.processKey().eq(filter.getProcessKey());
                if (filter.getFrom() != null) i.startedAt().ge(filter.getFrom());
                if (filter.getTo() != null) i.startedAt().lt(filter.getTo());
            })
            .orderByObject(!filter.getSort().isEmpty(), filter.getSort().allow(STARTED_SORTS))
            .orderBy(i -> { i.startedAt().desc(); i.id().desc(); })
            .select(InstanceVO.class).toPageResult(page, size), page, size);
    }

    /**
     * 实例详情：实例、表单、全部待办、流转记录，以及我能不能撤回、催办。
     * 表单都只读，按流程级的 {@code formView} 去掉隐藏的字段（0004 的 4.3、0005 的 2.1）：在待办里看的人走待办详情。
     * 流程变量是引擎推进用的，只给流程管理员看。
     */
    public InstanceDetail instance(Actor actor, Long instanceId) {
        EasyEntityQuery db = context.getDb();
        // 热表里没有时看归档表：结束得早、已经归档的实例照样能打开（同样的样子，什么都不能再做）
        InstanceEntity hot = db.queryable(InstanceEntity.class).whereById(instanceId).singleOrNull();
        boolean archived = hot == null;
        InstanceEntity instance = archived
            ? Archives.query(db, InstanceEntity.class).whereById(instanceId).singleNotNull(() -> FlowErrors.notFound("instance", instanceId))
            : hot;
        LoadedDefinition definition = definitions.of(instance);
        FormDataEntity data = Archives.from(db, FormDataEntity.class, archived).whereById(instanceId).singleOrNull();
        FormValues stored = data == null ? FormValues.empty() : data.getValues();
        FormView form = definition.hasForm()
            ? forms.formView(definition, new NodeFormAccess(FieldAccess.READONLY, definition.model().getSettings().getFormView()), false, stored,
                name -> References.read(name, instance, stored, context.getExtensions()))
            : null;
        List<WorkItemVO> items = Archives.from(db, WorkItemEntity.class, archived).where(w -> w.instanceId().eq(instanceId))
            .orderBy(w -> { w.createdAt().asc(); w.id().asc(); })
            .select(WorkItemVO.class).toList();
        boolean starterOfActive = actor.getId().equals(instance.getStartedBy()) && InstanceEntity.ACTIVE.equals(instance.getStatus());
        InstanceDetail.Actions actions = new InstanceDetail.Actions(
            starterOfActive && WithdrawHandler.allowed(definition, items),
            starterOfActive && items.stream().anyMatch(w -> WorkItemEntity.OPEN.equals(w.getState())));
        List<InstanceDetail.NodeRef> nodes = definition.model().getNodes().stream()
            .filter(n -> !(n instanceof ProcessNode.Start) && !(n instanceof ProcessNode.Gateway))
            .map(n -> new InstanceDetail.NodeRef(n.getId(), n.getName(), n.getType())).toList();
        List<FlowEventVO> timeline = Archives.from(db, EventEntity.class, archived).where(e -> e.instanceId().eq(instanceId))
            .orderBy(e -> { e.instanceRev().asc(); e.id().asc(); })
            .select(FlowEventVO.class).toList();
        InstanceVO view = Archives.from(db, InstanceEntity.class, archived).whereById(instanceId).select(InstanceVO.class).singleNotNull();
        ProcessDiagram diagram = DiagramView.of(definition.model(), view, timeline, context.getExtensions());
        return new InstanceDetail(view, form, items, timeline, actions, nodes, diagram, variablesFor(actor, definition, instance));
    }

    /** 流程管理员看到的流程变量；别人是 null */
    private List<VariableVO> variablesFor(Actor actor, LoadedDefinition definition, InstanceEntity instance) {
        if (!context.getOrg().administrators().contains(actor.getId())) return null;
        Variables values = instance.getVariables() == null ? Variables.empty() : instance.getVariables();
        return definition.model().getVariables().stream()
            .map(v -> new VariableVO(v.getName(), v.getLabel(), v.getType(), values.get(v.getName())))
            .toList();
    }

    /** 异常：重试用完的作业，新的在前；instanceId 不为 null 时只看这个实例的 */
    public Page<IncidentView> incidents(Long instanceId, int page, int size) {
        return Page.of(context.getDb().queryable(JobEntity.class)
            .include(j -> j.instance())
            .include(j -> j.workItem())
            .where(j -> { j.state().eq(JobEntity.FAILED); if (instanceId != null) j.instanceId().eq(instanceId); })
            .orderBy(j -> { j.dueAt().desc(); j.id().desc(); })
            .toPageResult(page, size), page, size).map(this::incident);
    }

    /** 一个异常（作业连同导航 instance、workItem 读出）：按实例的定义版本找节点的名称、跳过时能选的结果 */
    private IncidentView incident(JobEntity job) {
        InstanceEntity instance = job.getInstance();
        String node = BuiltinJobs.nodeOf(job);
        if (node == null && job.getWorkItem() != null) node = job.getWorkItem().getNode();
        if (instance == null) {
            return new IncidentView(job.getId(), job.getKind(), job.getInstanceId(), null, null, node, null, job.getAttempts(), job.getLastError(),
                job.getDueAt(), false, List.of());
        }
        ProcessGraph graph = definitions.of(instance).getGraph();
        ProcessNode target = graph.node(node);
        boolean skippable = BuiltinJobs.ACTION.equals(job.getKind()) && instance.getEndedAt() == null
            && BuiltinJobs.stillWaiting(job, instance.getState());
        return new IncidentView(job.getId(), job.getKind(), instance.getId(), instance.getTitle(), instance.getProcessKey(), node,
            target == null ? null : target.getName(), job.getAttempts(), job.getLastError(), job.getDueAt(), skippable,
            node == null ? List.of() : AdminHandler.skipOutcomes(graph, node));
    }
}
