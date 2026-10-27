package org.dpdns.zerodep.flow.core.engine.handler;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.command.WithdrawStepCommand;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.RunState;
import org.dpdns.zerodep.flow.core.data.Variables;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.Execution;
import org.dpdns.zerodep.flow.core.engine.FlowErrors;
import org.dpdns.zerodep.flow.core.engine.LoadedDefinition;
import org.dpdns.zerodep.flow.core.engine.store.Instances;
import org.dpdns.zerodep.flow.core.engine.store.WorkItems;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.entity.StepEntity;
import org.dpdns.zerodep.flow.core.entity.WorkItemEntity;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.view.workitem.WorkItemVO;
import org.dpdns.zerodep.flow.model.definition.ProcessGraph;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 处理人撤回刚处理的一步（0006 的 3.3 {@code withdrawStep}）。处理时记下了处理之前的快照（{@code flow_step}，{@link #remember}），撤回要同时满足：
 * <ul>
 *   <li>这个待办是我处理的、节点上 {@code allow.withdraw} 没关、实例进行中；</li>
 *   <li>处理之后实例再没被动过（版本没变：没人认领、处理、催办作业……）；</li>
 *   <li>那一步只走过人工节点、网关、抄送节点（没有发起子流程、自动处理、等外部事件、定时）；</li>
 *   <li>那一步生成的待办都还没人认领、没人处理。</li>
 * </ul>
 * 撤回时取消那一步生成的待办，运行状态、流程变量、表单的值恢复成快照，待办回到我手里重新处理。
 */
public class StepWithdrawal {
    /** 那一步只走过这些节点时才能撤回 */
    private static final Set<String> UNDOABLE = Set.of("task", "exclusive", "parallel", "inclusive", "cc", "end");

    private final EngineContext context;

    public StepWithdrawal(EngineContext context) { this.context = context; }

    /**
     * 处理完一个待办后记下处理之前的样子（每个实例只留最近一步）；实例结束了就不再能撤回，删掉。
     *
     * @param instance 处理之后的实例（版本是这一步锁住时的）
     */
    static void remember(EasyEntityQuery db, InstanceEntity instance, Long workItemId, RunState state, Variables variables, FormValues form) {
        db.deletable(StepEntity.class).allowDeleteStatement(true).where(s -> s.instanceId().eq(instance.getId())).executeRows();
        if (instance.getEndedAt() != null) return;
        StepEntity step = new StepEntity();
        step.setInstanceId(instance.getId());
        step.setWorkItemId(workItemId);
        step.setRev(instance.getRev());
        step.setState(state);
        step.setVariables(variables);
        step.setForm(form);
        db.insertable(step).executeRows();
    }

    /** 撤回；不能撤回时报 {@code flow.forbidden}（原因在明细里） */
    public CommandResult withdraw(Actor actor, WithdrawStepCommand command) {
        return context.transaction(() -> {
            WorkItemVO found = WorkItems.find(context.getDb(), command.getWorkItemId());
            InstanceEntity instance = Instances.lock(context.getDb(), found.getInstanceId());
            WorkItemVO item = WorkItems.find(context.getDb(), found.getId());
            LoadedDefinition definition = context.getDefinitions().of(instance);
            // 锁住实例时版本加了一：快照要是上一个版本的
            StepEntity snapshot = check(actor, item, definition.getGraph(), instance, instance.getRev() - 1);
            if (snapshot == null) throw FlowErrors.forbidden("workItem", item.getId(), "cannot-withdraw-step");

            Execution run = new Execution(context, actor, context.getClock().instant(), definition, instance, snapshot.getForm(), instance.getRev());
            for (WorkItemVO next : createdAt(instance.getId(), snapshot.getRev())) {
                WorkItems.cancel(context.getDb(), next.getId(), run.now());
                run.closed.add(next);
                run.addEvent(EventEntity.WORK_ITEM_CANCELED, run.graph().node(next.getNode()), null, null, next.getId(), null);
            }
            run.restore(snapshot.getState(), snapshot.getVariables());
            if (definition.hasForm()) Instances.saveForm(context.getDb(), instance.getId(), snapshot.getForm());
            reopen(item.getId());
            context.getDb().deletable(StepEntity.class).allowDeleteStatement(true).where(s -> s.instanceId().eq(instance.getId())).executeRows();
            run.addEvent(EventEntity.WORK_ITEM_WITHDRAWN, run.graph().node(item.getNode()), null, item.getOutcome(), item.getId(), command.getReason());
            run.flush(null);
            return run.result();
        });
    }

    /** 我现在能不能撤回这一步（待办详情上的"撤回"按钮）：同撤回的条件，实例的版本按现在的 */
    public boolean allowed(Actor actor, WorkItemVO item, ProcessGraph graph, InstanceEntity instance) {
        return check(actor, item, graph, instance, instance.getRev()) != null;
    }

    /** 能撤回时返回快照，否则为 null；rev 是处理那一步时实例应有的版本 */
    private StepEntity check(Actor actor, WorkItemVO item, ProcessGraph graph, InstanceEntity instance, int rev) {
        if (!WorkItemEntity.DONE.equals(item.getState()) || !actor.getId().equals(item.getCompletedBy())) return null;
        if (!InstanceEntity.ACTIVE.equals(instance.getStatus())) return null;
        if (!(graph.node(item.getNode()) instanceof ProcessNode.Task task) || !task.getAllow().isWithdraw()) return null;
        StepEntity snapshot = context.getDb().queryable(StepEntity.class)
            .where(s -> { s.instanceId().eq(instance.getId()); s.workItemId().eq(item.getId()); s.rev().eq(rev); })
            .singleOrNull();
        if (snapshot == null) return null;
        List<String> entered = context.getDb().queryable(EventEntity.class)
            .where(e -> { e.instanceId().eq(instance.getId()); e.instanceRev().eq(rev); e.type().eq(EventEntity.NODE_ENTERED); })
            .selectColumn(e -> e.node()).toList();
        boolean simple = entered.stream().allMatch(node -> graph.node(node) != null && UNDOABLE.contains(graph.node(node).getType()));
        boolean untouched = createdAt(instance.getId(), rev).stream()
            .allMatch(w -> WorkItemEntity.PENDING.contains(w.getState()) && !WorkItemEntity.CLAIMED.equals(w.getAssignment()));
        return simple && untouched ? snapshot : null;
    }

    /** 那一步生成的待办 */
    private List<WorkItemVO> createdAt(Long instanceId, int rev) {
        List<Long> ids = context.getDb().queryable(EventEntity.class)
            .where(e -> { e.instanceId().eq(instanceId); e.instanceRev().eq(rev); e.type().eq(EventEntity.WORK_ITEM_CREATED); e.workItemId().isNotNull(); })
            .selectColumn(e -> e.workItemId()).toList();
        if (ids.isEmpty()) return List.of();
        return context.getDb().queryable(WorkItemEntity.class).where(w -> w.id().in(ids)).orderBy(w -> w.id().asc()).select(WorkItemVO.class).toList();
    }

    /** 已处理的待办改回打开着，清掉处理结果、意见、处理人与处理时间 */
    private void reopen(Long workItemId) {
        context.getDb().updatable(WorkItemEntity.class)
            .setColumns(w -> {
                w.state().set(WorkItemEntity.OPEN);
                w.outcome().set((String) null);
                w.comment().set((String) null);
                w.completedBy().set((Long) null);
                w.completedByName().set((String) null);
                w.completedAt().set((Instant) null);
                w.rev().increment();
            })
            .where(w -> { w.id().eq(workItemId); w.state().eq(WorkItemEntity.DONE); })
            .executeRows();
    }
}
