package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.JsonNumbers;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.store.DefinitionStore.StoredProcess;
import org.dpdns.zerodep.flow.core.store.FormDataStore.StoredFormData;
import org.dpdns.zerodep.flow.core.store.FormStore.FormClient;
import org.dpdns.zerodep.flow.core.store.InstanceStore.InstanceRecord;
import org.dpdns.zerodep.flow.core.store.InstanceStore.InstanceSearch;
import org.dpdns.zerodep.flow.core.store.JobStore.Job;
import org.dpdns.zerodep.flow.core.store.WorkItemStore.Candidate;
import org.dpdns.zerodep.flow.core.store.WorkItemStore.Member;
import org.dpdns.zerodep.flow.core.view.FormView;
import org.dpdns.zerodep.flow.core.view.InstanceDetail;
import org.dpdns.zerodep.flow.core.view.PickView;
import org.dpdns.zerodep.flow.core.view.ProcessSummary;
import org.dpdns.zerodep.flow.core.view.StartView;
import org.dpdns.zerodep.flow.core.view.StartableProcess;
import org.dpdns.zerodep.flow.core.view.WorkItemView.OutcomeView;
import org.dpdns.zerodep.flow.core.view.WorkItemView;
import org.dpdns.zerodep.flow.model.definition.FieldAccess;
import org.dpdns.zerodep.flow.model.definition.NodeFormAccess;
import org.dpdns.zerodep.flow.model.definition.Outcome;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.form.FormAccess.FieldState;
import org.dpdns.zerodep.flow.model.form.FormAccess;
import org.dpdns.zerodep.flow.model.runtime.Cc;
import org.dpdns.zerodep.flow.model.runtime.CcItem;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance;
import org.dpdns.zerodep.flow.model.runtime.TodoItem;
import org.dpdns.zerodep.flow.model.runtime.WorkItem;
import org.dpdns.zerodep.query.exec.Page;
import org.dpdns.zerodep.wire.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 查询（0006 第 4 节）：发起列表与发起页、收件箱与待办详情、实例详情；已办、我发起的、抄送、进度、统计随场景补上。
 */
public final class Queries {
    private final EngineContext context;
    private final DefinitionCache definitions;

    public Queries(EngineContext context) {
        this.context = context;
        this.definitions = context.definitions();
    }

    /** 我能发起的流程：启用着、发起人名单里有我（名单只认写死的值），按分类、名称排 */
    public List<StartableProcess> startable(Actor actor) {
        List<StartableProcess> out = new ArrayList<>();
        for (StoredProcess process : context.stores().definitions().processes()) {
            if (!StoredProcess.ACTIVE.equals(process.status())) continue;
            if (!CandidateRules.isStarter(actor, latest(process.processKey()).model().starters())) continue;
            out.add(new StartableProcess(process.processKey(), process.name(), process.category(), process.latestVersion()));
        }
        return out;
    }

    /** 发起页：流程与开始节点上的表单（没写权限的字段默认能改）；流程停用、我不能发起时报 {@code flow.forbidden} */
    public StartView startView(Actor actor, String key) {
        StoredProcess process = context.stores().definitions().process(key).orElseThrow(() -> FlowErrors.notFound("process", key));
        if (!StoredProcess.ACTIVE.equals(process.status())) throw FlowErrors.forbidden("process", key, "disabled");
        LoadedDefinition definition = latest(key);
        if (!CandidateRules.isStarter(actor, definition.model().starters())) throw FlowErrors.forbidden("process", key, "not-a-starter");
        FormView form = definition.hasForm()
            ? formView(definition, definition.graph().start().form(), true, FormValues.empty(), name -> "sys.initiator".equals(name) ? actor.id() : null)
            : null;
        Function<String, Object> read = name -> "sys.initiator".equals(name) ? actor.id() : null;
        List<PickView> picks = new PickPreview(definition.graph(), context).after(definition.graph().start().id(), null, read);
        return new StartView(new StartableProcess(key, process.name(), process.category(), process.latestVersion()), form, picks);
    }

    /**
     * 我的待办：落到我的，加上我、我的角色、部门或扩展种类（{@link Actor#groups()}）能认领的（我没被排除），按到达时间从新到旧；
     * 每行带上实例的标题与摘要列（0006 第 4 节"待办的业务摘要列"）。
     */
    public Page<TodoItem> todo(Actor actor, int page, int size) {
        Map<String, Set<Long>> memberships = new LinkedHashMap<>(actor.groups());
        memberships.put(Candidate.USER, Set.of(actor.id()));
        memberships.put(Candidate.ROLE, actor.roles());
        memberships.put(Candidate.DEPARTMENT, actor.departments());
        return withInstances(context.stores().workItems().todo(new Member(actor.id(), memberships), page, size));
    }

