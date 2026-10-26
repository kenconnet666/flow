package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.command.JumpCommand;
import org.dpdns.zerodep.flow.core.command.ReassignCommand;
import org.dpdns.zerodep.flow.core.command.ResumeCommand;
import org.dpdns.zerodep.flow.core.command.RetryJobCommand;
import org.dpdns.zerodep.flow.core.command.SuspendCommand;
import org.dpdns.zerodep.flow.core.command.TerminateCommand;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.store.InstanceStore.InstanceRecord;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.runtime.FlowEvent;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance;
import org.dpdns.zerodep.flow.model.runtime.WorkItem;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 管理员命令（0006 的 3.4）：终止、暂停与恢复、跳转、改派、失败的作业重试。谁能做由 {@code PermissionPolicy} 定（默认放行，接入方收紧）；
 * 引擎只管实例的状态对不对：已结束的实例什么也不能做，暂停的实例只能恢复或终止。都先锁住实例，做完写回、写流转记录。
 */
public final class AdminHandler {
    private final EngineContext context;
    private final DefinitionCache definitions;

    public AdminHandler(EngineContext context, DefinitionCache definitions) {
        this.context = context;
        this.definitions = definitions;
    }

    /** 终止：打开着的待办都取消，实例结束，结果是 {@code terminated}（进行中、暂停的都能终止） */
    public CommandResult terminate(Actor actor, TerminateCommand command) {
        return onInstance(actor, command.instanceId(), false, (run, instance) ->
            run.finish(ProcessInstance.TERMINATED, ProcessInstance.TERMINATED, FlowEvent.INSTANCE_TERMINATED, command.reason()));
    }

    /** 暂停：实例停在原地，待办都不能处理（认领、处理、协作都报 {@code flow.instance-not-active}） */
    public CommandResult suspend(Actor actor, SuspendCommand command) {
        return onInstance(actor, command.instanceId(), true, (run, instance) ->
            run.changeStatus(ProcessInstance.SUSPENDED, FlowEvent.INSTANCE_SUSPENDED, command.reason()));
    }

    /** 恢复：暂停的实例接着走 */
    public CommandResult resume(Actor actor, ResumeCommand command) {
        return onInstance(actor, command.instanceId(), false, (run, instance) -> {
            if (!ProcessInstance.SUSPENDED.equals(instance.status())) throw FlowErrors.forbidden("instance", instance.id(), "not-suspended");
            run.changeStatus(ProcessInstance.ACTIVE, FlowEvent.INSTANCE_RESUMED, null);
        });
    }

    /** 跳转：原来还没办完的待办都取消，从指定的节点重新走（开始节点不行） */
    public CommandResult jump(Actor actor, JumpCommand command) {
        return onInstance(actor, command.instanceId(), true, (run, instance) -> {
            ProcessNode node = run.graph().node(command.node());
            if (node == null || node instanceof ProcessNode.Start) throw FlowErrors.invalid("node", "unknown-node");
            run.jumpTo(node.id(), command.reason());
        });
    }

    /** 改派：待办直接落到这个人，候选删掉（收件箱里的、认领了的、直接落到人的都能改） */
    public CommandResult reassign(Actor actor, ReassignCommand command) {
        if (command.userId() == null) throw FlowErrors.invalid("userId", "required");
        WorkItem found = context.stores().workItems().find(command.workItemId())
            .orElseThrow(() -> FlowErrors.notFound("workItem", command.workItemId()));
        return onInstance(actor, found.instanceId(), true, (run, instance) -> {
            WorkItem item = context.stores().workItems().find(found.id()).orElseThrow();
            if (!WorkItem.OPEN.equals(item.state())) throw FlowErrors.notOpen(item.id());
            String name = context.org().userName(command.userId());
            if (!context.stores().workItems().reassign(item.id(), command.userId(), name)) throw FlowErrors.notOpen(item.id());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("toUserId", command.userId());
            data.put("toUserName", name);
            run.addEvent(FlowEvent.WORK_ITEM_REASSIGNED, run.graph().node(item.node()), null, null, item.id(), command.reason(), data);
        });
    }

    /** 失败的作业重新来过：改回等着做、马上到期；不是失败的作业时报 {@code flow.not-found} */
    public boolean retryJob(Actor actor, RetryJobCommand command) {
        if (!context.stores().jobs().revive(command.jobId(), context.clock().instant())) throw FlowErrors.notFound("job", command.jobId());
        return true;
    }

    /**
     * 锁住实例（版本加一）再做：已结束的实例报 {@code flow.instance-not-active}；mustBeActive 时暂停的也报。做完写回。
     */
    private CommandResult onInstance(Actor actor, Long instanceId, boolean mustBeActive, BiConsumer<Execution, InstanceRecord> action) {
        return context.transaction(() -> {
            int rev = context.stores().instances().lock(instanceId).orElseThrow(() -> FlowErrors.notFound("instance", instanceId));
            InstanceRecord instance = context.stores().instances().find(instanceId).orElseThrow();
            boolean active = ProcessInstance.ACTIVE.equals(instance.status());
            boolean suspended = ProcessInstance.SUSPENDED.equals(instance.status());
            if (mustBeActive ? !active : !(active || suspended)) throw FlowErrors.notActive(instance);
            FormValues form = context.stores().formData().read(instanceId).map(f -> f.values()).orElse(FormValues.empty());
            Execution run = new Execution(context, actor, context.clock().instant(), definitions.of(instance), instance, form, rev);
            action.accept(run, instance);
            run.flush(null);
            return run.result();
        });
    }
}
