package org.dpdns.zerodep.flow.core.engine;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.dpdns.zerodep.flow.core.engine.assignee.AssigneeResolver;
import org.dpdns.zerodep.flow.core.engine.form.References;
import org.dpdns.zerodep.flow.core.engine.job.DeliveryJob;
import org.dpdns.zerodep.flow.core.engine.job.JobRunner;
import org.dpdns.zerodep.flow.core.engine.job.OutboxEntry;
import org.dpdns.zerodep.flow.core.engine.node.CallBehavior;
import org.dpdns.zerodep.flow.core.engine.node.CcBehavior;
import org.dpdns.zerodep.flow.core.engine.node.CustomBehavior;
import org.dpdns.zerodep.flow.core.engine.node.GatewayBehavior;
import org.dpdns.zerodep.flow.core.engine.node.ReceiveBehavior;
import org.dpdns.zerodep.flow.core.engine.node.ServiceBehavior;
import org.dpdns.zerodep.flow.core.engine.node.TaskBehavior;
import org.dpdns.zerodep.flow.core.engine.node.TimerBehavior;
import org.dpdns.zerodep.flow.core.engine.notify.Notifications;
import org.dpdns.zerodep.flow.core.engine.rule.Deadlines;
import org.dpdns.zerodep.flow.core.engine.state.RunState;
import org.dpdns.zerodep.flow.core.engine.state.Variables;
import org.dpdns.zerodep.flow.core.engine.store.Instances;
import org.dpdns.zerodep.flow.core.engine.store.WorkItems;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.entity.JobEntity;
import org.dpdns.zerodep.flow.core.entity.StepEntity;
import org.dpdns.zerodep.flow.core.entity.WaitEntity;
import org.dpdns.zerodep.flow.core.spi.Actor;
import org.dpdns.zerodep.flow.core.spi.FormValues;
import org.dpdns.zerodep.flow.core.spi.Notification;
import org.dpdns.zerodep.flow.core.view.CommandResult;
import org.dpdns.zerodep.flow.core.view.instance.FlowEventVO;
import org.dpdns.zerodep.flow.core.view.instance.InstanceVO;
import org.dpdns.zerodep.flow.core.view.workitem.WorkItemVO;
import org.dpdns.zerodep.flow.model.definition.GatewayPairs;
import org.dpdns.zerodep.flow.model.definition.ProcessEdge;
import org.dpdns.zerodep.flow.model.definition.ProcessGraph;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.definition.Routing;
import org.dpdns.zerodep.flow.model.error.ErrorDetail;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.dpdns.zerodep.flow.model.form.FormManifest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * 一个命令里对一个实例的推进：让实例从某个节点往前走，直到停在人工节点（等人处理）或走到结束。
 * <pre>
 *   命令的处理类（engine.handler 里的 StartHandler、CompleteHandler……）
 *     │  锁住实例、核对参数、收表单
 *     ▼
 *   leave(节点, 结果) ──► 按结果与条件选出线（Routing.selectEdges），线上的副作用改流程变量
 *                         enter(下一个节点) ──► 按节点类型交给 engine.node 里对应的行为：
 *                           ├─ task      人工节点：解析处理人、生成待办（会签每人一份）──► 停下，等人处理
 *                           ├─ 网关      排他、分叉直接往下走；汇合等齐了才往下走
 *                           ├─ timer     定时节点：排一个作业 ──► 停下，到时候再走
 *                           ├─ service   自动节点：同步的马上做动作、往下走；异步的排作业 ──► 停下
 *                           ├─ receive   等待外部事件 ──► 停下，事件到了（signal）再走
 *                           ├─ call      调用流程：发起子实例；同步的停下等它们结束
 *                           ├─ cc        抄送：抄送给写的人 ──► 接着走
 *                           └─ end       结束：finish（取消还开着的待办）──► 停下
 *                         退回、提级的线：跳出并行区域时先取消区域里其余的分支；退回落到原来的处理人，提级进收件箱
 *     │
 *     ▼
 *   flush() ──► 重算标题、摘要列、正在处理的节点；写回实例；写入这一步的流转记录
 * </pre>
 * 一路上记下这次新生成的待办（{@link #getCreated()}）、结束的待办（{@link #getClosed()}）和流转记录，处理类把它们作为命令的结果返回。
 */
@Getter
public class Execution {
    /** 一次推进最多经过多少个节点：网关绕成圈、条件永远成立时停下来报错，而不是一直走 */
    private static final int MAX_STEPS = 1000;

    /** 引擎内部共用的东西 */
    private final EngineContext context;
    /** 按来源解析处理人 */
    private final AssigneeResolver assignees;
    /** 这次的操作人；引擎自己推进（作业）时是 {@link SystemActor} */
    private final Actor actor;
    /** 这一步的时刻 */
    private final Instant now;
    /** 实例按的那个版本的图 */
    private final ProcessGraph graph;
    /** 并行、包容网关的分叉与汇合配对 */
    private final GatewayPairs pairs;
    /** 表单的字段清单；流程没有表单时为 null */
    private final FormManifest manifest;
    /** 时限算成时刻 */
    private final Deadlines deadlines;
    /** 实例锁住后的版本：这一步的流转记录都带它，同一实例的记录按它排出准确的先后 */
    private final int rev;
    /** 实例现在的样子：推进时就地改它，{@link #flush} 时整行写回 */
    private final InstanceEntity instance;

    /** 表单现在的值（处理类收了表单之后换上新的） */
    @Setter
    private FormValues form;
    /** 这一步的处理人选的下一步处理人（0005 的 5.6）：节点 → 人 */
    @Setter
    private Map<String, List<Long>> picks = Map.of();
    /** 这次处理退回到哪个节点（处理结果 {@code target: "choose"}，已经核对过是能选的） */
    @Setter
    private String returnTarget;
    /** 子实例：这个命令里调用它的父实例的推进（子实例马上就结束时直接回到它）；不是这样发起的为 null */
    @Setter
    private Execution caller;
    /** 子实例被父实例连带终止：结束时不再回到父实例 */
    @Setter
    private boolean quiet;

    /** 这次新生成的待办 */
    private final List<WorkItemVO> created = new ArrayList<>();
    /** 这次结束的待办（处理完的、取消的），是结束前的样子 */
    private final List<WorkItemVO> closed = new ArrayList<>();
    /** 这次的流转记录，{@link #publish} 时写库并读回（带上数据库给的编号） */
    private final List<FlowEventVO> events = new ArrayList<>();
    /** 还没写库的流转记录 */
    @Getter(AccessLevel.NONE)
    private final List<EventEntity> pending = new ArrayList<>();
    /** 这一步已经经过了几个节点 */
    @Getter(AccessLevel.NONE)
    private int steps;

    /**
     * @param actor      这次的操作人；记进流转记录
     * @param definition 实例按的那个版本（图与网关配对）
     * @param instance   实例当前的样子
     * @param form       实例当前的表单值
     * @param rev        实例锁住后的版本
     */
    public Execution(EngineContext context, Actor actor, Instant now, LoadedDefinition definition, InstanceEntity instance, FormValues form, int rev) {
        this.context = context;
        this.assignees = new AssigneeResolver(context.getDb(), context.getOrg(), context.getExtensions());
        this.actor = actor;
        this.now = now;
        this.graph = definition.getGraph();
        this.pairs = definition.getPairs();
        this.manifest = definition.getManifest();
        this.deadlines = new Deadlines(context.getCalendar(), definition.model().getSettings().getCalendar());
        this.instance = instance;
        this.form = form;
        this.rev = rev;
    }

    /** 实例给前端、监听的样子（从库里读：{@link #flush} 之后才是这一步之后的） */
    public InstanceVO loadView() { return Instances.view(context.getDb(), instance.getId()); }

    /** 这一步的结果：实例、新生成与结束的待办、流转记录（{@link #flush} 之后调） */
    public CommandResult toResult() { return new CommandResult(loadView(), created, closed, events); }

    /** 按引用的名字取值（规则见 {@link References}）：用推进到此刻的表单与实例 */
    public Object read(String name) { return References.read(name, instance, form, context.getExtensions()); }

    /** 改实例的运行状态（轮次、汇合、等待……都是不可变的 RunState，改一处换一份） */
    public void updateState(UnaryOperator<RunState> change) {
        instance.setState(change.apply(instance.getState()));
    }

    // ───────── 推进 ─────────

    /**
     * 离开一个节点：按结果与条件选出线（Routing.selectEdges），每条线应用副作用、进入下一个节点。
     * 并行、包容的分叉会选出几条线：先记下配对的汇合要等几条分支，再逐条往下走。
     *
     * @param outcome 节点的处理结果；网关、开始节点传 null
     */
    public void leave(String nodeId, String outcome) {
        List<ProcessEdge> edges = Routing.selectEdges(graph, nodeId, outcome, this::read);
        if (edges.isEmpty()) {
            // 发布时的校验保证每个结果都有出边；走到这里是条件都不成立、又没有默认边
            throw new FlowException(FlowErrorCode.DEFINITION_INVALID, "no edge leaves " + nodeId + " for outcome " + outcome,
                List.of(ErrorDetail.node(nodeId, "no-edge-selected")));
        }
        String join = pairs.joinOf(nodeId);
        if (join != null) updateState(s -> s.fork(join, edges.size()));
        else if (edges.size() > 1) throw new IllegalStateException("node " + nodeId + " leaves on several edges but has no paired join");
        // 从开始节点出来的待办是"发起"来的，其余是"流转"来的（待办的 arrival）
        String arrival = graph.start().getId().equals(nodeId) ? "start" : "forward";
        for (ProcessEdge edge : edges) {
            if (!InstanceEntity.ACTIVE.equals(instance.getStatus())) return;   // 前一条分支走到了终止的结束节点
            addEvent(EventEntity.NODE_LEFT, graph.node(nodeId), edge.getId(), outcome, null, null);
            if (!edge.getEffects().isEmpty()) {
                instance.setVariables(Variables.of(Routing.applyEffects(edge.getEffects(), instance.getVariables().asMap(), this::read)));
            }
            if (edge.isBackward()) {
                leaveRegions(nodeId, edge.getTo());
                rememberResubmit(nodeId, edge.getTo(), edge.getKind());
            }
            enter(edge.getTo(), edge.isBackward() ? edge.getKind() : arrival);
        }
    }

    /**
     * 处理完一个节点后往下走：这个节点是被退回来的、退回它的节点要求"重新提交后直接回来"（{@code returnTo: "resubmit"}）时，
     * 直接回到那个节点；否则照常 {@link #leave}。
     */
    public void proceed(String nodeId, String outcome) {
        String back = instance.getState().resubmitTo(nodeId);
        if (back == null) {
            leave(nodeId, outcome);
            return;
        }
        updateState(s -> s.resubmitted(nodeId));
        addEvent(EventEntity.NODE_LEFT, graph.node(nodeId), null, outcome, null, null);
        enter(back, "resubmit");
    }

    /**
     * 退回到处理人选的节点（0005 的 5.4 {@code target: "choose"}）：同沿退回的线走——跳出并行区域时取消区域里的待办，
     * 要求"重新提交后直接回来"时记下，到了那里落到它原来的处理人。流转记录 {@code node.left} 的 data 里记着目标 target。
     */
    public void returnTo(String from, String outcome) {
        if (returnTarget == null) throw FlowErrors.invalid("returnTarget", "required");
        addEvent(EventEntity.NODE_LEFT, graph.node(from), null, outcome, null, null, Map.of("target", returnTarget));
        leaveRegions(from, returnTarget);
        rememberResubmit(from, returnTarget, "return");
        enter(returnTarget, "return");
    }

    /** 进入一个节点：按节点类型交给 engine.node 里对应的行为 */
    private void enter(String nodeId, String arrival) {
        if (++steps > MAX_STEPS) throw new IllegalStateException("the instance passed " + MAX_STEPS + " nodes in one step; the gateways loop");
        ProcessNode node = graph.node(nodeId);
        addEvent(EventEntity.NODE_ENTERED, node, null, null, null, null);
        switch (node.getType()) {
            case "task" -> TaskBehavior.arrive(this, (ProcessNode.Task) node, arrival);
            case "exclusive", "parallel", "inclusive" -> GatewayBehavior.arrive(this, nodeId);
            case "timer" -> TimerBehavior.arrive(this, (ProcessNode.Timer) node);
            case "service" -> ServiceBehavior.arrive(this, (ProcessNode.Service) node);
            case "receive" -> ReceiveBehavior.arrive(this, (ProcessNode.Receive) node);
            case "call" -> CallBehavior.arrive(this, (ProcessNode.Call) node);
            case "cc" -> CcBehavior.arrive(this, (ProcessNode.Cc) node);
            case "end" -> finish(InstanceEntity.COMPLETED, ((ProcessNode.End) node).getResult(), EventEntity.INSTANCE_FINISHED, null);
            default -> {
                if (!(node instanceof ProcessNode.Custom custom)) throw new UnsupportedOperationException("node type '" + node.getType() + "' (" + nodeId + ") is not supported");
                CustomBehavior.arrive(this, custom);
            }
        }
    }

    /**
     * 退回、提级的线跳出了并行区域（目标不在区域里）：区域里其余还开着的待办一起取消，汇合也不再等它们（0005 的 4.4）。
     * 由里到外看每一层区域。
     */
    private void leaveRegions(String from, String to) {
        for (String fork : pairs.forksAround(from)) {
            if (pairs.region(fork).contains(to)) return;
            cancelItems(WorkItems.cancelOpenAt(context.getDb(), instance.getId(), pairs.region(fork), now));
            // 区域里停在定时节点上的分支也不再等（作业到时候看到不在等了就跳过）
            for (String node : pairs.region(fork)) updateState(s -> s.waitDone(node));
            updateState(s -> s.forget(pairs.joinOf(fork)));
        }
    }

    /** 退回它的节点写了 {@code returnTo: "resubmit"}：记下来，被退回的节点重新提交后直接回来 */
    private void rememberResubmit(String from, String to, String kind) {
        if ("return".equals(kind) && graph.node(from) instanceof ProcessNode.Task task && "resubmit".equals(task.getReturnTo())) {
            updateState(s -> s.returned(to, from));
        }
    }

    // ───────── 管理员与撤回 ─────────

    /** 撤回刚处理的一步：运行状态、流程变量换回处理之前的 */
    public void restore(RunState state, Variables variables) {
        instance.setState(state);
        instance.setVariables(variables);
    }

    /** 换实例的状态（暂停、恢复），记一条流转记录 */
    public void changeStatus(String status, String eventType, String comment) {
        instance.setStatus(status);
        addEvent(eventType, null, null, null, null, comment);
    }

    /** 管理员跳转：还没办完的待办都取消，等待中的汇合、队列都不再记，从这个节点重新走 */
    public void jumpTo(String nodeId, String reason) {
        cancelItems(WorkItems.cancelOpen(context.getDb(), instance.getId(), now));
        updateState(RunState::withoutPending);
        addEvent(EventEntity.INSTANCE_JUMPED, graph.node(nodeId), null, null, null, reason);
        enter(nodeId, "jump");
    }

    /**
     * 结束实例：取消还开着的待办，记结束的流转记录。
     *
     * @param status    结束后的状态：完成、终止、撤回
     * @param result    结束结果
     * @param eventType 记哪种流转记录
     * @param comment   原因（终止、撤回时）
     */
    public void finish(String status, String result, String eventType, String comment) {
        cancelItems(WorkItems.cancelOpen(context.getDb(), instance.getId(), now));
        instance.end(status, result, now);
        // 还没做的作业、等待、撤回用的快照都不再要（失败的作业留着给管理员看；发件箱的投递还要做）
        Long id = instance.getId();
        context.getDb().deletable(JobEntity.class).allowDeleteStatement(true)
            .where(j -> { j.instanceId().eq(id); j.state().eq(JobEntity.PENDING); j.kind().ne(DeliveryJob.KIND); }).executeRows();
        context.getDb().deletable(WaitEntity.class).allowDeleteStatement(true).where(w -> w.instanceId().eq(id)).executeRows();
        context.getDb().deletable(StepEntity.class).allowDeleteStatement(true).where(st -> st.instanceId().eq(id)).executeRows();
        addEvent(eventType, null, null, result, null, comment);
        // 调用流程：终止、撤回时子实例一起终止；子实例结束了回到父实例（0005 的 4.2）
        if (InstanceEntity.TERMINATED.equals(status) || InstanceEntity.WITHDRAWN.equals(status)) CallBehavior.terminateChildren(this, comment);
        if (instance.getParentInstanceId() != null && !quiet) CallBehavior.childEnded(this);
    }

    /** 取消了的待办：记进这次结束的、各记一条流转记录 */
    public void cancelItems(List<WorkItemVO> canceled) {
        for (WorkItemVO item : canceled) {
            closed.add(item);
            addEvent(EventEntity.WORK_ITEM_CANCELED, graph.node(item.getNode()), null, null, item.getId(), null);
        }
    }

    // ───────── 作业与流转记录 ─────────

    /**
     * 排一个这个实例的作业（到时候由 {@link JobRunner} 做）。
     *
     * @param workItemId  相关的待办；定时节点、自动节点、等待节点的作业为 null
     * @param maxAttempts 最多试几次
     */
    public void schedule(String kind, Long workItemId, Instant dueAt, Map<String, Object> payload, int maxAttempts) {
        JobEntity job = new JobEntity();
        job.setKind(kind);
        job.setInstanceId(instance.getId());
        job.setWorkItemId(workItemId);
        job.setDueAt(dueAt);
        job.setPayload(payload);
        job.setState(JobEntity.PENDING);
        job.setAttempts(0);
        job.setMaxAttempts(maxAttempts);
        job.setCreatedAt(now);
        context.getDb().insertable(job).executeRows();
    }

    /** 记下一条流转记录（先攒着，{@link #publish} 时一起写库）；同一步的记录共用实例的版本 */
    public void addEvent(String type, ProcessNode node, String edge, String outcome, Long workItemId, String comment) {
        addEvent(type, node, edge, outcome, workItemId, comment, Map.of());
    }

    /** 同上，带附加的数据（处理结果的名字这类快照） */
    public void addEvent(String type, ProcessNode node, String edge, String outcome, Long workItemId, String comment, Map<String, Object> data) {
        EventEntity event = new EventEntity();
        event.setInstanceId(instance.getId());
        event.setWorkItemId(workItemId);
        event.setType(type);
        event.setNode(node == null ? null : node.getId());
        event.setNodeName(node == null ? null : node.getName());
        event.setEdge(edge);
        event.setOutcome(outcome);
        event.setInstanceRev(rev);
        event.setActorId(actor.getId());
        event.setActorName(actor.getName());
        event.setAt(now);
        event.setComment(comment);
        event.setData(data);
        pending.add(event);
    }

    // ───────── 写回 ─────────

    /**
     * 结束这一步：重算标题、摘要列、正在处理的节点（{@link InstanceDisplay}），写回实例，写入这一步的流转记录。
     *
     * @param fixedTitle 发起时显式给的标题；为 null 时按模板生成
     */
    public void flush(String fixedTitle) {
        InstanceDisplay.refresh(instance, graph.definition(), fixedTitle, this::read, context.getDb());
        context.getDb().updatable(instance).executeRows();
        publish();
    }

    /**
     * 只写流转记录（不改实例的命令：认领、催办、留言、抄送）：写库后按编号读回（附加数据按 JSON 的数，同以后查到的一致），
     * 交给监听（事务内的马上调，提交后的记下），算出要发的通知（提交后交给渠道）。
     */
    public void publish() {
        if (pending.isEmpty()) return;
        context.getDb().insertable(pending).executeRows(true);
        List<Long> ids = pending.stream().map(EventEntity::getId).toList();
        pending.clear();
        List<FlowEventVO> saved = context.getDb().queryable(EventEntity.class).where(e -> e.id().in(ids)).orderBy(e -> e.id().asc())
            .select(FlowEventVO.class).toList();
        events.addAll(saved);
        InstanceVO current = loadView();
        List<Notification> notifications = context.getDispatcher().notifies()
            ? Notifications.plan(saved, context, graph, current, sources -> assignees.peopleOf(sources, instance, this::read))
            : List.of();
        context.getDispatcher().published(saved, current, graph, notifications);
        // 发件箱：要可靠投的，和记录同一个事务记成作业，马上到期（提交后作业轮询被叫醒来投）
        for (OutboxEntry entry : context.getDispatcher().outbox(saved, current, graph, notifications)) {
            schedule(DeliveryJob.KIND, null, now, entry.toPayload(), DeliveryJob.MAX_ATTEMPTS);
        }
    }
}
