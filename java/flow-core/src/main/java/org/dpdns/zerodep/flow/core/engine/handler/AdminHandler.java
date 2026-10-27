package org.dpdns.zerodep.flow.core.engine.handler;

import org.dpdns.zerodep.flow.core.command.ChangeDataCommand;
import org.dpdns.zerodep.flow.core.command.JumpCommand;
import org.dpdns.zerodep.flow.core.command.MigrateCommand;
import org.dpdns.zerodep.flow.core.command.ReassignCommand;
import org.dpdns.zerodep.flow.core.command.ResumeCommand;
import org.dpdns.zerodep.flow.core.command.RetryJobCommand;
import org.dpdns.zerodep.flow.core.command.SkipJobCommand;
import org.dpdns.zerodep.flow.core.command.SuspendCommand;
import org.dpdns.zerodep.flow.core.command.TerminateCommand;
import org.dpdns.zerodep.flow.core.engine.DefinitionCache;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.Execution;
import org.dpdns.zerodep.flow.core.engine.FlowErrors;
import org.dpdns.zerodep.flow.core.engine.LoadedDefinition;
import org.dpdns.zerodep.flow.core.engine.job.BuiltinJobs;
import org.dpdns.zerodep.flow.core.engine.job.DeliveryJob;
import org.dpdns.zerodep.flow.core.engine.node.CallBehavior;
import org.dpdns.zerodep.flow.core.engine.node.ServiceBehavior;
import org.dpdns.zerodep.flow.core.engine.store.Instances;
import org.dpdns.zerodep.flow.core.engine.store.WorkItems;
import org.dpdns.zerodep.flow.core.entity.DefinitionEntity;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.entity.FormDataEntity;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.entity.JobEntity;
import org.dpdns.zerodep.flow.core.entity.WaitEntity;
import org.dpdns.zerodep.flow.core.entity.WorkItemEntity;
import org.dpdns.zerodep.flow.core.spi.Actor;
import org.dpdns.zerodep.flow.core.spi.FormValues;
import org.dpdns.zerodep.flow.core.view.CommandResult;
import org.dpdns.zerodep.flow.core.view.workitem.WorkItemVO;
import org.dpdns.zerodep.flow.model.definition.ProcessEdge;
import org.dpdns.zerodep.flow.model.definition.ProcessGraph;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * 管理员命令（0006 的 3.4）：终止、暂停与恢复、跳转、改派、失败的作业重试或跳过。谁能做由 {@code PermissionPolicy} 定（默认放行，接入方收紧）；
 * 引擎只管实例的状态对不对：已结束的实例什么也不能做，暂停的实例只能恢复或终止。都先锁住实例，做完写回、写流转记录。
 */
public class AdminHandler {
    private final EngineContext context;
    private final DefinitionCache definitions;

    public AdminHandler(EngineContext context) {
        this.context = context;
        this.definitions = context.getDefinitions();
    }

    /** 终止：打开着的待办都取消，实例结束，结果是 {@code terminated}（进行中、暂停的都能终止） */
    public CommandResult terminate(Actor actor, TerminateCommand command) {
        return onInstance(actor, command.getInstanceId(), false, (run, instance) ->
            run.finish(InstanceEntity.TERMINATED, InstanceEntity.TERMINATED, EventEntity.INSTANCE_TERMINATED, command.getReason()));
    }

    /** 暂停：实例停在原地，待办都不能处理（认领、处理、协作都报 {@code flow.instance-not-active}） */
    public CommandResult suspend(Actor actor, SuspendCommand command) {
        return onInstance(actor, command.getInstanceId(), true, (run, instance) ->
            run.changeStatus(InstanceEntity.SUSPENDED, EventEntity.INSTANCE_SUSPENDED, command.getReason()));
    }

    /** 恢复：暂停的实例接着走 */
    public CommandResult resume(Actor actor, ResumeCommand command) {
        return onInstance(actor, command.getInstanceId(), false, (run, instance) -> {
            if (!InstanceEntity.SUSPENDED.equals(instance.getStatus())) throw FlowErrors.forbidden("instance", instance.getId(), "not-suspended");
            run.changeStatus(InstanceEntity.ACTIVE, EventEntity.INSTANCE_RESUMED, null);
            // 暂停期间子实例都结束了的调用节点，现在往下走
            CallBehavior.resumeWaitingCalls(run);
        });
    }

