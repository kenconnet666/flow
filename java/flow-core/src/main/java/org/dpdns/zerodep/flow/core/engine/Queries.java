package org.dpdns.zerodep.flow.core.engine;

import com.easy.query.api.proxy.entity.select.EntityQueryable;
import com.easy.query.core.annotation.Column;
import com.easy.query.core.func.def.enums.OrderByModeEnum;
import lombok.Data;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.db.JsonColumns;
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
import org.dpdns.zerodep.flow.core.view.CcVO;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 查询（0006 第 4 节）：发起列表与发起页、收件箱与待办详情、实例详情、已办、我发起的、抄送、管理端的列表。
 * 列表直接按 VO 读（{@code select(WorkItemVO.class)}），行上要带实例的标题、摘要列时再按编号一次读回（{@link InstanceBrief}）。
 */
public final class Queries {
    private final EngineContext context;
    private final DefinitionCache definitions;

    public Queries(EngineContext context) {
        this.context = context;
        this.definitions = context.definitions();
    }

    /**
     * 管理端搜实例的条件：不写的不限。
     *
     * @param status     状态
     * @param processKey 流程
     * @param keyword    标题里有这几个字
     * @param startedBy  发起人
     */
    public record InstanceSearch(String status, String processKey, String keyword, Long startedBy) { }

    /** 列表行上要带的实例信息（按编号一次读回）；definitionId 用来找处理结果的名字 */
    @Data
    public static class InstanceBrief {
        private Long id;
        private Long definitionId;
        private String title;
        @Column(conversion = JsonColumns.ObjectJson.class)
        private Map<String, Object> summary;
        private String status;
    }

    /** 我能发起的流程：启用着、发起人名单里有我（名单只认写死的值），按分类、名称排 */
    public List<StartableProcess> startable(Actor actor) {
        List<StartableProcess> out = new ArrayList<>();
        for (ProcessEntity process : processEntities()) {
            if (!ProcessEntity.ACTIVE.equals(process.getStatus())) continue;
            if (!CandidateRules.isStarter(actor, latest(process).model().starters())) continue;
            out.add(new StartableProcess(process.getProcessKey(), process.getName(), process.getCategory(), process.getLatestVersion()));
        }
        return out;
    }

    /** 发起页：流程与开始节点上的表单（没写权限的字段默认能改）；流程停用、我不能发起时报 {@code flow.forbidden} */
    public StartView startView(Actor actor, String key) {
        ProcessEntity process = process(key);
        if (!ProcessEntity.ACTIVE.equals(process.getStatus())) throw FlowErrors.forbidden("process", key, "disabled");
        LoadedDefinition definition = latest(process);
        if (!CandidateRules.isStarter(actor, definition.model().starters())) throw FlowErrors.forbidden("process", key, "not-a-starter");
        Function<String, Object> read = name -> "sys.initiator".equals(name) ? actor.id() : null;
        FormView form = definition.hasForm() ? formView(definition, definition.graph().start().form(), true, FormValues.empty(), read) : null;
        List<PickView> picks = new PickPreview(definition.graph(), context).after(definition.graph().start().id(), null, read);
        return new StartView(new StartableProcess(key, process.getName(), process.getCategory(), process.getLatestVersion()), form, picks);
    }

