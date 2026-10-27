package org.dpdns.zerodep.flow.core.engine;

import com.easy.query.api.proxy.entity.select.EntityQueryable;
import com.easy.query.core.func.def.enums.OrderByModeEnum;
import lombok.Data;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.WorkItemDraft;
import org.dpdns.zerodep.flow.core.entity.CandidateEntity;
import org.dpdns.zerodep.flow.core.entity.CcEntity;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.entity.FormEntity;
import org.dpdns.zerodep.flow.core.entity.FormVersionEntity;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.entity.JobEntity;
import org.dpdns.zerodep.flow.core.entity.ProcessEntity;
import org.dpdns.zerodep.flow.core.entity.ProcessVersionEntity;
import org.dpdns.zerodep.flow.core.entity.WorkItemEntity;
import org.dpdns.zerodep.flow.core.entity.proxy.WorkItemEntityProxy;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.view.CcItem;
import org.dpdns.zerodep.flow.core.view.DraftVO;
import org.dpdns.zerodep.flow.core.view.FlowEventVO;
import org.dpdns.zerodep.flow.core.view.FormDocument;
import org.dpdns.zerodep.flow.core.view.FormSummary;
import org.dpdns.zerodep.flow.core.view.FormVersionVO;
import org.dpdns.zerodep.flow.core.view.FormView;
import org.dpdns.zerodep.flow.core.view.IncidentView;
import org.dpdns.zerodep.flow.core.view.InstanceDetail;
import org.dpdns.zerodep.flow.core.view.InstanceVO;
import org.dpdns.zerodep.flow.core.view.Page;
import org.dpdns.zerodep.flow.core.view.PickView;
import org.dpdns.zerodep.flow.core.view.ProcessDiagram;
import org.dpdns.zerodep.flow.core.view.ProcessSummary;
import org.dpdns.zerodep.flow.core.view.ProcessVersionVO;
import org.dpdns.zerodep.flow.core.view.StartView;
import org.dpdns.zerodep.flow.core.view.StartableProcess;
import org.dpdns.zerodep.flow.core.view.TodoItem;
import org.dpdns.zerodep.flow.core.view.WorkItemVO;
import org.dpdns.zerodep.flow.core.view.WorkItemView.OutcomeView;
import org.dpdns.zerodep.flow.core.view.WorkItemView;
import org.dpdns.zerodep.flow.model.definition.FieldAccess;
import org.dpdns.zerodep.flow.model.definition.NodeFormAccess;
import org.dpdns.zerodep.flow.model.definition.Outcome;
import org.dpdns.zerodep.flow.model.definition.ProcessGraph;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.form.FormAccess.FieldState;
import org.dpdns.zerodep.flow.model.form.FormAccess;
import org.dpdns.zerodep.wire.Json;
import org.dpdns.zerodep.wire.Wire;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 查询（0006 第 4 节）：发起列表与发起页、收件箱与待办详情、实例详情、已办、我发起的、抄送、管理端的列表。
 * 列表按 VO 读（{@code select(InstanceVO.class)}）；行上要带实例的（待办、抄送）按对象树读：
 * {@code selectAutoInclude(TodoItem.class)} 把导航 {@code instance} 一起读出。
 */
public class Queries {
    private final EngineContext context;
    private final DefinitionCache definitions;

    public Queries(EngineContext context) {
        this.context = context;
        this.definitions = context.getDefinitions();
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
        FormView form = definition.hasForm() ? formView(definition, definition.getGraph().start().getForm(), true, FormValues.empty(), read) : null;
        List<PickView> picks = new PickPreview(definition.getGraph(), context).after(definition.getGraph().start().getId(), null, read);
        return new StartView(new StartableProcess(key, process.getName(), process.getCategory(), process.getLatestVersion()), form, picks);
    }

    /**
     * 我的待办：落到我的，加上我、我的角色、部门或扩展种类（{@link Actor#groups()}）能认领的（我没被排除），按到达时间从新到旧；
     * 每行带上实例（标题、摘要列与状态，0006 第 4 节"待办的业务摘要列"）。
     * <p>分两段查（0007 第 4 节），各取前"偏移 + 页长"条再合并：落到我的走（租户, 处理人, 状态, 到达时间）的索引；
     * 我能认领的（没有处理人、候选里有我所在的、我没被排除）从候选的覆盖索引进。第二段要求没有处理人，两段不会重叠。
     */
    public Page<TodoItem> todo(Actor actor, int page, int size) {
        if (page < 1 || size < 1) throw new IllegalArgumentException("page and size start at 1");
        Map<String, Set<Long>> memberships = new LinkedHashMap<>(actor.getGroups());
        memberships.put(CandidateEntity.USER, Set.of(actor.getId()));
        memberships.put(CandidateEntity.ROLE, actor.getRoles());
        memberships.put(CandidateEntity.DEPARTMENT, actor.getDepartments());
        Long me = actor.getId();
        long mine = assignedTo(me).count();
        long claimable = claimableBy(me, memberships).count();
        int head = page * size;
        List<TodoItem> rows = new ArrayList<>();
        rows.addAll(assignedTo(me).orderBy(w -> { w.createdAt().desc(); w.id().desc(); }).limit(head).selectAutoInclude(TodoItem.class).toList());
        rows.addAll(claimableBy(me, memberships).orderBy(w -> { w.createdAt().desc(); w.id().desc(); }).limit(head).selectAutoInclude(TodoItem.class).toList());
        rows.sort(Comparator.comparing(TodoItem::getCreatedAt).thenComparing(TodoItem::getId).reversed());
        return Page.of(rows.stream().skip((long) (page - 1) * size).limit(size).toList(), mine + claimable, page, size);
    }