    /** 跳转：原来还没办完的待办都取消，从指定的节点重新走（开始节点不行） */
    public CommandResult jump(Actor actor, JumpCommand command) {
        return onInstance(actor, command.getInstanceId(), true, (run, instance) -> {
            ProcessNode node = run.getGraph().node(command.getNode());
            if (node == null || node instanceof ProcessNode.Start) throw FlowErrors.invalid("node", "unknown-node");
            run.jumpTo(node.getId(), command.getReason());
        });
    }

    /** 改派：待办直接落到这个人，候选删掉（收件箱里的、认领了的、直接落到人的都能改） */
    public CommandResult reassign(Actor actor, ReassignCommand command) {
        if (command.getUserId() == null) throw FlowErrors.invalid("userId", "required");
        WorkItemVO found = WorkItems.find(context.getDb(), command.getWorkItemId());
        return onInstance(actor, found.getInstanceId(), true, (run, instance) -> {
            WorkItemVO item = WorkItems.find(context.getDb(), found.getId());
            if (!WorkItemEntity.OPEN.equals(item.getState())) throw FlowErrors.notOpen(item.getId());
            String name = context.getOrg().userName(command.getUserId());
            if (!WorkItems.assignTo(context.getDb(), item.getId(), command.getUserId(), name, WorkItemEntity.DIRECT, null)) throw FlowErrors.notOpen(item.getId());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("toUserId", command.getUserId());
            data.put("toUserName", name);
            run.addEvent(EventEntity.WORK_ITEM_REASSIGNED, run.getGraph().node(item.getNode()), null, null, item.getId(), command.getReason(), data);
        });
    }

    /**
     * 迁移到流程的另一个版本：正在用的节点（开着、等着的待办，在等的定时、事件、调用）按对应关系换到新版本的节点上
     * （同名的可以不写），新版本里没有或类型不同时报 {@code flow.validation-failed}；运行状态里按节点记的、待办、等待、作业都跟着改，
     * 记一条 {@code instance.migrated}。表单换成新版本用的表单版本（值不动）。
     */
    public CommandResult migrate(Actor actor, MigrateCommand command) {
        if (command.getReason() == null || command.getReason().isBlank()) throw FlowErrors.invalid("reason", "required");
        return context.transaction(() -> {
            InstanceEntity instance = Instances.lock(context.getDb(), command.getInstanceId());
            if (instance.getEndedAt() != null) throw FlowErrors.notActive(instance);
            LoadedDefinition from = definitions.of(instance);
            Long targetId = context.getDb().queryable(DefinitionEntity.class)
                .where(v -> { v.processKey().eq(instance.getProcessKey()); v.version().eq(command.getVersion()); })
                .selectColumn(v -> v.id()).firstOrNull();
            if (targetId == null) throw FlowErrors.notFound("definition", instance.getProcessKey() + "@" + command.getVersion());
            LoadedDefinition to = definitions.byId(targetId);

            // 正在用的节点 → 新版本里的节点
            List<WorkItemVO> live = WorkItems.ofInstance(context.getDb(), instance.getId()).stream()
                .filter(w -> WorkItemEntity.OPEN.equals(w.getState()) || WorkItemEntity.WAITING.equals(w.getState())).toList();
            java.util.Set<String> inUse = new java.util.LinkedHashSet<>(instance.getState().getWaits().keySet());
            live.forEach(w -> inUse.add(w.getNode()));
            Map<String, String> mapping = new LinkedHashMap<>();
            for (String node : inUse) {
                String target = command.getNodes().getOrDefault(node, node);
                ProcessNode before = from.getGraph().node(node);
                ProcessNode after = to.getGraph().node(target);
                if (after == null || before == null || !after.getType().equals(before.getType())) throw FlowErrors.invalid("nodes." + node, "no-matching-node");
                mapping.put(node, target);
            }

            Instant now = context.getClock().instant();
            instance.setDefinitionId(to.getStored().getId());
            instance.setVersion(to.getStored().getVersion());
            instance.setState(instance.getState().renamed(mapping));
            mapping.forEach((node, target) -> {
                String name = to.getGraph().node(target).getName();
                context.getDb().updatable(WorkItemEntity.class)
                    .setColumns(w -> { w.node().set(target); w.nodeName().set(name); })
                    .where(w -> { w.instanceId().eq(instance.getId()); w.node().eq(node); w.state().in(List.of(WorkItemEntity.OPEN, WorkItemEntity.WAITING)); })
                    .executeRows();
                context.getDb().updatable(WaitEntity.class).setColumns(w -> w.node().set(target))
                    .where(w -> { w.instanceId().eq(instance.getId()); w.node().eq(node); }).executeRows();
            });
            // 定时、自动节点、等事件超时的作业里记着节点
            for (JobEntity job : context.getDb().queryable(JobEntity.class)
                    .where(j -> { j.instanceId().eq(instance.getId()); j.state().eq(JobEntity.PENDING); }).toList()) {
                Object node = job.getPayload().get(BuiltinJobs.NODE);
                if (node instanceof String n && mapping.containsKey(n) && !mapping.get(n).equals(n)) {
                    Map<String, Object> payload = new LinkedHashMap<>(job.getPayload());
                    payload.put(BuiltinJobs.NODE, mapping.get(n));
                    context.getDb().updatable(JobEntity.class).setColumns(j -> j.payload().set(payload)).where(j -> j.id().eq(job.getId())).executeRows();
                }
            }
            if (to.hasForm()) {
                Long formVersionId = to.form().getId();
                context.getDb().updatable(FormDataEntity.class).setColumns(d -> d.formVersionId().set(formVersionId))
                    .where(d -> d.instanceId().eq(instance.getId())).executeRows();
            }

            Execution run = new Execution(context, actor, now, to, instance, Instances.form(context.getDb(), instance.getId()), instance.getRev());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("fromVersion", from.getStored().getVersion());
            data.put("toVersion", to.getStored().getVersion());
            data.put("nodes", mapping);
            run.addEvent(EventEntity.INSTANCE_MIGRATED, null, null, null, null, command.getReason(), data);
            run.flush(null);
            return run.toResult();
        });
    }