    /**
     * 我的待办：落到我的，加上我、我的角色、部门或扩展种类（{@link Actor#groups()}）能认领的（我没被排除），按到达时间从新到旧；
     * 每行带上实例的标题与摘要列（0006 第 4 节"待办的业务摘要列"）。
     * <p>分两段查（0007 第 4 节），各取前"偏移 + 页长"条再合并：落到我的走（租户, 处理人, 状态, 到达时间）的索引；
     * 我能认领的（没有处理人、候选里有我所在的、我没被排除）从候选的覆盖索引进。第二段要求没有处理人，两段不会重叠。
     */
    public Page<TodoItem> todo(Actor actor, int page, int size) {
        if (page < 1 || size < 1) throw new IllegalArgumentException("page and size start at 1");
        Map<String, Set<Long>> memberships = new LinkedHashMap<>(actor.groups());
        memberships.put(CandidateEntity.USER, Set.of(actor.id()));
        memberships.put(CandidateEntity.ROLE, actor.roles());
        memberships.put(CandidateEntity.DEPARTMENT, actor.departments());
        Long me = actor.id();
        long mine = assignedTo(me).count();
        long claimable = claimableBy(me, memberships).count();
        int head = page * size;
        List<WorkItemVO> rows = new ArrayList<>();
        rows.addAll(assignedTo(me).orderBy(w -> { w.createdAt().desc(); w.id().desc(); }).limit(head).select(WorkItemVO.class).toList());
        rows.addAll(claimableBy(me, memberships).orderBy(w -> { w.createdAt().desc(); w.id().desc(); }).limit(head).select(WorkItemVO.class).toList());
        rows.sort(Comparator.comparing(WorkItemVO::getCreatedAt).thenComparing(WorkItemVO::getId).reversed());
        List<WorkItemVO> slice = rows.stream().skip((long) (page - 1) * size).limit(size).toList();
        return withInstances(Page.of(slice, mine + claimable, page, size));
    }

    /** 落到我的、打开着的 */
    private EntityQueryable<WorkItemEntityProxy, WorkItemEntity> assignedTo(Long me) {
        return context.db().queryable(WorkItemEntity.class).where(w -> { w.assigneeId().eq(me); w.state().eq(WorkItemEntity.OPEN); });
    }

    /** 收件箱里我能认领的：候选里有我、我的角色、部门或扩展种类，而且我没被排除 */
    private EntityQueryable<WorkItemEntityProxy, WorkItemEntity> claimableBy(Long me, Map<String, Set<Long>> memberships) {
        return context.db().queryable(WorkItemEntity.class).where(w -> {
            w.assigneeId().isNull();
            w.state().eq(WorkItemEntity.OPEN);
            w.candidates().any(c -> c.or(() -> memberships.forEach((kind, ids) -> {
                if (!ids.isEmpty()) c.and(() -> { c.kind().eq(kind); c.targetId().in(ids); });
            })));
            w.candidates().none(c -> { c.kind().eq(CandidateEntity.EXCLUDED); c.targetId().eq(me); });
        });
    }

    /** 我处理过的：按处理时间从新到旧，每行带实例的标题与摘要列（同收件箱的行） */
    public Page<TodoItem> done(Actor actor, int page, int size) {
        return withInstances(Page.of(context.db().queryable(WorkItemEntity.class)
            .where(w -> { w.completedBy().eq(actor.id()); w.state().eq(WorkItemEntity.DONE); })
            .orderBy(w -> { w.completedAt().desc(); w.id().desc(); })
            .select(WorkItemVO.class).toPageResult(page, size), page, size));
    }

    /** 等我指定承办人的待办（无人认领超时、我是收件箱负责人），每行带实例的标题与摘要列 */
    public Page<TodoItem> designations(Actor actor, int page, int size) {
        return withInstances(Page.of(context.db().queryable(WorkItemEntity.class)
            .where(w -> { w.designatorId().eq(actor.id()); w.needsDesignation().eq(true); w.state().eq(WorkItemEntity.OPEN); })
            .orderBy(w -> { w.createdAt().desc(); w.id().desc(); })
            .select(WorkItemVO.class).toPageResult(page, size), page, size));
    }

    /** 待办列表的每一行带上实例的标题、摘要列与状态（0006 第 4 节"待办的业务摘要列"），处理过的再带上结果的名字 */
    private Page<TodoItem> withInstances(Page<WorkItemVO> items) {
        Map<Long, InstanceBrief> instances = briefs(items.rows().stream().map(WorkItemVO::getInstanceId).toList());
        return items.map(item -> {
            InstanceBrief instance = instances.get(item.getInstanceId());
            if (instance == null) return new TodoItem(item, null, Map.of(), null, null);
            return new TodoItem(item, instance.getTitle(), instance.getSummary(), instance.getStatus(), outcomeLabel(instance, item));
        });
    }

