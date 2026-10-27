package org.dpdns.zerodep.flow.core.engine;

import lombok.Data;
import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.RunState;
import org.dpdns.zerodep.flow.core.data.Variables;
import org.dpdns.zerodep.flow.core.engine.assignee.AssigneeResolver;
import org.dpdns.zerodep.flow.core.engine.assignee.Assignees;
import org.dpdns.zerodep.flow.core.engine.form.RefValues;
import org.dpdns.zerodep.flow.core.engine.form.References;
import org.dpdns.zerodep.flow.core.engine.job.BuiltinJobs;
import org.dpdns.zerodep.flow.core.engine.job.JobRunner;
import org.dpdns.zerodep.flow.core.engine.notify.Notifications;
import org.dpdns.zerodep.flow.core.engine.rule.CallActivities;
import org.dpdns.zerodep.flow.core.engine.rule.Deadlines;
import org.dpdns.zerodep.flow.core.engine.rule.InstanceDisplay;
import org.dpdns.zerodep.flow.core.engine.store.Instances;
import org.dpdns.zerodep.flow.core.engine.store.WorkItems;
import org.dpdns.zerodep.flow.core.entity.CandidateEntity;
import org.dpdns.zerodep.flow.core.entity.CcEntity;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.entity.JobEntity;
import org.dpdns.zerodep.flow.core.entity.StepEntity;
import org.dpdns.zerodep.flow.core.entity.WaitEntity;
import org.dpdns.zerodep.flow.core.entity.WorkItemEntity;
import org.dpdns.zerodep.flow.core.extension.ServiceAction;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.identity.OrgDirectory;
import org.dpdns.zerodep.flow.core.notify.Notification;
import org.dpdns.zerodep.flow.core.view.instance.FlowEventVO;
import org.dpdns.zerodep.flow.core.view.instance.InstanceVO;
import org.dpdns.zerodep.flow.core.view.workitem.WorkItemVO;
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
import org.dpdns.zerodep.flow.model.form.FormManifest;

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
public class Execution {
    /** 一次推进最多经过多少个节点：网关绕成圈、条件永远成立时停下来报错，而不是一直走 */
    private static final int MAX_STEPS = 1000;

    private final EngineContext context;
    private final AssigneeResolver assignees;
    private final Actor actor;
    private final Instant now;
    private final ProcessGraph graph;
    private final GatewayPairs pairs;
    /** 表单的字段清单；流程没有表单时为 null */
    private final FormManifest manifest;
    private final Deadlines deadlines;
    private final int rev;
    private InstanceEntity instance;
    private FormValues form;
    private int steps;

    /** 这次新生成的待办 */
    public final List<WorkItemVO> created = new ArrayList<>();
    /** 这次结束的待办（处理完的、取消的），是结束前的样子 */
    public final List<WorkItemVO> closed = new ArrayList<>();
    /** 这次的流转记录，{@link #publish} 时写库并读回（带上数据库给的编号） */
    final List<FlowEventVO> events = new ArrayList<>();
    /** 还没写库的流转记录 */
    private final List<EventEntity> pending = new ArrayList<>();

    /** 子实例：这个命令里调用它的父实例的推进（子实例马上就结束时直接回到它）；不是这样发起的为 null */
    public Execution caller;
    /** 子实例被父实例连带终止：结束时不再回到父实例 */
    public boolean quiet;
    /** 这一步的处理人选的下一步处理人（0005 的 5.6）：节点 → 人 */
    private Map<String, List<Long>> picks = Map.of();
    /** 这次处理退回到哪个节点（处理结果 {@code target: "choose"} 时） */
    private String returnTarget;

    /**
     * @param actor    这次的操作人；记进流转记录
     * @param definition 实例按的那个版本（图与网关配对）
     * @param instance 实例当前的样子
     * @param form     实例当前的表单值
     * @param rev      实例锁住后的版本：这一步的流转记录都带它，同一实例的记录按它排出准确的先后
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

    /** 实例现在的样子（推进时就地改它，{@link #flush} 时整行写回） */
    public InstanceEntity instance() { return instance; }

    /** 实例给前端、监听的样子（从库里读，{@link #flush} 之后才是这一步之后的） */
    public InstanceVO view() { return Instances.view(context.getDb(), instance.getId()); }