    /**
     * 改数据：改流程变量、表单字段（表单按清单的字段类型读），改前改后的值记进流转记录。暂停中的实例也能改。
     */
    public CommandResult changeData(Actor actor, ChangeDataCommand command) {
        if (command.getReason() == null || command.getReason().isBlank()) throw FlowErrors.invalid("reason", "required");
        if (command.getVariables().isEmpty() && command.getForm().isEmpty()) throw FlowErrors.invalid("changes", "required");
        return onInstance(actor, command.getInstanceId(), false, (run, instance) -> {
            Map<String, Object> data = new LinkedHashMap<>();
            if (!command.getVariables().isEmpty()) {
                data.put("variables", diff(instance.getVariables().asMap(), command.getVariables().asMap()));
                instance.setVariables(instance.getVariables().with(command.getVariables().asMap()));
            }
            if (!command.getForm().isEmpty()) {
                if (run.getManifest() == null) throw FlowErrors.invalid("form", "no-form");
                FormValues changes = FormValues.of(run.getManifest().typed(command.getForm().asMap()));
                data.put("form", diff(run.getForm().asMap(), changes.asMap()));
                run.setForm(run.getForm().with(changes));
                Instances.saveForm(context.getDb(), instance.getId(), run.getForm());
            }
            run.addEvent(EventEntity.INSTANCE_DATA_CHANGED, null, null, null, null, command.getReason(), data);
        });
    }

    /** 名字 → { before, after }：留痕用 */
    private static Map<String, Object> diff(Map<String, Object> before, Map<String, Object> changes) {
        Map<String, Object> out = new LinkedHashMap<>();
        changes.forEach((name, after) -> {
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("before", before.get(name));
            change.put("after", after);
            out.put(name, change);
        });
        return out;
    }