    /** 待办列表的每一行带上实例的标题、摘要列与状态（0006 第 4 节"待办的业务摘要列"），处理过的再带上结果的名字 */
    private Page<TodoItem> withInstances(Page<WorkItem> items) {
        Map<Long, InstanceRecord> instances = instancesOf(items.rows().stream().map(WorkItem::instanceId).collect(Collectors.toSet()));
        List<TodoItem> rows = items.rows().stream().map(item -> {
            InstanceRecord instance = instances.get(item.instanceId());
            if (instance == null) return new TodoItem(item, null, Map.of(), null, null);
            return new TodoItem(item, instance.title(), instance.summary(), instance.status(), outcomeLabel(instance, item));
        }).toList();
        return new Page<>(rows, items.total(), items.page(), items.size());
    }

    /** 一批实例，按编号 */
    private Map<Long, InstanceRecord> instancesOf(Set<Long> ids) {
        return context.stores().instances().findAll(ids).stream().collect(Collectors.toMap(InstanceRecord::id, Function.identity()));
    }

    /** 处理结果的名字：按实例的定义版本找节点上的 outcome；还没处理、隐式的结果（加签办完）时为 null */
    private String outcomeLabel(InstanceRecord instance, WorkItem item) {
        if (item.outcome() == null) return null;
        if (!(definitions.of(instance).graph().node(item.node()) instanceof ProcessNode.Task task)) return null;
        return task.outcomes().stream().filter(o -> o.key().equals(item.outcome())).map(Outcome::label).findFirst().orElse(null);
    }

    /** 我处理过的：按处理时间从新到旧，每行带实例的标题与摘要列（同收件箱的行） */
    public Page<TodoItem> done(Actor actor, int page, int size) {
        return withInstances(context.stores().workItems().done(actor.id(), page, size));
    }

    /** 抄送我的：未读在前，每行带实例的标题、摘要列与状态 */
    public Page<CcItem> ccList(Actor actor, int page, int size) {
        Page<Cc> ccs = context.stores().ccs().received(actor.id(), page, size);
        Map<Long, InstanceRecord> instances = instancesOf(ccs.rows().stream().map(Cc::instanceId).collect(Collectors.toSet()));
        List<CcItem> rows = ccs.rows().stream().map(cc -> {
            InstanceRecord instance = instances.get(cc.instanceId());
            return instance == null ? new CcItem(cc, null, Map.of(), null) : new CcItem(cc, instance.title(), instance.summary(), instance.status());
        }).toList();
        return new Page<>(rows, ccs.total(), ccs.page(), ccs.size());
    }

    /** 管理端搜实例：按发起时间从新到旧 */
    public Page<ProcessInstance> instances(InstanceSearch search, int page, int size) {
        Page<InstanceRecord> rows = context.stores().instances().search(search, page, size);
        return new Page<>(rows.rows().stream().map(InstanceRecord::view).toList(), rows.total(), rows.page(), rows.size());
    }

    /** 管理端的流程列表：全部流程（停用的也在），按分类、名称排 */
    public List<ProcessSummary> processes() {
        return context.stores().definitions().processes().stream()
            .map(p -> new ProcessSummary(p.processKey(), p.name(), p.category(), p.status(), p.latestVersion())).toList();
    }

    /** 重试用完的作业，新的在前 */
    public Page<Job> failedJobs(int page, int size) {
        return context.stores().jobs().failed(page, size);
    }

    /** 我发起的：按发起时间从新到旧 */
    public Page<ProcessInstance> started(Actor actor, int page, int size) {
        Page<InstanceRecord> rows = context.stores().instances().startedBy(actor.id(), page, size);
        return new Page<>(rows.rows().stream().map(InstanceRecord::view).toList(), rows.total(), rows.page(), rows.size());
    }

