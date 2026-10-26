package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.command.WithdrawStepCommand;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.store.InstanceStore.InstanceRecord;
import org.dpdns.zerodep.flow.core.store.StepStore.StepSnapshot;
import org.dpdns.zerodep.flow.model.definition.ProcessGraph;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.runtime.FlowEvent;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance;
import org.dpdns.zerodep.flow.model.runtime.WorkItem;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 处理人撤回刚处理的一步（0006 的 3.3 {@code withdrawStep}）。处理时记下了处理之前的快照（{@code flow_step}），撤回要同时满足：
 * <ul>
 *   <li>这个待办是我处理的、节点上 {@code allow.withdraw} 没关、实例进行中；</li>
 *   <li>处理之后实例再没被动过（版本没变：没人认领、处理、催办作业……）；</li>
 *   <li>那一步只走过人工节点、网关、抄送节点（没有发起子流程、自动处理、等外部事件、定时）；</li>
 *   <li>那一步生成的待办都还没人认领、没人处理。</li>
 * </ul>
 * 撤回时取消那一步生成的待办，运行状态、流程变量、表单的值恢复成快照，待办回到我手里重新处理。
 */
public final class StepWithdrawal {
    /** 那一步只走过这些节点时才能撤回 */
    private static final Set<String> UNDOABLE = Set.of("task", "exclusive", "parallel", "inclusive", "cc", "end");

    private final EngineContext context;

    public StepWithdrawal(EngineContext context) { this.context = context; }

    /** 撤回；不能撤回时报 {@code flow.forbidden}（原因在明细里） */
    public CommandResult withdraw(Actor actor, WithdrawStepCommand command) {
        return context.transaction(() -> {
            WorkItem found = context.stores().workItems().find(command.workItemId())
                .orElseThrow(() -> FlowErrors.notFound("workItem", command.workItemId()));
            int rev = context.stores().instances().lock(found.instanceId()).orElseThrow();
            InstanceRecord instance = context.stores().instances().find(found.instanceId()).orElseThrow();
            WorkItem item = context.stores().workItems().find(found.id()).orElseThrow();
            LoadedDefinition definition = context.definitions().of(instance);
            // 锁住实例时版本加了一：快照要是上一个版本的
            StepSnapshot snapshot = check(actor, item, definition.graph(), instance, rev - 1)
                .orElseThrow(() -> FlowErrors.forbidden("workItem", item.id(), "cannot-withdraw-step"));

            Execution run = new Execution(context, actor, context.clock().instant(), definition, instance, snapshot.form(), rev);
            for (WorkItem next : createdAt(instance.id(), snapshot.rev())) {
                context.stores().workItems().cancel(next.id(), run.now());
                run.closed.add(next);
                run.addEvent(FlowEvent.WORK_ITEM_CANCELED, run.graph().node(next.node()), null, null, next.id(), null);
            }
            run.restore(snapshot.state(), snapshot.variables());
            if (definition.hasForm()) context.stores().formData().update(instance.id(), snapshot.form());
            context.stores().workItems().reopen(item.id());
            context.stores().steps().remove(instance.id());
            run.addEvent(FlowEvent.WORK_ITEM_WITHDRAWN, run.graph().node(item.node()), null, item.outcome(), item.id(), command.reason());
            run.flush(null);
            return run.result();
        });
    }

    /** 我现在能不能撤回这一步（待办详情上的"撤回"按钮）：同撤回的条件，实例的版本按现在的 */
    boolean allowed(Actor actor, WorkItem item, ProcessGraph graph, InstanceRecord instance) {
        return check(actor, item, graph, instance, instance.rev()).isPresent();
    }

    /** 能撤回时返回快照；rev 是处理那一步时实例应有的版本 */
    private Optional<StepSnapshot> check(Actor actor, WorkItem item, ProcessGraph graph, InstanceRecord instance, int rev) {
        if (!WorkItem.DONE.equals(item.state()) || !actor.id().equals(item.completedBy())) return Optional.empty();
        if (!ProcessInstance.ACTIVE.equals(instance.status())) return Optional.empty();
        if (!(graph.node(item.node()) instanceof ProcessNode.Task task) || !task.allow().withdraw()) return Optional.empty();
        Optional<StepSnapshot> snapshot = context.stores().steps().find(instance.id())
            .filter(s -> s.workItemId().equals(item.id()) && s.rev() == rev);
        if (snapshot.isEmpty()) return Optional.empty();
        List<FlowEvent> step = context.stores().events().timeline(instance.id()).stream().filter(e -> e.instanceRev() == rev).toList();
        boolean simple = step.stream().filter(e -> FlowEvent.NODE_ENTERED.equals(e.type()))
            .allMatch(e -> graph.node(e.node()) != null && UNDOABLE.contains(graph.node(e.node()).type()));
        boolean untouched = createdAt(instance.id(), rev).stream()
            .allMatch(w -> (WorkItem.OPEN.equals(w.state()) || WorkItem.WAITING.equals(w.state())) && !WorkItem.CLAIMED.equals(w.assignment()));
        return simple && untouched ? snapshot : Optional.empty();
    }

    /** 那一步生成的待办 */
    private List<WorkItem> createdAt(Long instanceId, int rev) {
        Set<Long> ids = context.stores().events().timeline(instanceId).stream()
            .filter(e -> e.instanceRev() == rev && FlowEvent.WORK_ITEM_CREATED.equals(e.type()) && e.workItemId() != null)
            .map(FlowEvent::workItemId).collect(Collectors.toSet());
        return context.stores().workItems().ofInstance(instanceId).stream().filter(w -> ids.contains(w.id())).toList();
    }
}