    /** 失败的作业重新来过：改回等着做、马上到期；不是失败的作业时报 {@code flow.not-found} */
    public boolean retryJob(Actor actor, RetryJobCommand command) {
        Instant now = context.getClock().instant();
        long revived = context.getDb().updatable(JobEntity.class)
            .setColumns(j -> {
                j.state().set(JobEntity.PENDING);
                j.attempts().set(0);
                j.dueAt().set(now);
                j.lockedBy().set((String) null);
                j.lockedUntil().set((Instant) null);
            })
            .where(j -> { j.id().eq(command.getJobId()); j.state().eq(JobEntity.FAILED); })
            .executeRows();
        if (revived == 0) throw FlowErrors.notFound("job", command.getJobId());
        return true;
    }

    /**
     * 跳过失败的作业：发件箱的投递（{@code deliver}）放弃投、作业删掉，实例不动（结束了也行）；
     * 异步的自动节点不再做动作，按指定的结果往下走，作业删掉。只有这两种作业能跳过，自动节点的
     * 实例要进行中（暂停的先恢复）、还停在那个节点的那一轮上；结果要是节点出边上写的，不填时要有一条没写结果的出边。
     */
    public CommandResult skipJob(Actor actor, SkipJobCommand command) {
        JobEntity job = context.getDb().queryable(JobEntity.class).where(j -> { j.id().eq(command.getJobId()); j.state().eq(JobEntity.FAILED); })
            .singleNotNull(() -> FlowErrors.notFound("job", command.getJobId()));
        if (DeliveryJob.KIND.equals(job.getKind())) {
            context.getDb().deletable(JobEntity.class).allowDeleteStatement(true).whereById(job.getId()).executeRows();
            return new CommandResult(Instances.view(context.getDb(), job.getInstanceId()), List.of(), List.of(), List.of());
        }
        if (!BuiltinJobs.ACTION.equals(job.getKind())) throw FlowErrors.invalid("jobId", "not-skippable");
        String outcome = command.getOutcome() == null || command.getOutcome().isBlank() ? null : command.getOutcome();
        return onInstance(actor, job.getInstanceId(), true, (run, instance) -> {
            if (!BuiltinJobs.stillWaiting(job, instance.getState())) throw FlowErrors.forbidden("job", job.getId(), "not-waiting");
            ProcessNode.Service service = (ProcessNode.Service) run.getGraph().node(BuiltinJobs.nodeOf(job));
            if (outcome == null ? !hasPlainEdge(run.getGraph(), service.getId()) : !skipOutcomes(run.getGraph(), service.getId()).contains(outcome)) {
                throw FlowErrors.invalid("outcome", outcome == null ? "required" : "unknown-outcome");
            }
            context.getDb().deletable(JobEntity.class).allowDeleteStatement(true).whereById(job.getId()).executeRows();
            ServiceBehavior.skip(run, service, job.getId(), outcome, command.getReason());
        });
    }

    /** 跳过时能选的结果：节点出边上写的，按出边的先后、去重 */
    public static List<String> skipOutcomes(ProcessGraph graph, String node) {
        return graph.outgoing(node).stream().map(ProcessEdge::getOutcome).filter(Objects::nonNull).distinct().toList();
    }

    /** 有没写结果的出边（跳过时可以不选结果） */
    private static boolean hasPlainEdge(ProcessGraph graph, String node) {
        return graph.outgoing(node).stream().anyMatch(edge -> edge.getOutcome() == null);
    }

    /**
     * 锁住实例（版本加一）再做：已结束的实例报 {@code flow.instance-not-active}；mustBeActive 时暂停的也报。做完写回。
     */
    private CommandResult onInstance(Actor actor, Long instanceId, boolean mustBeActive, BiConsumer<Execution, InstanceEntity> action) {
        return context.transaction(() -> {
            InstanceEntity instance = Instances.lock(context.getDb(), instanceId);
            boolean active = InstanceEntity.ACTIVE.equals(instance.getStatus());
            boolean suspended = InstanceEntity.SUSPENDED.equals(instance.getStatus());
            if (mustBeActive ? !active : !(active || suspended)) throw FlowErrors.notActive(instance);
            FormValues form = Instances.form(context.getDb(), instanceId);
            Execution run = new Execution(context, actor, context.getClock().instant(), definitions.of(instance), instance, form, instance.getRev());
            action.accept(run, instance);
            run.flush(null);
            return run.toResult();
        });
    }
}
