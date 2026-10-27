package org.dpdns.zerodep.flow.core.engine.handler;

import org.dpdns.zerodep.flow.core.command.ClaimCommand;
import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.engine.DefinitionCache;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.Execution;
import org.dpdns.zerodep.flow.core.engine.FlowErrors;
import org.dpdns.zerodep.flow.core.engine.assignee.CandidateRules;
import org.dpdns.zerodep.flow.core.engine.store.Instances;
import org.dpdns.zerodep.flow.core.engine.store.WorkItems;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.entity.WorkItemEntity;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.view.workitem.WorkItemVO;

/**
 * 认领收件箱里的待办（0006 的 3.2）。认领不推进实例，所以不锁实例行；先到先得靠带条件的更新
 * （{@code WHERE state = 'open' AND assignee_id IS NULL}，0007 第 5 节"认领"）。
 * "处理"一个还在收件箱里的待办时也先走这里（{@link #claim}）。
 */
public class ClaimHandler {
    private final EngineContext context;
    private final DefinitionCache definitions;

    public ClaimHandler(EngineContext context) {
        this.context = context;
        this.definitions = context.getDefinitions();
    }

    /** 认领，记一条"认领"的流转记录 */
    public CommandResult handle(Actor actor, ClaimCommand command) {
        return context.transaction(() -> {
            WorkItemVO item = WorkItems.find(context.getDb(), command.getWorkItemId());
            InstanceEntity instance = Instances.find(context.getDb(), item.getInstanceId());
            requireActive(instance);
            claim(actor, item);

            Execution run = new Execution(context, actor, context.getClock().instant(), definitions.of(instance), instance,
                FormValues.empty(), instance.getRev());
            run.addEvent(EventEntity.WORK_ITEM_CLAIMED, run.graph().node(item.getNode()), null, null, item.getId(), null);
            run.publish();
            return run.result();
        });
    }

    /**
     * 把待办认领给操作人：已经是他的就什么也不做；别人认领了报 {@code flow.already-claimed}；
     * 不是候选人（或退回过收件箱）报 {@code flow.not-candidate}。
     */
    void claim(Actor actor, WorkItemVO item) {
        if (!WorkItemEntity.OPEN.equals(item.getState())) throw FlowErrors.notOpen(item.getId());
        if (item.getAssigneeId() != null) {
            if (item.getAssigneeId().equals(actor.getId())) return;
            throw FlowErrors.alreadyClaimed(item.getId());
        }
        if (!CandidateRules.isCandidate(actor, WorkItems.candidates(context.getDb(), item.getId()))) {
            throw FlowErrors.notCandidate(item.getId());
        }
        // 两个人同时认领：带条件的更新只有一个改到
        long claimed = context.getDb().updatable(WorkItemEntity.class)
            .setColumns(w -> {
                w.assigneeId().set(actor.getId());
                w.assigneeName().set(actor.getName());
                w.claimedAt().set(context.getClock().instant());
                w.assignment().set(WorkItemEntity.CLAIMED);
                w.rev().increment();
            })
            .where(w -> { w.id().eq(item.getId()); w.state().eq(WorkItemEntity.OPEN); w.assigneeId().isNull(); })
            .executeRows();
        if (claimed != 1) throw FlowErrors.alreadyClaimed(item.getId());
    }

    /** 实例已结束、已暂停时不能再动它的待办 */
    static void requireActive(InstanceEntity instance) {
        if (!InstanceEntity.ACTIVE.equals(instance.getStatus())) throw FlowErrors.notActive(instance);
    }
}