    /** 一批实例的标题、摘要列与状态，按编号 */
    private Map<Long, InstanceBrief> briefs(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return context.db().queryable(InstanceEntity.class).where(i -> i.id().in(ids)).select(InstanceBrief.class).toList().stream()
            .collect(Collectors.toMap(InstanceBrief::getId, Function.identity()));
    }

    /** 处理结果的名字：按实例的定义版本找节点上的 outcome；还没处理、隐式的结果（加签办完）时为 null */
    private String outcomeLabel(InstanceBrief instance, WorkItemVO item) {
        if (item.getOutcome() == null) return null;
        if (!(definitions.byId(instance.getDefinitionId()).graph().node(item.getNode()) instanceof ProcessNode.Task task)) return null;
        return task.outcomes().stream().filter(o -> o.key().equals(item.getOutcome())).map(Outcome::label).findFirst().orElse(null);
    }

    /** 抄送我的：未读在前，已读的按读的时间从新到旧，同样的按抄送时间从新到旧；每行带实例的标题、摘要列与状态 */
    public Page<CcItem> ccList(Actor actor, int page, int size) {
        Page<CcVO> ccs = Page.of(context.db().queryable(CcEntity.class)
            .where(c -> c.recipientId().eq(actor.id()))
            .orderBy(c -> { c.readAt().desc(true, OrderByModeEnum.NULLS_FIRST); c.createdAt().desc(); c.id().desc(); })
            .select(CcVO.class).toPageResult(page, size), page, size);
        Map<Long, InstanceBrief> instances = briefs(ccs.rows().stream().map(CcVO::getInstanceId).toList());
        return ccs.map(cc -> {
            InstanceBrief instance = instances.get(cc.getInstanceId());
            return instance == null ? new CcItem(cc, null, Map.of(), null) : new CcItem(cc, instance.getTitle(), instance.getSummary(), instance.getStatus());
        });
    }

    /** 管理端搜实例：按发起时间从新到旧；有状态、流程时走（租户, 状态, 流程, 发起时间）的索引 */
    public Page<InstanceVO> instances(InstanceSearch search, int page, int size) {
        return Page.of(context.db().queryable(InstanceEntity.class)
            .where(i -> {
                if (search.status() != null && !search.status().isBlank()) i.status().eq(search.status());
                if (search.processKey() != null && !search.processKey().isBlank()) i.processKey().eq(search.processKey());
                if (search.keyword() != null && !search.keyword().isBlank()) i.title().like(search.keyword().trim());
                if (search.startedBy() != null) i.startedBy().eq(search.startedBy());
            })
            .orderBy(i -> { i.startedAt().desc(); i.id().desc(); })
            .select(InstanceVO.class).toPageResult(page, size), page, size);
    }

