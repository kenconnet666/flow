package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.JsonNumbers;
import org.dpdns.zerodep.flow.core.data.RunState;
import org.dpdns.zerodep.flow.core.data.Variables;
import org.dpdns.zerodep.flow.core.extension.ServiceAction;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.store.CcStore.NewCc;
import org.dpdns.zerodep.flow.core.store.InstanceStore.InstanceRecord;
import org.dpdns.zerodep.flow.core.store.JobStore;
import org.dpdns.zerodep.flow.core.store.JobStore.NewJob;
import org.dpdns.zerodep.flow.core.store.WaitStore.NewWait;
import org.dpdns.zerodep.flow.core.store.WorkItemStore.Candidate;
import org.dpdns.zerodep.flow.model.definition.GatewayPairs;
import org.dpdns.zerodep.flow.model.definition.Outcome;
import org.dpdns.zerodep.flow.model.definition.ProcessEdge;
import org.dpdns.zerodep.flow.model.definition.ProcessGraph;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.definition.Routing;
import org.dpdns.zerodep.flow.model.definition.TaskRules;
import org.dpdns.zerodep.flow.model.error.ErrorDetail;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.dpdns.zerodep.flow.model.runtime.FlowEvent;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance;
import org.dpdns.zerodep.flow.model.runtime.WorkItem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 *                           ├─ 人工节点：轮次加一，解析处理人，生成待办（有时限的排上超时、提醒的作业）──► 停下，等人处理
 *                           ├─ 定时节点：记下到什么时候，排一个作业 ──► 停下，到时候作业让它往下走（BuiltinJobs）
 *                           ├─ 抄送节点：抄送给写的人 ──► 接着走
 *                           ├─ 自动节点：同步的马上做动作、按结果接着走；异步的排一个作业 ──► 停下，作业做完再走
 *                           ├─ 等待节点：记下在等什么事件 ──► 停下，事件到了（signal）再走
 *                           ├─ 调用节点：发起子实例；同步的停下等它们结束（CallActivities）
 *                           ├─ 排他网关：leave(网关) ──► 接着走
 *                           ├─ 并行、包容的分叉：选出的每条线都走，记下配对的汇合要等几条
 *                           ├─ 并行、包容的汇合：到了一条，还没等齐 ──► 这条分支停下；等齐了 ──► 接着走
 *                         退回、提级的线（kind）：跳出并行区域时先取消区域里其余的分支；
 *                           退回落到那个节点原来的处理人，提级进目标节点的收件箱
 *                           └─ 结束节点：finish（取消还开着的待办）──► 停下
 *     │
 *     ▼
 *   flush() ──► 重算标题、摘要列、正在处理的节点；写回实例；写入这一步的流转记录
 * </pre>
 * 一路上记下这次新生成的待办（{@link #created}）、结束的待办（{@link #closed}）和流转记录（{@link #events}），处理类把它们作为命令的结果返回。
 * <p>现在有：开始、结束、人工节点（含会签，0005 的 5.2；时限、提醒、超时、无人认领，5.3）、定时节点、抄送节点、自动节点（同步、异步）、等待外部事件、调用流程、排他网关、
 * 并行与包容网关（结构化配对，0005 的 4.4）、退回与提级、
 * 重新提交后直接回到退回它的节点（{@code returnTo: "resubmit"}）。
 * 其余节点类型在两个后端按场景补齐，遇到时报"还不支持"。
 */
final class Execution {
    /** 一次推进最多经过多少个节点：网关绕成圈、条件永远成立时停下来报错，而不是一直走 */
    private static final int MAX_STEPS = 1000;

    private final EngineContext context;
    private final AssigneeResolver assignees;
    private final Actor actor;
    private final Instant now;
    private final ProcessGraph graph;
    private final GatewayPairs pairs;
    private final Deadlines deadlines;
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

    /** 子实例：这个命令里调用它的父实例的推进（子实例马上就结束时直接回到它）；不是这样发起的为 null */
    Execution caller;
    /** 子实例被父实例连带终止：结束时不再回到父实例 */
    boolean quiet;

    /**
     * @param actor    这次的操作人；记进流转记录
     * @param definition 实例按的那个版本（图与网关配对）
     * @param instance 实例当前的样子
     * @param form     实例当前的表单值
     * @param rev      实例锁住后的版本：这一步的流转记录都带它，同一实例的记录按它排出准确的先后
     */
    Execution(EngineContext context, Actor actor, Instant now, LoadedDefinition definition, InstanceRecord instance, FormValues form, int rev) {
        this.context = context;
        this.assignees = new AssigneeResolver(context.stores().workItems(), context.org(), context.extensions());
        this.actor = actor;
        this.now = now;
        this.graph = definition.graph();
        this.pairs = definition.pairs();
        this.deadlines = new Deadlines(context.calendar(), definition.model().settings().calendar());
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

    /** 引擎内部共用的东西 */
    EngineContext context() { return context; }

    /** 实例按的那个版本的图 */
    ProcessGraph graph() { return graph; }

    /** 这一步的时刻 */
    Instant now() { return now; }

    /** 这次的操作人；引擎自己推进（作业）时是 {@link SystemActor} */
    Actor actor() { return actor; }

    /** 时限算成时刻 */
    Deadlines deadlines() { return deadlines; }

    /** 这一步的结果：实例、新生成与结束的待办、流转记录（{@link #flush} 之后调） */
    CommandResult result() { return new CommandResult(instance.view(), created, closed, events); }

    // ───────── 取值：{ ref } ─────────

    /** 按引用的名字取值（规则见 {@link References}）：用推进到此刻的表单与实例 */
    Object read(String name) { return References.read(name, instance, form); }

    // ───────── 推进 ─────────

    /**
     * 离开一个节点：按结果与条件选出线（Routing.selectEdges），每条线应用副作用、进入下一个节点。
     * 并行、包容的分叉会选出几条线：先记下配对的汇合要等几条分支，再逐条往下走。
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
        String join = pairs.joinOf(nodeId);
        if (join != null) instance = instance.withState(instance.state().fork(join, edges.size()));
        else if (edges.size() > 1) throw new IllegalStateException("node " + nodeId + " leaves on several edges but has no paired join");
        // 从开始节点出来的待办是"发起"来的，其余是"流转"来的（待办的 arrival）
        String arrival = graph.start().id().equals(nodeId) ? "start" : "forward";
        for (ProcessEdge edge : edges) {
            if (!ProcessInstance.ACTIVE.equals(instance.status())) return;   // 前一条分支走到了终止的结束节点
            addEvent(FlowEvent.NODE_LEFT, graph.node(nodeId), edge.id(), outcome, null, null);
            if (!edge.effects().isEmpty()) {
                Map<String, Object> changed = Routing.applyEffects(edge.effects(), instance.variables().asMap(), this::read);
                instance = instance.withVariables(Variables.of(changed));
            }
            if (edge.isBackward()) {
                leaveRegions(nodeId, edge.to());
                rememberResubmit(nodeId, edge);
            }
            enter(edge.to(), edge.isBackward() ? edge.kind() : arrival);
        }
    }

    /**
     * 处理完一个节点后往下走：这个节点是被退回来的、退回它的节点要求"重新提交后直接回来"（{@code returnTo: "resubmit"}）时，
     * 直接回到那个节点；否则照常 {@link #leave}。
     */
    void proceed(String nodeId, String outcome) {
        String back = instance.state().resubmitTo(nodeId);
        if (back == null) {
            leave(nodeId, outcome);
            return;
        }
        instance = instance.withState(instance.state().resubmitted(nodeId));
        addEvent(FlowEvent.NODE_LEFT, graph.node(nodeId), null, outcome, null, null);
        enter(back, "resubmit");
    }

    /**
     * 退回、提级的线跳出了并行区域（目标不在区域里）：区域里其余还开着的待办一起取消，汇合也不再等它们（0005 的 4.4）。
     * 由里到外看每一层区域。
     */
    private void leaveRegions(String from, String to) {
        for (String fork : pairs.forksAround(from)) {
            if (pairs.region(fork).contains(to)) return;
            for (WorkItem item : context.stores().workItems().cancelOpenAt(instance.id(), pairs.region(fork), now)) {
                closed.add(item);
                addEvent(FlowEvent.WORK_ITEM_CANCELED, graph.node(item.node()), null, null, item.id(), null);
            }
            // 区域里停在定时节点上的分支也不再等（作业到时候看到不在等了就跳过）
            for (String node : pairs.region(fork)) instance = instance.withState(instance.state().waitDone(node));
            instance = instance.withState(instance.state().forget(pairs.joinOf(fork)));
        }
    }

    /** 退回它的节点写了 {@code returnTo: "resubmit"}：记下来，被退回的节点重新提交后直接回来 */
    private void rememberResubmit(String from, ProcessEdge edge) {
        if ("return".equals(edge.kind()) && graph.node(from) instanceof ProcessNode.Task task && "resubmit".equals(task.returnTo())) {
            instance = instance.withState(instance.state().returned(edge.to(), from));
        }
    }

    /** 进入一个节点，按节点类型做它该做的事 */
    private void enter(String nodeId, String arrival) {
        if (++steps > MAX_STEPS) throw new IllegalStateException("the instance passed " + MAX_STEPS + " nodes in one step; the gateways loop");
        ProcessNode node = graph.node(nodeId);
        addEvent(FlowEvent.NODE_ENTERED, node, null, null, null, null);
        if (node instanceof ProcessNode.Task task) {
            arriveAtTask(task, arrival);
        } else if (node instanceof ProcessNode.Gateway) {
            arriveAtGateway(nodeId);
        } else if (node instanceof ProcessNode.Timer timer) {
            arriveAtTimer(timer);
        } else if (node instanceof ProcessNode.Cc cc) {
            arriveAtCc(cc);
        } else if (node instanceof ProcessNode.Service service) {
            arriveAtService(service);
        } else if (node instanceof ProcessNode.Receive receive) {
            arriveAtReceive(receive);
        } else if (node instanceof ProcessNode.Call call) {
            instance = instance.withState(instance.state().enter(call.id()));
            CallActivities.call(this, call);
        } else if (node instanceof ProcessNode.End end) {
            finish(ProcessInstance.COMPLETED, end.result(), FlowEvent.INSTANCE_FINISHED, null);
        } else {
            throw new UnsupportedOperationException("node type '" + node.type() + "' (" + nodeId + ") is not supported yet");
        }
    }

    /**
     * 到达网关：排他网关与分叉直接往下走；汇合（配对的分叉记下了要等几条）到了一条就少等一条，等齐了才往下走，
     * 没等齐时这条分支就停在这里。
     */
    private void arriveAtGateway(String nodeId) {
        if (pairs.forkOf(nodeId) != null) {
            boolean last = instance.state().waitingAt(nodeId) <= 1;
            instance = instance.withState(instance.state().arrive(nodeId));
            if (!last) return;
        }
        leave(nodeId, null);
    }

    /**
     * 到达定时节点：轮次加一，算出到什么时候（时长从现在起算，或到某个时刻），记在运行状态里、排一个作业，停在这里；
     * 到时候作业调 {@link #timerFired}。时刻已经过了、引用的字段没有值时直接往下走。
     */
    private void arriveAtTimer(ProcessNode.Timer timer) {
        instance = instance.withState(instance.state().enter(timer.id()));
        Instant until = deadlines.of(timer.waitFor(), now, this::read);
        if (until == null || !until.isAfter(now)) {
            leave(timer.id(), null);
            return;
        }
        instance = instance.withState(instance.state().waitUntil(timer.id(), until));
        Map<String, Object> payload = Map.of(BuiltinJobs.NODE, timer.id(), BuiltinJobs.ROUND, instance.state().round(timer.id()));
        context.stores().jobs().schedule(new NewJob(BuiltinJobs.TIMER, instance.id(), null, until, payload, BuiltinJobs.MAX_ATTEMPTS, now));
    }

    /** 定时节点到时候了：不再等，往下走 */
    void timerFired(String nodeId) {
        instance = instance.withState(instance.state().waitDone(nodeId));
        leave(nodeId, null);
    }

    /**
     * 到达自动节点（0005 的 4.1）：轮次加一。同步的（{@code mode: "sync"}）马上做动作、按结果往下走，失败整个流转回滚；
     * 异步的（默认）停在这里、排一个 action 作业，事务提交后由作业做（{@link #performAction}），失败按节点的 retry 重试。
     */
    private void arriveAtService(ProcessNode.Service service) {
        instance = instance.withState(instance.state().enter(service.id()));
        if ("sync".equals(service.mode())) {
            performAction(service);
            return;
        }
        instance = instance.withState(instance.state().waitUntil(service.id(), now));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(BuiltinJobs.NODE, service.id());
        payload.put(BuiltinJobs.ROUND, instance.state().round(service.id()));
        if (service.retryBackoff() != null) {
            payload.put(BuiltinJobs.BACKOFF_SECONDS, deadlines.plus(service.retryBackoff(), now).getEpochSecond() - now.getEpochSecond());
        }
        // retry.times 是失败后再试几次：一共做 times + 1 次
        int attempts = service.retryTimes() == null ? BuiltinJobs.MAX_ATTEMPTS : service.retryTimes() + 1;
        context.stores().jobs().schedule(new NewJob(BuiltinJobs.ACTION, instance.id(), null, now, payload, attempts, now));
    }

    /**
     * 做自动节点的动作（接入方注册的 ServiceAction），改它给的流程变量，按它给的结果往下走。
     * 去重键是"实例:节点:轮次"：同一轮重试时相同。动作抛的不是业务错误码的异常包成 {@code flow.extension-failed}。
     */
    void performAction(ProcessNode.Service service) {
        String key = instance.id() + ":" + service.id() + ":" + instance.state().round(service.id());
        ServiceAction.Context actionContext = new ServiceAction.Context(instance.view(), service.id(), RefValues.resolve(service.args(), this::read),
            key, this::read);
        ServiceAction.Result result;
        try {
            result = context.extensions().action(service.action()).perform(actionContext);
        } catch (FlowException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FlowException(FlowErrorCode.EXTENSION_FAILED, "action " + service.action() + " failed at " + service.id(),
                Map.of("kind", "action", "name", service.action()), List.of(), e);
        }
        if (!result.variables().isEmpty()) instance = instance.withVariables(instance.variables().with(result.variables()));
        instance = instance.withState(instance.state().waitDone(service.id()));
        leave(service.id(), result.outcome());
    }

    /**
     * 到达等待节点（0005 的 4.3）：轮次加一，停在这里等外部事件（{@code flow_wait} 一行，按事件名加实例或业务编号找到）；
     * 写了 {@code due} 时排一个超时作业。事件到了由 {@code SignalHandler} 调 {@link #received}。
     */
    private void arriveAtReceive(ProcessNode.Receive receive) {
        instance = instance.withState(instance.state().enter(receive.id()).waitUntil(receive.id(), now));
        context.stores().waits().insert(new NewWait(instance.id(), receive.id(), receive.event(), instance.businessKey(), now));
        Instant due = receive.due() == null ? null : deadlines.of(receive.due(), now, this::read);
        if (due == null) return;
        Map<String, Object> payload = Map.of(BuiltinJobs.NODE, receive.id(), BuiltinJobs.ROUND, instance.state().round(receive.id()));
        context.stores().jobs().schedule(new NewJob(BuiltinJobs.RECEIVE_TIMEOUT, instance.id(), null, due, payload, BuiltinJobs.MAX_ATTEMPTS, now));
    }

    /**
     * 外部事件到了（或等超时、按 overdue 的结果）：不再等；{@code output} 把事件数据里的值写进表单（{@code form.} 开头）
     * 或流程变量，按结果往下走。
     */
    void received(ProcessNode.Receive receive, String outcome, Map<String, Object> data) {
        Map<String, Object> formChanges = new LinkedHashMap<>();
        Map<String, Object> variableChanges = new LinkedHashMap<>();
        receive.output().forEach((target, key) -> {
            if (target.startsWith("form.")) formChanges.put(target.substring("form.".length()), data.get(key));
            else variableChanges.put(target, data.get(key));
        });
        if (!formChanges.isEmpty()) {
            Map<String, Object> merged = new LinkedHashMap<>(form.asMap());
            merged.putAll(formChanges);
            form = FormValues.of(merged);
            context.stores().formData().update(instance.id(), form);
        }
        if (!variableChanges.isEmpty()) instance = instance.withVariables(instance.variables().with(variableChanges));
        instance = instance.withState(instance.state().waitDone(receive.id()));
        leave(receive.id(), outcome);
    }

    /** 同步调用流程：停在调用节点上，等 count 个子实例结束（等的个数记在汇合的计数里，键是调用节点） */
    void waitForChildren(String node, int count) {
        instance = instance.withState(instance.state().fork(node, count).waitUntil(node, now));
    }

    /** 一个子实例结束了：少等一个，返回是不是最后一个 */
    boolean childArrived(String node) {
        boolean last = instance.state().waitingAt(node) <= 1;
        instance = instance.withState(instance.state().arrive(node));
        return last;
    }

    /** 子实例都结束了：output 收进流程变量，按结果往下走 */
    void callReturned(ProcessNode.Call call, String outcome, Map<String, Object> variables) {
        if (!variables.isEmpty()) instance = instance.withVariables(instance.variables().with(variables));
        instance = instance.withState(instance.state().waitDone(call.id()));
        leave(call.id(), outcome);
    }

    /** 到达抄送节点：抄送给节点上写的人（角色、部门展开成人），不停，接着往下走 */
    private void arriveAtCc(ProcessNode.Cc cc) {
        List<Long> users = assignees.people(cc.to(), instance, this::read).stream().map(AssigneeResolver.Participant::user).toList();
        copyTo(cc, users, cc.message());
        leave(cc.id(), null);
    }

    /**
     * 抄送给这些人（同一个人只一条）：每人一条抄送（"抄送我的"里看到），记一条流转记录。
     *
     * @param node 抄送节点；手动抄送时为 null（抄送人记在抄送上）
     */
    void copyTo(ProcessNode.Cc node, List<Long> users, String message) {
        List<Long> recipients = users.stream().distinct().toList();
        if (recipients.isEmpty()) return;
        Long fromId = node == null ? actor.id() : null;
        String fromName = node == null ? actor.name() : null;
        List<String> names = recipients.stream().map(context.org()::userName).toList();
        List<NewCc> rows = new ArrayList<>();
        for (int i = 0; i < recipients.size(); i++) {
            rows.add(new NewCc(instance.id(), node == null ? null : node.id(), node == null ? null : node.name(), recipients.get(i),
                names.get(i), fromId, fromName, message, now));
        }
        context.stores().ccs().insert(rows);
        addEvent(FlowEvent.INSTANCE_CC, node, null, null, null, message, Map.of("users", recipients, "userNames", names));
    }

    /**
     * 到达人工节点：轮次加一，解析处理人，生成一个待办。只解析出一个人、没有收件箱时直接落到他；
     * 否则进收件箱（{@code pool}），人和角色、部门都存成候选，谁先认领谁办。
     */
    private void arriveAtTask(ProcessNode.Task task, String arrival) {
        instance = instance.withState(instance.state().enter(task.id()));
        if (task.multi() != null) {
            arriveAtCountersign(task, arrival);
            return;
        }
        Assignees who = assigneesFor(task, arrival);
        if (who.isEmpty()) {
            handleEmptyAssignee(task);
            return;
        }
        createWorkItem(task, arrival, who, null, null);
    }

    /**
     * 到达会签节点：每个参与人一份待办（直接落到人）；依次会签只给第一个人，其余排队（{@link RunState#queue}），
     * 前一个人同意后轮到下一个（{@link #nextSigner}）。
     */
    private void arriveAtCountersign(ProcessNode.Task task, String arrival) {
        List<AssigneeResolver.Participant> participants = assignees.participants(task, instance, this::read);
        if (participants.isEmpty()) {
            handleEmptyAssignee(task);
            return;
        }
        boolean sequential = "sequential".equals(task.multi().complete());
        boolean weighted = "votes".equals(task.multi().complete());
        List<AssigneeResolver.Participant> first = sequential ? participants.subList(0, 1) : participants;
        for (int i = 0; i < first.size(); i++) {
            AssigneeResolver.Participant p = first.get(i);
            createWorkItem(task, arrival, Assignees.of(p.user()), i + 1, weighted ? p.weight() : null);
        }
        if (sequential) {
            List<Long> rest = participants.subList(1, participants.size()).stream().map(AssigneeResolver.Participant::user).toList();
            instance = instance.withState(instance.state().queue(task.id(), rest));
        }
    }

    /** 依次会签：轮到排队的下一个人（前一个人同意了） */
    void nextSigner(ProcessNode.Task task, int seq) {
        List<Long> queue = instance.state().queue(task.id());
        instance = instance.withState(instance.state().queue(task.id(), queue.subList(1, queue.size())));
        createWorkItem(task, "forward", Assignees.of(queue.get(0)), seq, null);
    }

    /** 会签节点结束了：其余还开着的待办取消，排队的人也不用再等 */
    void closeCountersign(ProcessNode.Task task) {
        instance = instance.withState(instance.state().queue(task.id(), List.of()));
        for (WorkItem item : context.stores().workItems().cancelOpenAt(instance.id(), List.of(task.id()), now)) {
            closed.add(item);
            addEvent(FlowEvent.WORK_ITEM_CANCELED, task, null, null, item.id(), null);
        }
    }

    /**
     * 待办从哪来：转办、委派、加签的人与原来的待办。流转生成的待办是 {@link #NONE}。
     *
     * @param fromUserId   转办、委派、加签的人
     * @param fromUserName 那个人的名字（快照）
     * @param parentItemId 委派、加签时原来的待办；转办时为 null（原来的那一份取消了）
     * @param addMode      {@code delegate}、{@code before}、{@code after}、{@code parallel}；转办时为 null
     */
    record Origin(Long fromUserId, String fromUserName, Long parentItemId, String addMode) {
        static final Origin NONE = new Origin(null, null, null, null);
    }

    /**
     * 生成一个待办：只有一个人、没有收件箱时直接落到他；否则进收件箱（{@code pool}），人和角色、部门都存成候选，谁先认领谁办。
     *
     * @param signSeq 会签时是第几个参与人；不是会签时为 null
     * @param weight  票签的权重；不是票签时为 null
     */
    private WorkItem createWorkItem(ProcessNode.Task task, String arrival, Assignees who, Integer signSeq, Integer weight) {
        Instant due = task.due() == null ? null : deadlines.of(task.due(), now, this::read);
        return createWorkItem(task, arrival, who, signSeq, weight, Origin.NONE, WorkItem.OPEN, due);
    }

    /**
     * 同上，带上从哪来；state 是 {@code open}，或后加签时先生成、原来的人处理完才打开的 {@code waiting}。
     *
     * @param dueAt 截止时间：流转来的按节点的时限算，转办、委派、加签的沿用原来那一份的；没有时限时为 null
     */
    private WorkItem createWorkItem(ProcessNode.Task task, String arrival, Assignees who, Integer signSeq, Integer weight,
                                    Origin origin, String state, Instant dueAt) {
        Long assigneeId = who.isDirect() ? who.directUser() : null;
        String assigneeName = assigneeId == null ? null : context.org().userName(assigneeId);
        String assignment = assigneeId != null ? WorkItem.DIRECT : WorkItem.POOL;
        Candidate inbox = who.firstInbox();
        int round = instance.state().round(task.id());

        WorkItem draft = new WorkItem(null, instance.id(), instance.processKey(), instance.priority(), task.id(), task.name(), round,
            signSeq, weight, state, assignment, arrival, inbox == null ? null : inbox.kind(), inbox == null ? null : inbox.targetId(),
            assigneeId, assigneeName, origin.fromUserId(), origin.fromUserName(), origin.parentItemId(), origin.addMode(), now,
            null, dueAt, null, null, null, null, null);   // 认领时间、处理结果、意见、处理人、处理人名字、处理时间：都还没有
        WorkItem item = context.stores().workItems().insert(draft, assigneeId != null ? List.of() : who.candidates());
        created.add(item);
        scheduleTimeouts(task, item);
        // 直接落到人的记下名字快照：流转记录上显示"到达 某节点 · 处理人 张三"；在收件箱里的没有
        addEvent(FlowEvent.WORK_ITEM_CREATED, task, null, null, item.id(), null,
            assigneeName == null ? Map.of() : Map.of("assigneeName", assigneeName));
        return item;
    }

    /**
     * 把一个待办交给别人（转办、委派、加签）：直接落到这个人，会签的序号与权重照抄原来的那一份。
     *
     * @param from    原来的待办
     * @param arrival {@code transfer}、{@code delegate}、{@code addSign}
     * @param addMode 委派、加签的方式；转办时为 null（新的待办不挂在原来的下面）
     * @param state   {@code open}，或后加签时的 {@code waiting}
     */
    WorkItem handOver(WorkItem from, Long userId, String arrival, String addMode, String state) {
        ProcessNode.Task task = (ProcessNode.Task) graph.node(from.node());
        Origin origin = new Origin(actor.id(), actor.name(), addMode == null ? null : from.id(), addMode);
        return createWorkItem(task, arrival, Assignees.of(userId), from.signSeq(), from.weight(), origin, state, from.dueAt());
    }

    /**
     * 待办的超时、提醒、无人认领（0005 的 5.3）各排一个作业：有截止时间时到期那一刻一个超时作业（超时怎么办见节点的 overdue）、
     * 每个提醒一个（到期前的；超时后每隔一段时间的从到期起算）；进了收件箱、写了 unclaimed 时到点看还有没有人认领。
     * 待办先办完了也不用删：作业到时候看到待办不再打开就跳过（BuiltinJobs）。
     */
    private void scheduleTimeouts(ProcessNode.Task task, WorkItem item) {
        JobStore jobs = context.stores().jobs();
        if (item.dueAt() != null) {
            jobs.schedule(itemJob(BuiltinJobs.OVERDUE, item, item.dueAt(), Map.of()));
            for (int i = 0; i < task.reminders().size(); i++) {
                TaskRules.Reminder reminder = task.reminders().get(i);
                Instant at = reminder.before() != null ? deadlines.minus(reminder.before(), item.dueAt()) : deadlines.plus(reminder.every(), item.dueAt());
                if (at.isAfter(now)) jobs.schedule(itemJob(BuiltinJobs.REMIND, item, at, Map.of(BuiltinJobs.REMINDER, i, BuiltinJobs.SENT, 0)));
            }
        }
        if (task.unclaimed() != null && item.assigneeId() == null) {
            jobs.schedule(itemJob(BuiltinJobs.UNCLAIMED, item, deadlines.plus(task.unclaimed().after(), now), Map.of()));
        }
    }

    private NewJob itemJob(String kind, WorkItem item, Instant at, Map<String, Object> payload) {
        return new NewJob(kind, instance.id(), item.id(), at, payload, BuiltinJobs.MAX_ATTEMPTS, now);
    }

    /**
     * 这次到达人工节点由谁处理：退回来的、重新提交直接回来的，落到这个节点原来的处理人（没人处理过时照常解析）；提级来的只进收件箱，
     * 不直接落到人（第 6 节连线的 kind）；其余照节点上写的来源解析。
     */
    private Assignees assigneesFor(ProcessNode.Task task, String arrival) {
        if ("return".equals(arrival) || "resubmit".equals(arrival)) {
            Long last = context.stores().workItems().lastCompletedBy(instance.id(), task.id()).orElse(null);
            if (last != null) return Assignees.of(last);
        }
        Assignees who = assignees.resolve(task, graph, instance, this::read);
        return "escalate".equals(arrival) && !who.inboxes().isEmpty() ? who.inboxesOnly() : who;
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

    /** 换实例的状态（暂停、恢复），记一条流转记录 */
    void changeStatus(String status, String eventType, String comment) {
        instance = instance.withStatus(status);
        addEvent(eventType, null, null, null, null, comment);
    }

    /** 管理员跳转：还没办完的待办都取消，等待中的汇合、队列都不再记，从这个节点重新走 */
    void jumpTo(String nodeId, String reason) {
        for (WorkItem item : context.stores().workItems().cancelOpen(instance.id(), now)) {
            closed.add(item);
            addEvent(FlowEvent.WORK_ITEM_CANCELED, graph.node(item.node()), null, null, item.id(), null);
        }
        instance = instance.withState(instance.state().withoutPending());
        addEvent(FlowEvent.INSTANCE_JUMPED, graph.node(nodeId), null, null, null, reason);
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
    void finish(String status, String result, String eventType, String comment) {
        for (WorkItem item : context.stores().workItems().cancelOpen(instance.id(), now)) {
            closed.add(item);
            addEvent(FlowEvent.WORK_ITEM_CANCELED, graph.node(item.node()), null, null, item.id(), null);
        }
        instance = instance.withEnd(status, result, now);
        context.stores().jobs().cancelForInstance(instance.id());
        context.stores().waits().removeForInstance(instance.id());
        addEvent(eventType, null, null, result, null, comment);
        // 调用流程：终止、撤回时子实例一起终止；子实例结束了回到父实例（0005 的 4.2）
        if (ProcessInstance.TERMINATED.equals(status) || ProcessInstance.WITHDRAWN.equals(status)) CallActivities.terminateChildren(this, comment);
        if (instance.parentInstanceId() != null && !quiet) CallActivities.childEnded(this);
    }

    /** 记下一条流转记录（先放在 {@link #events} 里，{@link #flush} 时一起写库）；同一步的记录共用实例的版本 */
    void addEvent(String type, ProcessNode node, String edge, String outcome, Long workItemId, String comment) {
        addEvent(type, node, edge, outcome, workItemId, comment, Map.of());
    }

    /** 同上，带附加的数据（处理结果的名字这类快照） */
    void addEvent(String type, ProcessNode node, String edge, String outcome, Long workItemId, String comment, Map<String, Object> data) {
        String nodeId = node == null ? null : node.id();
        String nodeName = node == null ? null : node.name();
        // 附加数据是用户的 JSON：数字按 JavaScript 的数（同存进库再读出来的样子，这一步返回的与以后查到的一致）
        @SuppressWarnings("unchecked")
        Map<String, Object> json = (Map<String, Object>) JsonNumbers.normalize(data);
        events.add(new FlowEvent(null, instance.id(), workItemId, type, nodeId, nodeName, edge, outcome, rev, actor.id(), actor.name(), now,
            comment, json));
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
        publish();
    }

    /**
     * 只写流转记录（不改实例的命令：认领、催办、留言、抄送）：换上数据库给的编号，交给监听（事务内的马上调，提交后的记下）。
     */
    void publish() {
        List<FlowEvent> saved = context.stores().events().append(events);
        events.clear();
        events.addAll(saved);
        context.dispatcher().published(saved, instance.view(), graph);
    }
}