    /**
     * 待办详情：待办与实例、这个节点上的表单（按节点权限去掉隐藏的字段）、能选的处理结果，以及我现在能不能认领、处理。
     * 待办已处理、实例已结束时照样能看，只是两样都不能做。
     */
    public WorkItemView workItem(Actor actor, Long workItemId) {
        WorkItem item = context.stores().workItems().find(workItemId).orElseThrow(() -> FlowErrors.notFound("workItem", workItemId));
        InstanceRecord instance = context.stores().instances().find(item.instanceId())
            .orElseThrow(() -> FlowErrors.notFound("instance", item.instanceId()));
        LoadedDefinition definition = definitions.of(instance);
        ProcessNode.Task task = (ProcessNode.Task) definition.graph().node(item.node());

        boolean open = WorkItem.OPEN.equals(item.state()) && ProcessInstance.ACTIVE.equals(instance.status());
        boolean mine = actor.id().equals(item.assigneeId());
        boolean candidate = item.assigneeId() == null && CandidateRules.isCandidate(actor, context.stores().workItems().candidates(item.id()));

        FormValues stored = context.stores().formData().read(instance.id()).map(StoredFormData::values).orElse(FormValues.empty());
        FormView form = definition.hasForm()
            ? formView(definition, task.form(), false, stored, name -> References.read(name, instance, stored))
            : null;
        boolean completable = open && (mine || candidate);
        PickPreview preview = new PickPreview(definition.graph(), context);
        Function<String, Object> read = name -> References.read(name, instance, stored);
        List<OutcomeView> outcomes = task.outcomes().stream()
            .map(o -> outcomeView(o, completable ? preview.after(task.id(), o.key(), read) : List.of())).toList();
        WorkItemView.Actions actions = completable
            ? new WorkItemView.Actions(task.allow().transfer(), task.allow().delegate(), task.allow().addSign(),
                mine && WorkItem.CLAIMED.equals(item.assignment()))
            : WorkItemView.Actions.NONE;
        return new WorkItemView(item, instance.view(), form, outcomes, open && candidate, completable, actions);
    }

    /**
     * 实例详情：实例、表单、全部待办、流转记录，以及我能不能撤回、催办。
     * 表单都只读，按流程级的 {@code formView} 去掉隐藏的字段（0004 的 4.3、0005 的 2.1）：在待办里看的人走待办详情。
     */
    public InstanceDetail instance(Actor actor, Long instanceId) {
        InstanceRecord instance = context.stores().instances().find(instanceId).orElseThrow(() -> FlowErrors.notFound("instance", instanceId));
        LoadedDefinition definition = definitions.of(instance);
        FormValues stored = context.stores().formData().read(instanceId).map(StoredFormData::values).orElse(FormValues.empty());
        FormView form = definition.hasForm()
            ? formView(definition, new NodeFormAccess(FieldAccess.READONLY, definition.model().settings().formView()), false, stored,
                name -> References.read(name, instance, stored))
            : null;
        List<WorkItem> items = context.stores().workItems().ofInstance(instanceId);
        boolean starterOfActive = actor.id().equals(instance.startedBy()) && ProcessInstance.ACTIVE.equals(instance.status());
        InstanceDetail.Actions actions = new InstanceDetail.Actions(
            starterOfActive && WithdrawHandler.allowed(definition, items),
            starterOfActive && items.stream().anyMatch(w -> WorkItem.OPEN.equals(w.state())));
        List<InstanceDetail.NodeRef> nodes = definition.model().nodes().stream()
            .filter(n -> !(n instanceof ProcessNode.Start) && !(n instanceof ProcessNode.Gateway))
            .map(n -> new InstanceDetail.NodeRef(n.id(), n.name(), n.type())).toList();
        return new InstanceDetail(instance.view(), form, items, context.stores().events().timeline(instanceId), actions, nodes);
    }

    /** 流程最新发布的版本（解析好的） */
    private LoadedDefinition latest(String key) {
        return definitions.of(context.stores().definitions().latest(key).orElseThrow(() -> FlowErrors.notFound("process", key)));
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
        FormClient client = context.stores().forms().client(definition.form().id()).orElse(new FormClient(null, null, null));
        @SuppressWarnings("unchecked")
        Map<String, Object> manifest = (Map<String, Object>) JsonNumbers.normalize(Json.read(definition.form().manifest()));
        return new FormView(definition.form().id(), definition.form().formKey(), definition.form().version(), manifest,
            client.clientSource(), client.clientCompiled(), client.compilerVersion(), FormView.accessJson(access), isStart, visible);
    }

    /** 处理结果给前端的样子：没写意见要求时是 {@code none}（不收意见，0005 的人工节点） */
    private static OutcomeView outcomeView(Outcome outcome, List<PickView> picks) {
        return new OutcomeView(outcome.key(), outcome.label(), outcome.kindOrDefault(), outcome.comment() == null ? "none" : outcome.comment(),
            outcome.confirm(), picks);
    }
}
