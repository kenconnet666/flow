package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.Variables;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.store.InstanceStore.InstanceRecord;
import org.dpdns.zerodep.flow.core.store.WorkItemStore.Candidate;
import org.dpdns.zerodep.flow.model.Values;
import org.dpdns.zerodep.flow.model.definition.AssigneeSource;
import org.dpdns.zerodep.flow.model.definition.Lane;
import org.dpdns.zerodep.flow.model.definition.Outcome;
import org.dpdns.zerodep.flow.model.definition.ProcessEdge;
import org.dpdns.zerodep.flow.model.definition.ProcessGraph;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.definition.Ref;
import org.dpdns.zerodep.flow.model.definition.Routing;
import org.dpdns.zerodep.flow.model.error.ErrorDetail;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.dpdns.zerodep.flow.model.runtime.FlowEvent;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance;
import org.dpdns.zerodep.flow.model.runtime.WorkItem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 一个命令里对一个实例的推进。命令的处理类在事务里创建它，调用 {@link #leave} 等方法让实例往前走，最后 {@link #flush} 一次写回。
 * 一路上记下这次新生成的待办（{@link #created}）、结束的待办（{@link #closed}）和写的流转记录（{@link #events}），作为命令的结果返回。
 * <p>节点的行为按 docs/decisions/0005：
 * <ul>
 *   <li>人工节点：轮次加一，解析处理人，生成一个待办，停下等人处理；</li>
 *   <li>排他网关：按条件选一条线，接着走；</li>
 *   <li>结束节点：结束实例，取消还开着的待办。</li>
 * </ul>
 * 连线上的副作用改流程变量（0005 第 6 节）。这一版走通最小链路：开始、结束、人工节点（不含会签）、排他网关，
 * 处理人来源是指定的人、角色、部门、发起人、同某节点。其余节点类型与来源在两个后端按场景补齐，遇到时报"还不支持"。
 */
final class Execution {
    /** 一次推进最多经过多少个节点：网关绕成圈、条件永远成立时停下来报错，而不是一直走 */
    private static final int MAX_STEPS = 1000;
    /** 标题模板里的占位：{form.title} */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)\\}");

    private final EngineContext context;
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
    /** 这次写的流转记录 */
    final List<FlowEvent> events = new ArrayList<>();

    /**
     * @param actor    这次的操作人；记进流转记录
     * @param graph    实例按的那个版本的图
     * @param instance 实例当前的样子
     * @param form     实例当前的表单值
     * @param rev      实例锁住后的版本：这一步写的流转记录都带它，排出准确的先后
     */
    Execution(EngineContext context, Actor actor, Instant now, ProcessGraph graph, InstanceRecord instance, FormValues form, int rev) {
        this.context = context;
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

    /** 换上新的表单值（收了表单之后） */
    void form(FormValues next) { this.form = next; }

    /** 实例按的那个版本的图 */
    ProcessGraph graph() { return graph; }

    // ───────── 取值：{ ref } ─────────

    /**
     * 按引用的名字取值（0005 的 {@code { ref }}）：{@code form.} 是表单字段，{@code sys.} 是引擎给的（发起人、轮次……），
     * 其余是流程变量。连线条件、处理人、标题模板都经过这里取值。
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

    /** 定义里写的值：直接写的值原样，引用（{@link Ref}）去取 */
    private Object valueOf(Object literalOrRef) {
        return literalOrRef instanceof Ref ref ? read(ref.ref()) : literalOrRef;
    }

    // ───────── 推进 ─────────

    /**
     * 离开一个节点：按结果与条件选出一条线（{@link Routing#selectEdges}），应用线上的副作用，进入下一个节点。
     *
     * @param outcome 节点的处理结果；网关、开始节点传 null
     */
    void leave(String nodeId, String outcome) {
        List<ProcessEdge> edges = Routing.selectEdges(graph, nodeId, outcome, this::read);
        if (edges.isEmpty()) {
            // 发布时的校验保证每个结果都有出边；走到这里是条件都不成立又没有默认边
            throw new FlowException(FlowErrorCode.DEFINITION_INVALID, "no edge leaves " + nodeId + " for outcome " + outcome,
                List.of(ErrorDetail.node(nodeId, "no-edge-selected")));
        }
        if (edges.size() > 1) {
            throw new UnsupportedOperationException("leaving " + nodeId + " on several edges (parallel branches) is not supported yet");
        }
        ProcessEdge edge = edges.get(0);
        record(FlowEvent.NODE_LEFT, graph.node(nodeId), edge.id(), outcome, null, null);
        if (!edge.effects().isEmpty()) {
            Map<String, Object> next = Routing.applyEffects(edge.effects(), instance.variables().asMap(), this::read);
            instance = instance.withVariables(Variables.of(next));
        }
        enter(edge.to(), nodeId);
    }

    /** 进入一个节点，按节点类型做它该做的事 */
    private void enter(String nodeId, String fromNode) {
        if (++steps > MAX_STEPS) throw new IllegalStateException("the instance passed " + MAX_STEPS + " nodes in one step; the gateways loop");
        ProcessNode node = graph.node(nodeId);
        record(FlowEvent.NODE_ENTERED, node, null, null, null, null);
        if (node instanceof ProcessNode.Task task) {
            arriveAtTask(task, fromNode);
        } else if (node instanceof ProcessNode.Gateway && "exclusive".equals(node.type())) {
            leave(nodeId, null);
        } else if (node instanceof ProcessNode.End end) {
            finish(ProcessInstance.COMPLETED, end.result(), FlowEvent.INSTANCE_FINISHED, null);
        } else {
            throw new UnsupportedOperationException("node type '" + node.type() + "' (" + nodeId + ") is not supported yet");
        }
    }

    /** 到达人工节点：轮次加一，解析处理人，生成一个待办（一个人时直接落到他，否则进收件箱） */
    private void arriveAtTask(ProcessNode.Task task, String fromNode) {
        if (task.multi() != null) throw new UnsupportedOperationException("multi-instance tasks (" + task.id() + ") are not supported yet");
        instance = instance.withState(instance.state().enter(task.id()));

        Assignees assignees = resolveAssignees(task);
        if (assignees.isEmpty()) {
            handleEmptyAssignee(task);
            return;
        }
        String assignee = assignees.isDirect() ? assignees.directUser() : null;
        Candidate inbox = assignees.firstInbox();
        WorkItem item = new WorkItem(context.ids().next("flow_work_item"), instance.id(), instance.processKey(), instance.priority(),
            task.id(), task.name(), instance.state().round(task.id()), WorkItem.OPEN, assignee != null ? WorkItem.DIRECT : WorkItem.POOL,
            fromNode == null ? "start" : "forward", inbox == null ? null : inbox.kind(), inbox == null ? null : inbox.value(),
            assignee, assignee == null ? null : context.org().userName(assignee), now, null, null, null, null, null, null, null, null, null);
        context.stores().workItems().insert(item, assignee != null ? List.of() : assignees.candidates());
        created.add(item);
        record(FlowEvent.WORK_ITEM_CREATED, task, null, null, item.id(), null);
    }

    /** 0005 的 3.2：几个来源取并集；节点没写来源时用所在泳道的收件箱 */
    private Assignees resolveAssignees(ProcessNode.Task task) {
        List<AssigneeSource> sources = task.assignees();
        if (sources.isEmpty()) {
            Lane lane = graph.lane(task.lane());
            sources = lane == null || lane.inbox() == null ? List.of() : List.of(lane.inbox().source());
        }
        Assignees out = Assignees.empty();
        for (AssigneeSource source : sources) {
            if (source instanceof AssigneeSource.User user) {
                out.users().addAll(idsOf(valueOf(user.user())));
            } else if (source instanceof AssigneeSource.Role role) {
                idsOf(valueOf(role.role())).forEach(id -> out.inboxes().add(new Candidate(Candidate.ROLE, id)));
            } else if (source instanceof AssigneeSource.Department dept) {
                idsOf(valueOf(dept.department())).forEach(id -> out.inboxes().add(new Candidate(Candidate.DEPARTMENT, id)));
            } else if (source instanceof AssigneeSource.Initiator) {
                out.users().add(instance.startedBy());
            } else if (source instanceof AssigneeSource.SameAs same) {
                context.stores().workItems().lastCompletedBy(instance.id(), same.node()).ifPresent(out.users()::add);
            } else {
                throw new UnsupportedOperationException("assignee source " + source.getClass().getSimpleName() + " is not supported yet");
            }
        }
        return out;
    }

    /** 一个编号，或一组编号（{@code people} 类型的值是列表） */
    private static List<String> idsOf(Object value) {
        if (value instanceof Collection<?> items) return items.stream().filter(v -> !Values.isBlank(v)).map(Values::toText).toList();
        return Values.isBlank(value) ? List.of() : List.of(Values.toText(value));
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
     * 结束实例：取消还开着的待办，写结束的记录。
     *
     * @param status    结束后的状态：完成、终止、撤回
     * @param result    结束结果
     * @param eventType 写哪种流转记录
     * @param comment   原因（终止、撤回时）
     */
    void finish(String status, String result, String eventType, String comment) {
        for (WorkItem item : context.stores().workItems().cancelOpen(instance.id(), now)) {
            closed.add(item);
            record(FlowEvent.WORK_ITEM_CANCELED, graph.node(item.node()), null, null, item.id(), null);
        }
        instance = instance.withEnd(status, result, now);
        record(eventType, null, null, result, null, comment);
    }

    /** 记一条流转记录：同一步的记录共用实例的版本 */
    void record(String type, ProcessNode node, String edge, String outcome, String workItemId, String comment) {
        events.add(new FlowEvent(context.ids().next("flow_event"), instance.id(), workItemId, type, node == null ? null : node.id(),
            node == null ? null : node.name(), edge, outcome, rev, actor.id(), actor.name(), now, comment, Map.of()));
    }

    // ───────── 写回 ─────────

    /**
     * 结束这一步：按最新的表单与变量重算标题和摘要列（0005 第 2 节：每次提交都重新生成），算出正在处理的节点，
     * 写回实例，写入流转记录。
     *
     * @param fixedTitle 发起时显式给的标题；为 null 时按模板生成
     */
    void flush(String fixedTitle) {
        Map<String, Object> summary = new LinkedHashMap<>();
        for (String name : graph.definition().summary()) summary.put(name, read(name));
        String title = fixedTitle != null ? fixedTitle : renderTitle();
        instance = instance.withDisplay(title, summary, currentNodes());
        context.stores().instances().update(instance, now);
        context.stores().events().append(events);
    }

    /** 正在处理的节点：还开着的待办所在的节点，去重；实例结束了就是空的 */
    private List<ProcessInstance.CurrentNode> currentNodes() {
        if (!ProcessInstance.ACTIVE.equals(instance.status())) return List.of();
        Map<String, ProcessInstance.CurrentNode> open = new LinkedHashMap<>();
        for (WorkItem item : context.stores().workItems().ofInstance(instance.id())) {
            if (WorkItem.OPEN.equals(item.state())) open.putIfAbsent(item.node(), new ProcessInstance.CurrentNode(item.node(), item.nodeName()));
        }
        return List.copyOf(open.values());
    }

    /** 按模板生成标题：花括号里的引用换成值；不写模板时用流程名 */
    private String renderTitle() {
        String template = graph.definition().title();
        if (template == null || template.isBlank()) return graph.definition().name();
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Object value = read(m.group(1).trim());
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : Values.toText(value)));
        }
        m.appendTail(out);
        return out.toString();
    }
}
