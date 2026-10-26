package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.command.ClaimCommand;
import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.store.InstanceStore.InstanceRecord;
import org.dpdns.zerodep.flow.core.store.WorkItemStore.Claim;
import org.dpdns.zerodep.flow.model.runtime.FlowEvent;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance;
import org.dpdns.zerodep.flow.model.runtime.WorkItem;

import java.util.List;

/**
 * 认领收件箱里的待办（0006 的 3.2）。认领不推进实例，所以不锁实例行；先到先得靠带条件的更新
 * （{@code WHERE state = 'open' AND assignee_id IS NULL}，0007 第 5 节"认领"）。
 * "处理"一个还在收件箱里的待办时也先走这里（{@link #claim}）。
 */
public final class ClaimHandler {
    private final EngineContext context;
    private final DefinitionCache definitions;

    public ClaimHandler(EngineContext context) {
        this.context = context;
        this.definitions = context.definitions();
    }

    /** 认领，记一条"认领"的流转记录 */
    public CommandResult handle(Actor actor, ClaimCommand command) {
        return context.transaction(() -> {
            WorkItem item = context.stores().workItems().find(command.workItemId())
                .orElseThrow(() -> FlowErrors.notFound("workItem", command.workItemId()));
            InstanceRecord instance = context.stores().instances().find(item.instanceId())
                .orElseThrow(() -> FlowErrors.notFound("instance", item.instanceId()));
            requireActive(instance);
            claim(actor, item);

            Execution run = new Execution(context, actor, context.clock().instant(), definitions.of(instance), instance,
                FormValues.empty(), instance.rev());
            run.addEvent(FlowEvent.WORK_ITEM_CLAIMED, run.graph().node(item.node()), null, null, item.id(), null);
            run.publish();
            return new CommandResult(instance.view(), List.of(), List.of(), run.events);
        });
    }

    /**
     * 把待办认领给操作人：已经是他的就什么也不做；别人认领了报 {@code flow.already-claimed}；
     * 不是候选人（或退回过收件箱）报 {@code flow.not-candidate}。
     */
    void claim(Actor actor, WorkItem item) {
        if (!WorkItem.OPEN.equals(item.state())) throw FlowErrors.notOpen(item.id());
        if (item.assigneeId() != null) {
            if (item.assigneeId().equals(actor.id())) return;
            throw FlowErrors.alreadyClaimed(item.id());
        }
        if (!CandidateRules.isCandidate(actor, context.stores().workItems().candidates(item.id()))) {
            throw FlowErrors.notCandidate(item.id());
        }
        // 两个人同时认领：带条件的更新只有一个改到
        if (!context.stores().workItems().claim(item.id(), new Claim(actor.id(), actor.name(), context.clock().instant()))) {
            throw FlowErrors.alreadyClaimed(item.id());
        }
    }

    /** 实例已结束、已暂停时不能再动它的待办 */
    static void requireActive(InstanceRecord instance) {
        if (!ProcessInstance.ACTIVE.equals(instance.status())) throw FlowErrors.notActive(instance);
    }
}
