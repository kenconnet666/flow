package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.Variables;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.store.InstanceStore.InstanceRecord;
import org.dpdns.zerodep.flow.core.store.WorkItemStore.Candidate;
import org.dpdns.zerodep.flow.model.definition.Outcome;
import org.dpdns.zerodep.flow.model.definition.ProcessEdge;
import org.dpdns.zerodep.flow.model.definition.ProcessGraph;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.definition.Routing;
import org.dpdns.zerodep.flow.model.error.ErrorDetail;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.dpdns.zerodep.flow.model.runtime.FlowEvent;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance;
import org.dpdns.zerodep.flow.model.runtime.WorkItem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 一个命令里对一个实例的推进：让实例从某个节点往前走，直到停在人工节点（等人处理）或走到结束。
 * <pre>
 *   命令的处理类（StartHandler、CompleteHandler……）
 *     │  锁住实例、核对参数、收表单
 *     ▼
 *   leave(节点, 结果) ──► 按结果与条件选一条线（Routing.selectEdges）
 *                         线上的副作用改流程变量
 *                         enter(下一个节点)
 *                           ├─ 人工节点：轮次加一，解析处理人，生成待办 ──► 停下，等人处理
 *                           ├─ 排他网关：leave(网关) ──► 接着走
 *                           └─ 结束节点：finish（取消还开着的待办）──► 停下
 *     │
 *     ▼
 *   flush() ──► 重算标题、摘要列、正在处理的节点；写回实例；写入这一步的流转记录
 * </pre>
 * 一路上记下这次新生成的待办（{@link #created}）、结束的待办（{@link #closed}）和流转记录（{@link #events}），处理类把它们作为命令的结果返回。
 * <p>这一版走通最小链路：开始、结束、人工节点（不含会签）、排他网关。其余节点类型在两个后端按场景补齐，遇到时报"还不支持"。
 */
final class Execution {
    /** 一次推进最多经过多少个节点：网关绕成圈、条件永远成立时停下来报错，而不是一直走 */
    private static final int MAX_STEPS = 1000;

    private final EngineContext context;
    private final AssigneeResolver assignees;
    private final Actor actor;
    private final Instant now;
    private final ProcessGraph graph;
    private final int rev;
    private InstanceRecord instance;
    private FormValues form;
    private int steps;

    /** 这次新生成的待办 */
    final List<WorkItem> created = new ArrayList<>();
    /** 这次结束的待办（处理完的、取消的），是结束前的样子 */
    final List<WorkItem> closed = new ArrayList<>();
    /** 这次的流转记录；{@link #flush} 之后带上数据库给的编号 */
    final List<FlowEvent> events = new ArrayList<>();

    /**
     * @param actor    这次的操作人；记进流转记录
     * @param graph    实例按的那个版本的图
     * @param instance 实例当前的样子
     * @param form     实例当前的表单值
     * @param rev      实例锁住后的版本：这一步的流转记录都带它，同一实例的记录按它排出准确的先后
     */
    Execution(EngineContext context, Actor actor, Instant now, ProcessGraph graph, InstanceRecord instance, FormValues form, int rev) {
        this.context = context;
        this.assignees = new AssigneeResolver(context.stores().workItems());
        this.actor = actor;
        this.now = now;
        this.graph = graph;
        this.instance = instance;
        this.form = form;
        this.rev = rev;
    }

    /** 实例现在的样子 */
    InstanceRecord instance() { return instance; }

    /** 表单现在的值 */
    FormValues form() { return form; }

    /** 换上新的表单值（处理类收了表单之后） */
    void form(FormValues next) { this.form = next; }

    /** 实例按的那个版本的图 */
    ProcessGraph graph() { return graph; }

    // ───────── 取值：{ ref } ─────────

    /**
     * 按引用的名字取值（0005 的 {@code { ref }}）。连线条件、处理人、标题模板、字段的显示条件都经过这里取值：
     * <pre>
     *   form.amount        表单字段
     *   sys.initiator      引擎给的：发起人、发起时间、实例编号、业务编号
     *   sys.round.review   某个节点到过几轮
     *   negotiationRound   其余是流程变量
     * </pre>
     */
    Object read(String name) {
        if (name.startsWith("form.")) return form.get(name.substring("form.".length()));
        if (name.startsWith("sys.round.")) return instance.state().round(name.substring("sys.round.".length()));
        if (name.startsWith("sys.")) return system(name.substring("sys.".length()));
        if (name.startsWith("fn.")) throw new UnsupportedOperationException("computed values (" + name + ") are not supported yet");
        return instance.variables().get(name);
    }

    /** {@code sys.} 下引擎给的只读值 */
    private Object system(String name) {
        return switch (name) {
            case "initiator" -> instance.startedBy();
            case "startedAt" -> instance.startedAt() == null ? null : instance.startedAt().toString();
            case "instanceId" -> instance.id();
            case "businessKey" -> instance.businessKey();
            default -> null;
        };
    }

    // ───────── 推进 ─────────

    /**
     * 离开一个节点：按结果与条件选出一条线，应用线上的副作用，进入下一个节点。
     *
     * @param outcome 节点的处理结果；网关、开始节点传 null
     */
    void leave(String nodeId, String outcome) {
        List<ProcessEdge> edges = Routing.selectEdges(graph, nodeId, outcome, this::read);
        if (edges.isEmpty()) {
            // 发布时的校验保证每个结果都有出边；走到这里是条件都不成立、又没有默认边
            throw new FlowException(FlowErrorCode.DEFINITION_INVALID, "no edge leaves " + nodeId + " for outcome " + outcome,
                List.of(ErrorDetail.node(nodeId, "no-edge-selected")));
        }
        if (edges.size() > 1) {
            throw new UnsupportedOperationException("leaving " + nodeId + " on several edges (parallel branches) is not supported yet");
        }
        ProcessEdge edge = edges.get(0);
        addEvent(FlowEvent.NODE_LEFT, graph.node(nodeId), edge.id(), outcome, null, null);
        if (!edge.effects().isEmpty()) {
            Map<String, Object> changed = Routing.applyEffects(edge.effects(), instance.variables().asMap(), this::read);
            instance = instance.withVariables(Variables.of(changed));
        }
        // 从开始节点出来的待办是"发起"来的，其余是"流转"来的（待办的 arrival）
        enter(edge.to(), graph.start().id().equals(nodeId) ? "start" : "forward");
    }

    /** 进入一个节点，按节点类型做它该做的事 */
    private void enter(String nodeId, String arrival) {
        if (++steps > MAX_STEPS) throw new IllegalStateException("the instance passed " + MAX_STEPS + " nodes in one step; the gateways loop");
        ProcessNode node = graph.node(nodeId);
        addEvent(FlowEvent.NODE_ENTERED, node, null, null, null, null);
        if (node instanceof ProcessNode.Task task) {
            arriveAtTask(task, arrival);
        } else if (node instanceof ProcessNode.Gateway && "exclusive".equals(node.type())) {
            leave(nodeId, null);
        } else if (node instanceof ProcessNode.End end) {
            finish(ProcessInstance.COMPLETED, end.result(), FlowEvent.INSTANCE_FINISHED, null);
        } else {
            throw new UnsupportedOperationException("node type '" + node.type() + "' (" + nodeId + ") is not supported yet");
        }
    }

    /**
     * 到达人工节点：轮次加一，解析处理人，生成一个待办。只解析出一个人、没有收件箱时直接落到他；
     * 否则进收件箱（{@code pool}），人和角色、部门都存成候选，谁先认领谁办。
     */
    private void arriveAtTask(ProcessNode.Task task, String arrival) {
        if (task.multi() != null) throw new UnsupportedOperationException("multi-instance tasks (" + task.id() + ") are not supported yet");
        instance = instance.withState(instance.state().enter(task.id()));

        Assignees who = assignees.resolve(task, graph, instance, this::read);
        if (who.isEmpty()) {
            handleEmptyAssignee(task);
            return;
        }
        String assigneeId = who.isDirect() ? who.directUser() : null;
        String assigneeName = assigneeId == null ? null : context.org().userName(assigneeId);
        String assignment = assigneeId != null ? WorkItem.DIRECT : WorkItem.POOL;
        Candidate inbox = who.firstInbox();
        int round = instance.state().round(task.id());

        WorkItem draft = new WorkItem(null, instance.id(), instance.processKey(), instance.priority(), task.id(), task.name(), round,
            WorkItem.OPEN, assignment, arrival, inbox == null ? null : inbox.kind(), inbox == null ? null : inbox.value(),
            assigneeId, assigneeName, now,
            null, null, null, null, null, null, null);   // 认领时间、截止时间、处理结果、意见、处理人、处理人名字、处理时间：都还没有
        WorkItem item = context.stores().workItems().insert(draft, assigneeId != null ? List.of() : who.candidates());
        created.add(item);
        addEvent(FlowEvent.WORK_ITEM_CREATED, task, null, null, item.id(), null);
    }

    /**
     * 处理人解析为空（0005 的 2.1 {@code emptyAssignee}）：{@code autoPass} 按第一个"同意"的结果自动通过；
     * {@code admin}（转给管理员）要接入方的管理员名单，随组织扩展点补上，现在与 {@code error} 一样报错。
     */
    private void handleEmptyAssignee(ProcessNode.Task task) {
        if ("autoPass".equals(graph.definition().settings().emptyAssignee())) {
            String outcome = task.outcomes().stream().filter(o -> "approve".equals(o.kindOrDefault())).map(Outcome::key).findFirst().orElse(null);
            leave(task.id(), outcome);
            return;
        }
        throw new FlowException(FlowErrorCode.VALIDATION_FAILED, "nobody can handle node " + task.id(),
            List.of(ErrorDetail.node(task.id(), "assignee-empty")));
    }

    /**
     * 结束实例：取消还开着的待办，记结束的流转记录。
     *
     * @param status    结束后的状态：完成、终止、撤回
     * @param result    结束结果
     * @param eventType 记哪种流转记录
     * @param comment   原因（终止、撤回时）
     */
    void finish(String status, String result, String eventType, String comment) {
        for (WorkItem item : context.stores().workItems().cancelOpen(instance.id(), now)) {
            closed.add(item);
            addEvent(FlowEvent.WORK_ITEM_CANCELED, graph.node(item.node()), null, null, item.id(), null);
        }
        instance = instance.withEnd(status, result, now);
        addEvent(eventType, null, null, result, null, comment);
    }

    /** 记下一条流转记录（先放在 {@link #events} 里，{@link #flush} 时一起写库）；同一步的记录共用实例的版本 */
    void addEvent(String type, ProcessNode node, String edge, String outcome, Long workItemId, String comment) {
        String nodeId = node == null ? null : node.id();
        String nodeName = node == null ? null : node.name();
        events.add(new FlowEvent(null, instance.id(), workItemId, type, nodeId, nodeName, edge, outcome, rev, actor.id(), actor.name(), now,
            comment, Map.of()));
    }

    // ───────── 写回 ─────────

    /**
     * 结束这一步：重算标题、摘要列、正在处理的节点（{@link InstanceDisplay}），写回实例，写入这一步的流转记录。
     *
     * @param fixedTitle 发起时显式给的标题；为 null 时按模板生成
     */
    void flush(String fixedTitle) {
        instance = InstanceDisplay.refresh(instance, graph.definition(), fixedTitle, this::read, context.stores().workItems());
        context.stores().instances().update(instance);
        List<FlowEvent> saved = context.stores().events().append(events);
        events.clear();
        events.addAll(saved);
    }
}