    /** 表单现在的值 */
    public FormValues form() { return form; }

    /** 换上新的表单值（处理类收了表单之后） */
    public void form(FormValues next) { this.form = next; }

    /** 这一步的处理人选的下一步处理人：节点 → 人 */
    public void picks(Map<String, List<Long>> next) { this.picks = next; }

    /** 退回时处理人选的节点（处理结果 {@code target: "choose"}，已经核对过是能选的） */
    public void returnTarget(String node) { this.returnTarget = node; }

    /** 引擎内部共用的东西 */
    public EngineContext context() { return context; }

    /** 实例按的那个版本的图 */
    public ProcessGraph graph() { return graph; }

    /** 这一步的时刻 */
    public Instant now() { return now; }

    /** 这次的操作人；引擎自己推进（作业）时是 {@link SystemActor} */
    public Actor actor() { return actor; }

    /** 时限算成时刻 */
    public Deadlines deadlines() { return deadlines; }

    /** 这一步的结果：实例、新生成与结束的待办、流转记录（{@link #flush} 之后调） */
    public CommandResult result() { return new CommandResult(view(), created, closed, events); }

    // ───────── 取值：{ ref } ─────────

    /** 按引用的名字取值（规则见 {@link References}）：用推进到此刻的表单与实例 */
    public Object read(String name) { return References.read(name, instance, form); }

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
        if (join != null) instance.setState(instance.getState().fork(join, edges.size()));
        else if (edges.size() > 1) throw new IllegalStateException("node " + nodeId + " leaves on several edges but has no paired join");
        // 从开始节点出来的待办是"发起"来的，其余是"流转"来的（待办的 arrival）
        String arrival = graph.start().getId().equals(nodeId) ? "start" : "forward";
        for (ProcessEdge edge : edges) {
            if (!InstanceEntity.ACTIVE.equals(instance.getStatus())) return;   // 前一条分支走到了终止的结束节点
            addEvent(EventEntity.NODE_LEFT, graph.node(nodeId), edge.getId(), outcome, null, null);
            if (!edge.getEffects().isEmpty()) {
                Map<String, Object> changed = Routing.applyEffects(edge.getEffects(), instance.getVariables().asMap(), this::read);
                instance.setVariables(Variables.of(changed));
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
        instance.setState(instance.getState().resubmitted(nodeId));
        addEvent(EventEntity.NODE_LEFT, graph.node(nodeId), null, outcome, null, null);
        enter(back, "resubmit");
    }

    /**
     * 退回、提级的线跳出了并行区域（目标不在区域里）：区域里其余还开着的待办一起取消，汇合也不再等它们（0005 的 4.4）。
     * 由里到外看每一层区域。
     */
    private void leaveRegions(String from, String to) {
        for (String fork : pairs.forksAround(from)) {
            if (pairs.region(fork).contains(to)) return;
            for (WorkItemVO item : WorkItems.cancelOpenAt(context.getDb(), instance.getId(), pairs.region(fork), now)) {
                closed.add(item);
                addEvent(EventEntity.WORK_ITEM_CANCELED, graph.node(item.getNode()), null, null, item.getId(), null);
            }
            // 区域里停在定时节点上的分支也不再等（作业到时候看到不在等了就跳过）
            for (String node : pairs.region(fork)) instance.setState(instance.getState().waitDone(node));
            instance.setState(instance.getState().forget(pairs.joinOf(fork)));
        }
    }

    /** 退回它的节点写了 {@code returnTo: "resubmit"}：记下来，被退回的节点重新提交后直接回来 */
    private void rememberResubmit(String from, String to, String kind) {
        if ("return".equals(kind) && graph.node(from) instanceof ProcessNode.Task task && "resubmit".equals(task.getReturnTo())) {
            instance.setState(instance.getState().returned(to, from));
        }
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

    /** 进入一个节点，按节点类型做它该做的事 */
    private void enter(String nodeId, String arrival) {
        if (++steps > MAX_STEPS) throw new IllegalStateException("the instance passed " + MAX_STEPS + " nodes in one step; the gateways loop");
        ProcessNode node = graph.node(nodeId);
        addEvent(EventEntity.NODE_ENTERED, node, null, null, null, null);
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
            instance.setState(instance.getState().enter(call.getId()));
            CallActivities.call(this, call);
        } else if (node instanceof ProcessNode.End end) {
            finish(InstanceEntity.COMPLETED, end.getResult(), EventEntity.INSTANCE_FINISHED, null);
        } else {
            throw new UnsupportedOperationException("node type '" + node.getType() + "' (" + nodeId + ") is not supported yet");
        }
    }

    /**
     * 到达网关：排他网关与分叉直接往下走；汇合（配对的分叉记下了要等几条）到了一条就少等一条，等齐了才往下走，
     * 没等齐时这条分支就停在这里。
     */
    private void arriveAtGateway(String nodeId) {
        if (pairs.forkOf(nodeId) != null) {
            boolean last = instance.getState().waitingAt(nodeId) <= 1;
            instance.setState(instance.getState().arrive(nodeId));
            if (!last) return;
        }
        leave(nodeId, null);
    }

    /**
     * 到达定时节点：轮次加一，算出到什么时候（时长从现在起算，或到某个时刻），记在运行状态里、排一个作业，停在这里；
     * 到时候作业调 {@link #timerFired}。时刻已经过了、引用的字段没有值时直接往下走。
     */
    private void arriveAtTimer(ProcessNode.Timer timer) {
        instance.setState(instance.getState().enter(timer.getId()));
        Instant until = deadlines.of(timer.getWaitFor(), now, this::read);
        if (until == null || !until.isAfter(now)) {
            leave(timer.getId(), null);
            return;
        }
        instance.setState(instance.getState().waitUntil(timer.getId(), until));
        Map<String, Object> payload = Map.of(BuiltinJobs.NODE, timer.getId(), BuiltinJobs.ROUND, instance.getState().round(timer.getId()));
        schedule(BuiltinJobs.TIMER, null, until, payload, BuiltinJobs.MAX_ATTEMPTS);
    }

    /** 定时节点到时候了：不再等，往下走 */
    public void timerFired(String nodeId) {
        instance.setState(instance.getState().waitDone(nodeId));
        leave(nodeId, null);
    }

    /**
     * 到达自动节点（0005 的 4.1）：轮次加一。同步的（{@code mode: "sync"}）马上做动作、按结果往下走，失败整个流转回滚；
     * 异步的（默认）停在这里、排一个 action 作业，事务提交后由作业做（{@link #performAction}），失败按节点的 retry 重试。
     */
    private void arriveAtService(ProcessNode.Service service) {
        instance.setState(instance.getState().enter(service.getId()));
        if ("sync".equals(service.getMode())) {
            performAction(service);
            return;
        }
        instance.setState(instance.getState().waitUntil(service.getId(), now));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(BuiltinJobs.NODE, service.getId());
        payload.put(BuiltinJobs.ROUND, instance.getState().round(service.getId()));
        if (service.getRetryBackoff() != null) {
            payload.put(BuiltinJobs.BACKOFF_SECONDS, deadlines.plus(service.getRetryBackoff(), now).getEpochSecond() - now.getEpochSecond());
        }
        // retry.times 是失败后再试几次：一共做 times + 1 次
        int attempts = service.getRetryTimes() == null ? BuiltinJobs.MAX_ATTEMPTS : service.getRetryTimes() + 1;
        schedule(BuiltinJobs.ACTION, null, now, payload, attempts);
    }

    /**
     * 做自动节点的动作（接入方注册的 ServiceAction），改它给的流程变量，按它给的结果往下走。
     * 去重键是"实例:节点:轮次"：同一轮重试时相同。动作抛的不是业务错误码的异常包成 {@code flow.extension-failed}。
     */
    public void performAction(ProcessNode.Service service) {
        String key = instance.getId() + ":" + service.getId() + ":" + instance.getState().round(service.getId());
        ServiceAction.Context actionContext = new ServiceAction.Context(view(), service.getId(), RefValues.resolve(service.getArgs(), this::read),
            key, this::read);
        ServiceAction.Result result;
        try {
            result = context.getExtensions().action(service.getAction()).perform(actionContext);
        } catch (FlowException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FlowException(FlowErrorCode.EXTENSION_FAILED, "action " + service.getAction() + " failed at " + service.getId(),
                Map.of("kind", "action", "name", service.getAction()), List.of(), e);
        }
        if (!result.getVariables().isEmpty()) instance.setVariables(instance.getVariables().with(result.getVariables()));
        instance.setState(instance.getState().waitDone(service.getId()));
        leave(service.getId(), result.getOutcome());
    }

    /** 管理员跳过失败的动作（{@code skipJob}）：记一条，不再等，按指定的结果往下走 */
    public void skipAction(ProcessNode.Service service, Long jobId, String outcome, String reason) {
        addEvent(EventEntity.JOB_SKIPPED, service, null, outcome, null, reason, Map.of("jobId", jobId));
        instance.setState(instance.getState().waitDone(service.getId()));
        leave(service.getId(), outcome);
    }

    /**
     * 到达等待节点（0005 的 4.3）：轮次加一，停在这里等外部事件（{@code flow_wait} 一行，按事件名加实例或业务编号找到）；
     * 写了 {@code due} 时排一个超时作业。事件到了由 {@code SignalHandler} 调 {@link #received}。
     */
    private void arriveAtReceive(ProcessNode.Receive receive) {
        instance.setState(instance.getState().enter(receive.getId()).waitUntil(receive.getId(), now));
        WaitEntity wait = new WaitEntity();
        wait.setInstanceId(instance.getId());
        wait.setNode(receive.getId());
        wait.setEvent(receive.getEvent());
        wait.setBusinessKey(instance.getBusinessKey());
        wait.setCreatedAt(now);
        context.getDb().insertable(wait).executeRows();
        Instant due = receive.getDue() == null ? null : deadlines.of(receive.getDue(), now, this::read);
        if (due == null) return;
        Map<String, Object> payload = Map.of(BuiltinJobs.NODE, receive.getId(), BuiltinJobs.ROUND, instance.getState().round(receive.getId()));
        schedule(BuiltinJobs.RECEIVE_TIMEOUT, null, due, payload, BuiltinJobs.MAX_ATTEMPTS);
    }

    /**
     * 外部事件到了（或等超时、按 overdue 的结果）：不再等；{@code output} 把事件数据里的值写进表单（{@code form.} 开头）
     * 或流程变量，按结果往下走。
     */
    public void received(ProcessNode.Receive receive, String outcome, Map<String, Object> data) {
        Map<String, Object> formChanges = new LinkedHashMap<>();
        Map<String, Object> variableChanges = new LinkedHashMap<>();
        receive.getOutput().forEach((target, key) -> {
            if (target.startsWith("form.")) formChanges.put(target.substring("form.".length()), data.get(key));
            else variableChanges.put(target, data.get(key));
        });
        if (!formChanges.isEmpty()) {
            Map<String, Object> merged = new LinkedHashMap<>(form.asMap());
            // 业务系统送来的数据未必标了类型：按清单的字段类型读成强类型
            merged.putAll(manifest == null ? formChanges : manifest.typed(formChanges));
            form = FormValues.of(merged);
            Instances.saveForm(context.getDb(), instance.getId(), form);
        }
        if (!variableChanges.isEmpty()) instance.setVariables(instance.getVariables().with(variableChanges));
        instance.setState(instance.getState().waitDone(receive.getId()));
        leave(receive.getId(), outcome);
    }

    /** 同步调用流程：停在调用节点上，等 count 个子实例结束（等的个数记在汇合的计数里，键是调用节点） */
    public void waitForChildren(String node, int count) {
        instance.setState(instance.getState().fork(node, count).waitUntil(node, now));
    }

    /** 一个子实例结束了：少等一个，返回是不是最后一个 */
    public boolean childArrived(String node) {
        boolean last = instance.getState().waitingAt(node) <= 1;
        instance.setState(instance.getState().arrive(node));
        return last;
    }

    /** 子实例都结束了：output 收进流程变量，按结果往下走 */
    public void callReturned(ProcessNode.Call call, String outcome, Map<String, Object> variables) {
        if (!variables.isEmpty()) instance.setVariables(instance.getVariables().with(variables));
        instance.setState(instance.getState().waitDone(call.getId()));
        leave(call.getId(), outcome);
    }

    /** 到达抄送节点：抄送给节点上写的人（角色、部门展开成人），不停，接着往下走 */
    private void arriveAtCc(ProcessNode.Cc cc) {
        List<Long> users = assignees.people(cc.getTo(), instance, this::read).stream().map(AssigneeResolver.Participant::getUser).toList();
        copyTo(cc, users, cc.getMessage());
        leave(cc.getId(), null);
    }

    /**
     * 抄送给这些人（同一个人只一条）：每人一条抄送（"抄送我的"里看到），记一条流转记录。
     *
     * @param node 抄送节点；手动抄送时为 null（抄送人记在抄送上）
     */
    public void copyTo(ProcessNode.Cc node, List<Long> users, String message) {
        List<Long> recipients = users.stream().distinct().toList();
        if (recipients.isEmpty()) return;
        Long fromId = node == null ? actor.getId() : null;
        String fromName = node == null ? actor.getName() : null;
        List<String> names = recipients.stream().map(context.getOrg()::userName).toList();
        List<CcEntity> rows = new ArrayList<>();
        for (int i = 0; i < recipients.size(); i++) {
            CcEntity cc = new CcEntity();
            cc.setInstanceId(instance.getId());
            cc.setNode(node == null ? null : node.getId());
            cc.setNodeName(node == null ? null : node.getName());
            cc.setRecipientId(recipients.get(i));
            cc.setRecipientName(names.get(i));
            cc.setFromUserId(fromId);
            cc.setFromUserName(fromName);
            cc.setMessage(message);
            cc.setCreatedAt(now);
            rows.add(cc);
        }
        context.getDb().insertable(rows).executeRows();
        addEvent(EventEntity.INSTANCE_CC, node, null, null, null, message, Map.of("users", recipients, "userNames", names));
    }

    /**
     * 到达人工节点：轮次加一，解析处理人，生成一个待办。只解析出一个人、没有收件箱时直接落到他；
     * 否则进收件箱（{@code pool}），人和角色、部门都存成候选，谁先认领谁办。
     */
    private void arriveAtTask(ProcessNode.Task task, String arrival) {
        instance.setState(instance.getState().enter(task.getId()));
        if (task.getMulti() != null) {
            arriveAtCountersign(task, arrival);
            return;
        }
        Assignees who = assigneesFor(task, arrival);
        if (who.isEmpty()) {
            handleEmptyAssignee(task);
            return;
        }
        String skip = skipReason(task, arrival, who);
        if (skip != null) {
            Long user = who.directUser();
            addEvent(EventEntity.NODE_SKIPPED, task, null, approveOutcome(task), null, null,
                Map.of("reason", skip, "userId", user, "userName", context.getOrg().userName(user)));
            leave(task.getId(), approveOutcome(task));
            return;
        }
        createWorkItem(task, arrival, who, null, null);
    }

    /**
     * 自动跳过（0005 的 2.1 {@code autoSkip}，节点上写的盖过流程级的）：顺着流转到达、直接落到一个人、节点有"同意"的结果时，
     * 这个人就是上一步的处理人（{@code sameAsPrevious}）或发起人（{@code initiator}）就不生成待办，按"同意"往下走。
     * 退回、重新提交、提级、跳转来的不跳过：那是要这个人再看一次。会签节点不跳过。返回跳过的原因，不跳过时为 null。
     */
    private String skipReason(ProcessNode.Task task, String arrival, Assignees who) {
        if (!("start".equals(arrival) || "forward".equals(arrival)) || !who.isDirect() || approveOutcome(task) == null) return null;
        Map<String, Object> rules = task.getInfo().getAutoSkip().isEmpty() ? graph.definition().getSettings().getAutoSkip() : task.getInfo().getAutoSkip();
        Long user = who.directUser();
        if (Boolean.TRUE.equals(rules.get("initiator")) && user.equals(instance.getStartedBy())) return "initiator";
        if (Boolean.TRUE.equals(rules.get("sameAsPrevious")) && user.equals(previousHandler())) return "sameAsPrevious";
        return null;
    }

    /** 上一步的处理人：这个实例最近处理完的待办的处理人；还没人处理过时是发起人（发起算第一步） */
    private Long previousHandler() {
        Long last = WorkItems.lastCompletedBy(context.getDb(), instance.getId(), null);
        return last != null ? last : instance.getStartedBy();
    }

    /** 节点上第一个"同意"类的结果；没有时为 null */
    private static String approveOutcome(ProcessNode.Task task) {
        return task.getOutcomes().stream().filter(o -> "approve".equals(o.kindOrDefault())).map(Outcome::getKey).findFirst().orElse(null);
    }

    /**
     * 到达会签节点：每个参与人一份待办（直接落到人）；依次会签只给第一个人，其余排队（{@link RunState#queue}），
     * 前一个人同意后轮到下一个（{@link #nextSigner}）。
     */
    private void arriveAtCountersign(ProcessNode.Task task, String arrival) {
        List<AssigneeResolver.Participant> participants = assignees.participants(task, instance, this::read, picks);
        if (participants.isEmpty()) {
            handleEmptyAssignee(task);
            return;
        }
        boolean sequential = "sequential".equals(task.getMulti().getComplete());
        boolean weighted = "votes".equals(task.getMulti().getComplete());
        List<AssigneeResolver.Participant> first = sequential ? participants.subList(0, 1) : participants;
        for (int i = 0; i < first.size(); i++) {
            AssigneeResolver.Participant p = first.get(i);
            createWorkItem(task, arrival, Assignees.of(p.getUser()), i + 1, weighted ? p.getWeight() : null);
        }
        if (sequential) {
            List<Long> rest = participants.subList(1, participants.size()).stream().map(AssigneeResolver.Participant::getUser).toList();
            instance.setState(instance.getState().queue(task.getId(), rest));
        }
    }

    /** 依次会签：轮到排队的下一个人（前一个人同意了） */
    public void nextSigner(ProcessNode.Task task, int seq) {
        List<Long> queue = instance.getState().queue(task.getId());
        instance.setState(instance.getState().queue(task.getId(), queue.subList(1, queue.size())));
        createWorkItem(task, "forward", Assignees.of(queue.get(0)), seq, null);
    }

    /** 会签节点结束了：其余还开着的待办取消，排队的人也不用再等 */
    public void closeCountersign(ProcessNode.Task task) {
        instance.setState(instance.getState().queue(task.getId(), List.of()));
        for (WorkItemVO item : WorkItems.cancelOpenAt(context.getDb(), instance.getId(), List.of(task.getId()), now)) {
            closed.add(item);
            addEvent(EventEntity.WORK_ITEM_CANCELED, task, null, null, item.getId(), null);
        }
    }

    /** 待办从哪来：转办、委派、加签的人与原来的待办。流转生成的待办是 {@link #NONE}。 */
    @Data
    static class Origin {
        /** 转办、委派、加签的人 */
        private final Long fromUserId;
        /** 那个人的名字（快照） */
        private final String fromUserName;
        /** 委派、加签时原来的待办；转办时为 null（原来的那一份取消了） */
        private final Long parentItemId;
        /** {@code delegate}、{@code before}、{@code after}、{@code parallel}；转办时为 null */
        private final String addMode;

        static final Origin NONE = new Origin(null, null, null, null);
    }

    /**
     * 生成一个待办：只有一个人、没有收件箱时直接落到他；否则进收件箱（{@code pool}），人和角色、部门都存成候选，谁先认领谁办。
     *
     * @param signSeq 会签时是第几个参与人；不是会签时为 null
     * @param weight  票签的权重；不是票签时为 null
     */
    private WorkItemVO createWorkItem(ProcessNode.Task task, String arrival, Assignees who, Integer signSeq, Integer weight) {
        Instant due = task.getDue() == null ? null : deadlines.of(task.getDue(), now, this::read);
        return createWorkItem(task, arrival, who, signSeq, weight, Origin.NONE, WorkItemEntity.OPEN, due);
    }

    /**
     * 同上，带上从哪来；state 是 {@code open}，或后加签时先生成、原来的人处理完才打开的 {@code waiting}。
     *
     * @param dueAt 截止时间：流转来的按节点的时限算，转办、委派、加签的沿用原来那一份的；没有时限时为 null
     */
    private WorkItemVO createWorkItem(ProcessNode.Task task, String arrival, Assignees who, Integer signSeq, Integer weight,
                                    Origin origin, String state, Instant dueAt) {
        Long assigneeId = who.isDirect() ? who.directUser() : null;
        String assigneeName = assigneeId == null ? null : context.getOrg().userName(assigneeId);
        String assignment = assigneeId != null ? WorkItemEntity.DIRECT : WorkItemEntity.POOL;
        CandidateEntity inbox = who.firstInbox();
        int round = instance.getState().round(task.getId());

        WorkItemEntity row = new WorkItemEntity();
        row.setInstanceId(instance.getId());
        row.setProcessKey(instance.getProcessKey());
        row.setPriority(instance.getPriority());
        row.setNode(task.getId());
        row.setNodeName(task.getName());
        row.setRound(round);
        row.setSignSeq(signSeq);
        row.setWeight(weight);
        row.setState(state);
        row.setAssignment(assignment);
        row.setArrival(arrival);
        row.setInboxKind(inbox == null ? null : inbox.getKind());
        row.setInboxId(inbox == null ? null : inbox.getTargetId());
        row.setAssigneeId(assigneeId);
        row.setAssigneeName(assigneeName);
        row.setFromUserId(origin.getFromUserId());
        row.setFromUserName(origin.getFromUserName());
        row.setParentItemId(origin.getParentItemId());
        row.setAddMode(origin.getAddMode());
        row.setCreatedAt(now);
        row.setDueAt(dueAt);
        row.setRemindCount(0);
        row.setNeedsDesignation(false);
        row.setRev(0);
        WorkItemVO item = WorkItems.insert(context.getDb(), row, assigneeId != null ? List.of() : who.candidates());
        created.add(item);
        scheduleTimeouts(task, item);
        // 直接落到人的记下名字快照：流转记录上显示"到达 某节点 · 处理人 张三"；在收件箱里的没有
        addEvent(EventEntity.WORK_ITEM_CREATED, task, null, null, item.getId(), null,
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
    public WorkItemVO handOver(WorkItemVO from, Long userId, String arrival, String addMode, String state) {
        ProcessNode.Task task = (ProcessNode.Task) graph.node(from.getNode());
        Origin origin = new Origin(actor.getId(), actor.getName(), addMode == null ? null : from.getId(), addMode);
        return createWorkItem(task, arrival, Assignees.of(userId), from.getSignSeq(), from.getWeight(), origin, state, from.getDueAt());
    }

    /**
     * 待办的超时、提醒、无人认领（0005 的 5.3）各排一个作业：有截止时间时到期那一刻一个超时作业（超时怎么办见节点的 overdue）、
     * 每个提醒一个（到期前的；超时后每隔一段时间的从到期起算）；进了收件箱、写了 unclaimed 时到点看还有没有人认领。
     * 待办先办完了也不用删：作业到时候看到待办不再打开就跳过（BuiltinJobs）。
     */
    private void scheduleTimeouts(ProcessNode.Task task, WorkItemVO item) {
        if (item.getDueAt() != null) {
            schedule(BuiltinJobs.OVERDUE, item.getId(), item.getDueAt(), Map.of(), BuiltinJobs.MAX_ATTEMPTS);
            for (int i = 0; i < task.getReminders().size(); i++) {
                TaskRules.Reminder reminder = task.getReminders().get(i);
                Instant at = reminder.getBefore() != null ? deadlines.minus(reminder.getBefore(), item.getDueAt()) : deadlines.plus(reminder.getEvery(), item.getDueAt());
                if (at.isAfter(now)) {
                    schedule(BuiltinJobs.REMIND, item.getId(), at, Map.of(BuiltinJobs.REMINDER, i, BuiltinJobs.SENT, 0), BuiltinJobs.MAX_ATTEMPTS);
                }
            }
        }
        if (task.getUnclaimed() != null && item.getAssigneeId() == null) {
            schedule(BuiltinJobs.UNCLAIMED, item.getId(), deadlines.plus(task.getUnclaimed().getAfter(), now), Map.of(), BuiltinJobs.MAX_ATTEMPTS);
        }
    }

    /**
     * 排一个这个实例的作业（到时候由 {@link JobRunner} 做）。
     *
     * @param workItemId 相关的待办；定时节点、自动节点、等待节点的作业为 null
     * @param maxAttempts 最多试几次
     */
    void schedule(String kind, Long workItemId, Instant dueAt, Map<String, Object> payload, int maxAttempts) {
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

    /**
     * 这次到达人工节点由谁处理：退回来的、重新提交直接回来的，落到这个节点原来的处理人（没人处理过时照常解析）；提级来的只进收件箱，
     * 不直接落到人（第 6 节连线的 kind）；其余照节点上写的来源解析。
     */
    private Assignees assigneesFor(ProcessNode.Task task, String arrival) {
        if ("return".equals(arrival) || "resubmit".equals(arrival)) {
            Long last = WorkItems.lastCompletedBy(context.getDb(), instance.getId(), task.getId());
            if (last != null) return Assignees.of(last);
        }
        Assignees who = assignees.resolve(task, graph, instance, this::read, picks);
        return "escalate".equals(arrival) && !who.getInboxes().isEmpty() ? who.inboxesOnly() : who;
    }

    /**
     * 处理人解析为空（0005 的 2.1 {@code emptyAssignee}，节点上写的盖过流程级的）：{@code autoPass} 按第一个"同意"的结果自动通过；
     * {@code admin} 转给流程管理员（{@link org.dpdns.zerodep.flow.core.identity.OrgDirectory#administrators}，几个人时进收件箱）；
     * 其余（{@code error}，默认）报 {@code assignee-empty}，整个命令回滚。
     */
    private void handleEmptyAssignee(ProcessNode.Task task) {
        String rule = task.getInfo().getEmptyAssignee() != null ? task.getInfo().getEmptyAssignee() : graph.definition().getSettings().getEmptyAssignee();
        if ("autoPass".equals(rule)) {
            leave(task.getId(), approveOutcome(task));
            return;
        }
        List<Long> admins = "admin".equals(rule) ? context.getOrg().administrators() : List.of();
        if (!admins.isEmpty()) {
            Assignees who = Assignees.empty();
            who.getUsers().addAll(admins);
            createWorkItem(task, "emptyAssignee", who, null, null);
            return;
        }
        throw new FlowException(FlowErrorCode.VALIDATION_FAILED, "nobody can handle node " + task.getId(),
            List.of(ErrorDetail.node(task.getId(), "assignee-empty")));
    }

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
        for (WorkItemVO item : WorkItems.cancelOpen(context.getDb(), instance.getId(), now)) {
            closed.add(item);
            addEvent(EventEntity.WORK_ITEM_CANCELED, graph.node(item.getNode()), null, null, item.getId(), null);
        }
        instance.setState(instance.getState().withoutPending());
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
        for (WorkItemVO item : WorkItems.cancelOpen(context.getDb(), instance.getId(), now)) {
            closed.add(item);
            addEvent(EventEntity.WORK_ITEM_CANCELED, graph.node(item.getNode()), null, null, item.getId(), null);
        }
        instance.end(status, result, now);
        // 还没做的作业、等待、撤回用的快照都不再要（失败的作业留着给管理员看）
        Long id = instance.getId();
        context.getDb().deletable(JobEntity.class).allowDeleteStatement(true)
            .where(j -> { j.instanceId().eq(id); j.state().eq(JobEntity.PENDING); }).executeRows();
        context.getDb().deletable(WaitEntity.class).allowDeleteStatement(true).where(w -> w.instanceId().eq(id)).executeRows();
        context.getDb().deletable(StepEntity.class).allowDeleteStatement(true).where(st -> st.instanceId().eq(id)).executeRows();
        addEvent(eventType, null, null, result, null, comment);
        // 调用流程：终止、撤回时子实例一起终止；子实例结束了回到父实例（0005 的 4.2）
        if (InstanceEntity.TERMINATED.equals(status) || InstanceEntity.WITHDRAWN.equals(status)) CallActivities.terminateChildren(this, comment);
        if (instance.getParentInstanceId() != null && !quiet) CallActivities.childEnded(this);
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
        InstanceVO current = view();
        List<Notification> notifications = context.getDispatcher().notifies()
            ? Notifications.plan(saved, context, graph, current, sources -> assignees.people(sources, instance, this::read).stream().map(AssigneeResolver.Participant::getUser).toList())
            : List.of();
        context.getDispatcher().published(saved, current, graph, notifications);
    }
}