    /** 落到我的、打开着的 */
    private EntityQueryable<WorkItemEntityProxy, WorkItemEntity> assignedTo(Long me) {
        return context.getDb().queryable(WorkItemEntity.class).where(w -> { w.assigneeId().eq(me); w.state().eq(WorkItemEntity.OPEN); });
    }

    /** 收件箱里我能认领的：候选里有我、我的角色、部门或扩展种类，而且我没被排除 */
    private EntityQueryable<WorkItemEntityProxy, WorkItemEntity> claimableBy(Long me, Map<String, Set<Long>> memberships) {
        return context.getDb().queryable(WorkItemEntity.class).where(w -> {
            w.assigneeId().isNull();
            w.state().eq(WorkItemEntity.OPEN);
            w.candidates().any(c -> c.or(() -> memberships.forEach((kind, ids) -> {
                if (!ids.isEmpty()) c.and(() -> { c.kind().eq(kind); c.targetId().in(ids); });
            })));
            w.candidates().none(c -> { c.kind().eq(CandidateEntity.EXCLUDED); c.targetId().eq(me); });
        });
    }

    /** 我处理过的：按处理时间从新到旧，每行带实例与处理结果的名字 */
    public Page<TodoItem> done(Actor actor, int page, int size) {
        Page<TodoItem> rows = Page.of(context.getDb().queryable(WorkItemEntity.class)
            .where(w -> { w.completedBy().eq(actor.getId()); w.state().eq(WorkItemEntity.DONE); })
            .orderBy(w -> { w.completedAt().desc(); w.id().desc(); })
            .selectAutoInclude(TodoItem.class).toPageResult(page, size), page, size);
        rows.getRows().forEach(this::labelOutcome);
        return rows;
    }

    /** 等我指定承办人的待办（无人认领超时、我是收件箱负责人），每行带实例 */
    public Page<TodoItem> designations(Actor actor, int page, int size) {
        return Page.of(context.getDb().queryable(WorkItemEntity.class)
            .where(w -> { w.designatorId().eq(actor.getId()); w.needsDesignation().eq(true); w.state().eq(WorkItemEntity.OPEN); })
            .orderBy(w -> { w.createdAt().desc(); w.id().desc(); })
            .selectAutoInclude(TodoItem.class).toPageResult(page, size), page, size);
    }

    /** 处理过的行带上结果的名字：按实例的定义版本找节点上的 outcome；隐式的结果（加签办完）没有名字 */
    private void labelOutcome(TodoItem item) {
        if (item.getOutcome() == null || item.getInstance() == null) return;
        if (!(definitions.byId(item.getInstance().getDefinitionId()).getGraph().node(item.getNode()) instanceof ProcessNode.Task task)) return;
        task.getOutcomes().stream().filter(o -> o.getKey().equals(item.getOutcome())).findFirst().ifPresent(o -> item.setOutcomeLabel(o.getLabel()));
    }

    /** 抄送我的：未读在前，已读的按读的时间从新到旧，同样的按抄送时间从新到旧；每行带实例 */
    public Page<CcItem> ccList(Actor actor, int page, int size) {
        return Page.of(context.getDb().queryable(CcEntity.class)
            .where(c -> c.recipientId().eq(actor.getId()))
            .orderBy(c -> { c.readAt().desc(true, OrderByModeEnum.NULLS_FIRST); c.createdAt().desc(); c.id().desc(); })
            .selectAutoInclude(CcItem.class).toPageResult(page, size), page, size);
    }