    /** 我发起的：按发起时间从新到旧 */
    public Page<InstanceVO> started(Actor actor, int page, int size) {
        return Page.of(context.db().queryable(InstanceEntity.class)
            .where(i -> i.startedBy().eq(actor.id()))
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
        return context.db().queryable(ProcessVersionEntity.class).where(v -> v.processKey().eq(processKey)).orderBy(v -> v.version().desc())
            .select(ProcessVersionVO.class).toList();
    }

    /** 管理端的表单列表：停用的也在，按分类、名称排 */
    public List<FormSummary> forms() {
        return context.db().queryable(FormEntity.class).orderBy(f -> { f.category().asc(); f.name().asc(); }).toList().stream()
            .map(f -> new FormSummary(f.getFormKey(), f.getName(), f.getCategory(), f.getStatus(), f.getLatestVersion())).toList();
    }

    /** 表单的全部版本，新的在前 */
    public List<FormVersionVO> formVersions(String formKey) {
        form(formKey);
        return context.db().queryable(FormVersionEntity.class).where(v -> v.formKey().eq(formKey)).orderBy(v -> v.version().desc())
            .select(FormVersionVO.class).toList();
    }

    /** 表单某个版本的全部内容（编辑时载入，不含编译结果）；version 为 null 时取最新的 */
    public FormDocument formDocument(String formKey, Integer version) {
        FormEntity form = form(formKey);
        int wanted = version == null ? form.getLatestVersion() : version;
        FormVersionEntity source = context.db().queryable(FormVersionEntity.class)
            .where(v -> { v.formKey().eq(formKey); v.version().eq(wanted); })
            .select(v -> v.FETCHER.version().manifest().clientSource().serverLanguage().serverSource())
            .singleNotNull(() -> FlowErrors.notFound("form", formKey + "@" + wanted));
        return new FormDocument(form.getFormKey(), form.getName(), form.getCategory(), source.getVersion(), source.getManifest(),
            source.getClientSource(), source.getServerLanguage(), source.getServerSource());
    }

    /** 某个版本的定义（0005 的 JSON 文字，发布时的原样）；version 为 null 时取最新的 */
    public String definitionJson(String processKey, Integer version) {
        return context.db().queryable(ProcessVersionEntity.class)
            .where(v -> { v.processKey().eq(processKey); if (version != null) v.version().eq(version); })
            .orderBy(v -> v.version().desc())
            .selectColumn(v -> v.model())
            .firstNotNull(() -> FlowErrors.notFound("definition", processKey + (version == null ? "" : "@" + version)));
    }

    /** 异常：重试用完的作业，新的在前；instanceId 不为 null 时只看这个实例的 */
    public Page<IncidentView> incidents(Long instanceId, int page, int size) {
        Page<JobEntity> jobs = Page.of(context.db().queryable(JobEntity.class)
            .where(j -> { j.state().eq(JobEntity.FAILED); if (instanceId != null) j.instanceId().eq(instanceId); })
            .orderBy(j -> { j.dueAt().desc(); j.id().desc(); })
            .toPageResult(page, size), page, size);
        Map<Long, InstanceEntity> instances = jobs.rows().isEmpty() ? Map.of()
            : context.db().queryable(InstanceEntity.class).where(i -> i.id().in(jobs.rows().stream().map(JobEntity::getInstanceId).toList()))
                .toList().stream().collect(Collectors.toMap(InstanceEntity::getId, Function.identity()));
        return jobs.map(job -> incident(job, instances.get(job.getInstanceId())));
    }

    /** 一个异常：按实例的定义版本找节点的名称、跳过时能选的结果 */
    private IncidentView incident(JobEntity job, InstanceEntity instance) {
        String node = BuiltinJobs.nodeOf(job);
        if (node == null && job.getWorkItemId() != null) {
            node = context.db().queryable(WorkItemEntity.class).whereById(job.getWorkItemId()).selectColumn(w -> w.node()).singleOrNull();
        }
        if (instance == null) {
            return new IncidentView(job.getId(), job.getKind(), job.getInstanceId(), null, null, node, null, job.getAttempts(), job.getLastError(),
                job.getDueAt(), false, List.of());
        }
        ProcessGraph graph = definitions.of(instance).graph();
        ProcessNode target = graph.node(node);
        boolean skippable = BuiltinJobs.ACTION.equals(job.getKind()) && instance.getEndedAt() == null
            && BuiltinJobs.stillWaiting(job, instance.getState());
        return new IncidentView(job.getId(), job.getKind(), instance.getId(), instance.getTitle(), instance.getProcessKey(), node,
            target == null ? null : target.name(), job.getAttempts(), job.getLastError(), job.getDueAt(), skippable,
            node == null ? List.of() : AdminHandler.skipOutcomes(graph, node));
    }

    /**
     * 待办详情：待办与实例、这个节点上的表单（按节点权限去掉隐藏的字段）、能选的处理结果，以及我现在能不能认领、处理。
     * 待办已处理、实例已结束时照样能看，只是两样都不能做。
     */
    public WorkItemView workItem(Actor actor, Long workItemId) {
        WorkItemVO item = WorkItems.find(context.db(), workItemId);
        InstanceEntity instance = Instances.find(context.db(), item.getInstanceId());
        LoadedDefinition definition = definitions.of(instance);
        ProcessNode.Task task = (ProcessNode.Task) definition.graph().node(item.getNode());

        boolean open = WorkItemEntity.OPEN.equals(item.getState()) && InstanceEntity.ACTIVE.equals(instance.getStatus());
        boolean mine = actor.id().equals(item.getAssigneeId());
        boolean candidate = item.getAssigneeId() == null && CandidateRules.isCandidate(actor, WorkItems.candidates(context.db(), item.getId()));

        FormValues stored = Instances.form(context.db(), instance.getId());
        Function<String, Object> read = name -> References.read(name, instance, stored);
        FormView form = definition.hasForm() ? formView(definition, task.form(), false, stored, read) : null;
        boolean completable = open && (mine || candidate);
        PickPreview preview = new PickPreview(definition.graph(), context);
        List<WorkItemView.ReturnTarget> targets = completable && task.outcomes().stream().anyMatch(Outcome::choosesTarget)
            ? returnTargets(definition, instance, item.getNode()) : List.of();
        // 选节点的退回在没有能退回的节点时（还在第一个人工节点上）不给：点了也做不成
        List<OutcomeView> outcomes = task.outcomes().stream()
            .filter(o -> !(completable && o.choosesTarget() && targets.isEmpty()))
            .map(o -> outcomeView(o, completable ? preview.after(task.id(), o.key(), read) : List.of(), o.choosesTarget() ? targets : List.of()))
            .toList();
        boolean assign = open && context.db().queryable(WorkItemEntity.class)
            .where(w -> { w.id().eq(item.getId()); w.designatorId().eq(actor.id()); w.needsDesignation().eq(true); })
            .any();
        boolean withdrawStep = new StepWithdrawal(context).allowed(actor, item, definition.graph(), instance);
        WorkItemView.Actions actions = completable
            ? new WorkItemView.Actions(task.allow().transfer(), task.allow().delegate(), task.allow().addSign(),
                mine && WorkItemEntity.CLAIMED.equals(item.getAssignment()), assign, withdrawStep)
            : new WorkItemView.Actions(false, false, false, false, assign, withdrawStep);
        return new WorkItemView(item, Instances.view(context.db(), instance.getId()), form, outcomes, open && candidate, completable, actions);
    }

    /**
     * 实例详情：实例、表单、全部待办、流转记录，以及我能不能撤回、催办。
     * 表单都只读，按流程级的 {@code formView} 去掉隐藏的字段（0004 的 4.3、0005 的 2.1）：在待办里看的人走待办详情。
     */
    public InstanceDetail instance(Actor actor, Long instanceId) {
        InstanceEntity instance = Instances.find(context.db(), instanceId);
        LoadedDefinition definition = definitions.of(instance);
        FormValues stored = Instances.form(context.db(), instanceId);
        FormView form = definition.hasForm()
            ? formView(definition, new NodeFormAccess(FieldAccess.READONLY, definition.model().settings().formView()), false, stored,
                name -> References.read(name, instance, stored))
            : null;
        List<WorkItemVO> items = WorkItems.ofInstance(context.db(), instanceId);
        boolean starterOfActive = actor.id().equals(instance.getStartedBy()) && InstanceEntity.ACTIVE.equals(instance.getStatus());
        InstanceDetail.Actions actions = new InstanceDetail.Actions(
            starterOfActive && WithdrawHandler.allowed(definition, items),
            starterOfActive && items.stream().anyMatch(w -> WorkItemEntity.OPEN.equals(w.getState())));
        List<InstanceDetail.NodeRef> nodes = definition.model().nodes().stream()
            .filter(n -> !(n instanceof ProcessNode.Start) && !(n instanceof ProcessNode.Gateway))
            .map(n -> new InstanceDetail.NodeRef(n.id(), n.name(), n.type())).toList();
        List<FlowEventVO> timeline = context.db().queryable(EventEntity.class).where(e -> e.instanceId().eq(instanceId))
            .orderBy(e -> { e.instanceRev().asc(); e.id().asc(); })
            .select(FlowEventVO.class).toList();
        InstanceVO view = Instances.view(context.db(), instanceId);
        ProcessDiagram diagram = DiagramView.of(definition.model(), view, timeline);
        return new InstanceDetail(view, form, items, timeline, actions, nodes, diagram);
    }

    /** 全部流程，按分类、名称排 */
    private List<ProcessEntity> processEntities() {
        return context.db().queryable(ProcessEntity.class).orderBy(p -> { p.category().asc(); p.name().asc(); }).toList();
    }

    /** 流程本身；没有时报 {@code flow.not-found} */
    private ProcessEntity process(String key) {
        return context.db().queryable(ProcessEntity.class).where(p -> p.processKey().eq(key)).singleNotNull(() -> FlowErrors.notFound("process", key));
    }

    /** 表单本身；没有时报 {@code flow.not-found} */
    private FormEntity form(String key) {
        return context.db().queryable(FormEntity.class).where(f -> f.formKey().eq(key)).singleNotNull(() -> FlowErrors.notFound("form", key));
    }

    /** 流程最新发布的版本（解析好的） */
    private LoadedDefinition latest(ProcessEntity process) {
        return definitions.of(context.db().queryable(ProcessVersionEntity.class)
            .where(v -> { v.processKey().eq(process.getProcessKey()); v.version().eq(process.getLatestVersion()); })
            .singleNotNull(() -> FlowErrors.notFound("process", process.getProcessKey())));
    }

    /**
     * 表单在一个节点上的样子：清单、前端代码、节点上的权限，值里去掉节点上隐藏的字段。
     * 只按节点权限去掉（{@code hiddenBy = ACCESS}）：显示条件不成立的字段由前端按实时的值判断。
     */
    private FormView formView(LoadedDefinition definition, NodeFormAccess access, boolean isStart, FormValues values,
                              Function<String, Object> read) {
        Map<String, FieldState> states = FormAccess.fieldStates(definition.manifest(), access, isStart, read);
        Map<String, Object> visible = new LinkedHashMap<>();
        values.asMap().forEach((name, value) -> {
            FieldState state = states.get(name);
            if (state == null || state.hiddenBy() != FormAccess.HiddenBy.ACCESS) visible.put(name, value);
        });
        FormVersionEntity version = definition.form();
        // 源码与编译结果可能很大，只在要渲染表单时读
        FormVersionEntity client = context.db().queryable(FormVersionEntity.class).whereById(version.getId())
            .select(v -> v.FETCHER.clientSource().clientCompiled().compilerVersion())
            .singleNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> manifest = (Map<String, Object>) Wire.decode(Json.read(version.getManifest()));
        return new FormView(version.getId(), version.getFormKey(), version.getVersion(), manifest,
            client.getClientSource(), client.getClientCompiled(), client.getCompilerVersion(), FormView.accessJson(access), isStart, visible);
    }

    /** 处理结果给前端的样子：没写意见要求时是 {@code none}（不收意见，0005 的人工节点） */
    private static OutcomeView outcomeView(Outcome outcome, List<PickView> picks, List<WorkItemView.ReturnTarget> targets) {
        return new OutcomeView(outcome.key(), outcome.label(), outcome.kindOrDefault(), outcome.comment() == null ? "none" : outcome.comment(),
            outcome.confirm(), picks, targets);
    }

    /** 退回时能选的节点，带上次在那里处理的人（取这个节点最近处理完的待办） */
    private List<WorkItemView.ReturnTarget> returnTargets(LoadedDefinition definition, InstanceEntity instance, String current) {
        List<WorkItemVO> items = WorkItems.ofInstance(context.db(), instance.getId());
        return ReturnTargets.of(definition, instance.getState(), current).stream().map(node -> {
            String handler = items.stream().filter(i -> i.getNode().equals(node.id()) && i.getCompletedAt() != null)
                .max(Comparator.comparing(WorkItemVO::getCompletedAt)).map(WorkItemVO::getCompletedByName).orElse(null);
            return new WorkItemView.ReturnTarget(node.id(), node.name(), handler);
        }).toList();
    }
}