    /** 管理端搜实例：按发起时间从新到旧；有状态、流程时走（租户, 状态, 流程, 发起时间）的索引 */
    public Page<InstanceVO> instances(InstanceSearch search, int page, int size) {
        return Page.of(context.getDb().queryable(InstanceEntity.class)
            .where(i -> {
                if (search.getStatus() != null && !search.getStatus().isBlank()) i.status().eq(search.getStatus());
                if (search.getProcessKey() != null && !search.getProcessKey().isBlank()) i.processKey().eq(search.getProcessKey());
                if (search.getKeyword() != null && !search.getKeyword().isBlank()) i.title().like(search.getKeyword().trim());
                if (search.getStartedBy() != null) i.startedBy().eq(search.getStartedBy());
            })
            .orderBy(i -> { i.startedAt().desc(); i.id().desc(); })
            .select(InstanceVO.class).toPageResult(page, size), page, size);
    }

    /** 我发起的：按发起时间从新到旧 */
    public Page<InstanceVO> started(Actor actor, int page, int size) {
        return Page.of(context.getDb().queryable(InstanceEntity.class)
            .where(i -> i.startedBy().eq(actor.getId()))
            .orderBy(i -> { i.startedAt().desc(); i.id().desc(); })
            .select(InstanceVO.class).toPageResult(page, size), page, size);
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

    /** 管理端的表单列表：停用的也在，按分类、名称排 */
    public List<FormSummary> forms() {
        return context.getDb().queryable(FormEntity.class).orderBy(f -> { f.category().asc(); f.name().asc(); }).toList().stream()
            .map(f -> new FormSummary(f.getFormKey(), f.getName(), f.getCategory(), f.getStatus(), f.getLatestVersion())).toList();
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

    /** 某个版本的定义（0005 的 JSON 文字，发布时的原样）；version 为 null 时取最新的 */
    public String definitionJson(String processKey, Integer version) {
        return context.getDb().queryable(ProcessVersionEntity.class)
            .where(v -> { v.processKey().eq(processKey); if (version != null) v.version().eq(version); })
            .orderBy(v -> v.version().desc())
            .selectColumn(v -> v.model())
            .firstNotNull(() -> FlowErrors.notFound("definition", processKey + (version == null ? "" : "@" + version)));
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

    /**
     * 待办详情：待办与实例、这个节点上的表单（按节点权限去掉隐藏的字段）、能选的处理结果，以及我现在能不能认领、处理。
     * 待办已处理、实例已结束时照样能看，只是两样都不能做。
     */
    public WorkItemView workItem(Actor actor, Long workItemId) {
        WorkItemVO item = WorkItems.find(context.getDb(), workItemId);
        InstanceEntity instance = Instances.find(context.getDb(), item.getInstanceId());
        LoadedDefinition definition = definitions.of(instance);
        ProcessNode.Task task = (ProcessNode.Task) definition.getGraph().node(item.getNode());

        boolean open = WorkItemEntity.OPEN.equals(item.getState()) && InstanceEntity.ACTIVE.equals(instance.getStatus());
        boolean mine = actor.getId().equals(item.getAssigneeId());
        boolean candidate = item.getAssigneeId() == null && CandidateRules.isCandidate(actor, WorkItems.candidates(context.getDb(), item.getId()));

        FormValues stored = Instances.form(context.getDb(), instance.getId());
        Function<String, Object> read = name -> References.read(name, instance, stored);
        // 我暂存过的接着填：表单显示暂存的值
        WorkItemDraft draft = open && mine
            ? context.getDb().queryable(WorkItemEntity.class).whereById(item.getId()).selectColumn(w -> w.draft()).singleOrNull()
            : null;
        FormValues shown = draft == null ? stored : stored.with(draft.getForm());
        FormView form = definition.hasForm() ? formView(definition, task.getForm(), false, shown, read) : null;
        boolean completable = open && (mine || candidate);
        PickPreview preview = new PickPreview(definition.getGraph(), context);
        List<WorkItemView.ReturnTarget> targets = completable && task.getOutcomes().stream().anyMatch(Outcome::choosesTarget)
            ? returnTargets(definition, instance, item.getNode()) : List.of();
        // 选节点的退回在没有能退回的节点时（还在第一个人工节点上）不给：点了也做不成
        List<OutcomeView> outcomes = task.getOutcomes().stream()
            .filter(o -> !(completable && o.choosesTarget() && targets.isEmpty()))
            .map(o -> outcomeView(o, completable ? preview.after(task.getId(), o.getKey(), read) : List.of(), o.choosesTarget() ? targets : List.of()))
            .toList();
        boolean assign = open && context.getDb().queryable(WorkItemEntity.class)
            .where(w -> { w.id().eq(item.getId()); w.designatorId().eq(actor.getId()); w.needsDesignation().eq(true); })
            .any();
        boolean withdrawStep = new StepWithdrawal(context).allowed(actor, item, definition.getGraph(), instance);
        WorkItemView.Actions actions = completable
            ? new WorkItemView.Actions(task.getAllow().isTransfer(), task.getAllow().isDelegate(), task.getAllow().isAddSign(),
                mine && WorkItemEntity.CLAIMED.equals(item.getAssignment()), assign, withdrawStep)
            : new WorkItemView.Actions(false, false, false, false, assign, withdrawStep);
        return new WorkItemView(item, Instances.view(context.getDb(), instance.getId()), form, outcomes, open && candidate, completable, actions,
            draft == null ? null : new DraftVO(draft.getComment(), draft.getSavedAt()));
    }

    /**
     * 实例详情：实例、表单、全部待办、流转记录，以及我能不能撤回、催办。
     * 表单都只读，按流程级的 {@code formView} 去掉隐藏的字段（0004 的 4.3、0005 的 2.1）：在待办里看的人走待办详情。
     */
    public InstanceDetail instance(Actor actor, Long instanceId) {
        InstanceEntity instance = Instances.find(context.getDb(), instanceId);
        LoadedDefinition definition = definitions.of(instance);
        FormValues stored = Instances.form(context.getDb(), instanceId);
        FormView form = definition.hasForm()
            ? formView(definition, new NodeFormAccess(FieldAccess.READONLY, definition.model().getSettings().getFormView()), false, stored,
                name -> References.read(name, instance, stored))
            : null;
        List<WorkItemVO> items = WorkItems.ofInstance(context.getDb(), instanceId);
        boolean starterOfActive = actor.getId().equals(instance.getStartedBy()) && InstanceEntity.ACTIVE.equals(instance.getStatus());
        InstanceDetail.Actions actions = new InstanceDetail.Actions(
            starterOfActive && WithdrawHandler.allowed(definition, items),
            starterOfActive && items.stream().anyMatch(w -> WorkItemEntity.OPEN.equals(w.getState())));
        List<InstanceDetail.NodeRef> nodes = definition.model().getNodes().stream()
            .filter(n -> !(n instanceof ProcessNode.Start) && !(n instanceof ProcessNode.Gateway))
            .map(n -> new InstanceDetail.NodeRef(n.getId(), n.getName(), n.getType())).toList();
        List<FlowEventVO> timeline = context.getDb().queryable(EventEntity.class).where(e -> e.instanceId().eq(instanceId))
            .orderBy(e -> { e.instanceRev().asc(); e.id().asc(); })
            .select(FlowEventVO.class).toList();
        InstanceVO view = Instances.view(context.getDb(), instanceId);
        ProcessDiagram diagram = DiagramView.of(definition.model(), view, timeline);
        return new InstanceDetail(view, form, items, timeline, actions, nodes, diagram);
    }

    /** 全部流程，按分类、名称排 */
    private List<ProcessEntity> processEntities() {
        return context.getDb().queryable(ProcessEntity.class).orderBy(p -> { p.category().asc(); p.name().asc(); }).toList();
    }

    /** 流程本身；没有时报 {@code flow.not-found} */
    private ProcessEntity process(String key) {
        return context.getDb().queryable(ProcessEntity.class).where(p -> p.processKey().eq(key)).singleNotNull(() -> FlowErrors.notFound("process", key));
    }

    /** 表单本身；没有时报 {@code flow.not-found} */
    private FormEntity form(String key) {
        return context.getDb().queryable(FormEntity.class).where(f -> f.formKey().eq(key)).singleNotNull(() -> FlowErrors.notFound("form", key));
    }


    /**
     * 表单在一个节点上的样子：清单、前端代码、节点上的权限，值里去掉节点上隐藏的字段。
     * 只按节点权限去掉（{@code hiddenBy = ACCESS}）：显示条件不成立的字段由前端按实时的值判断。
     */
    private FormView formView(LoadedDefinition definition, NodeFormAccess access, boolean isStart, FormValues values,
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

    /** 处理结果给前端的样子：没写意见要求时是 {@code none}（不收意见，0005 的人工节点） */
    private static OutcomeView outcomeView(Outcome outcome, List<PickView> picks, List<WorkItemView.ReturnTarget> targets) {
        return new OutcomeView(outcome.getKey(), outcome.getLabel(), outcome.kindOrDefault(), outcome.getComment() == null ? "none" : outcome.getComment(),
            outcome.isConfirm(), picks, targets);
    }

    /** 退回时能选的节点，带上次在那里处理的人（取这个节点最近处理完的待办） */
    private List<WorkItemView.ReturnTarget> returnTargets(LoadedDefinition definition, InstanceEntity instance, String current) {
        List<WorkItemVO> items = WorkItems.ofInstance(context.getDb(), instance.getId());
        return ReturnTargets.of(definition, instance.getState(), current).stream().map(node -> {
            String handler = items.stream().filter(i -> i.getNode().equals(node.getId()) && i.getCompletedAt() != null)
                .max(Comparator.comparing(WorkItemVO::getCompletedAt)).map(WorkItemVO::getCompletedByName).orElse(null);
            return new WorkItemView.ReturnTarget(node.getId(), node.getName(), handler);
        }).toList();
    }
}
