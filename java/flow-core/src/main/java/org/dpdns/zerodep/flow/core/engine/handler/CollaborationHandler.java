package org.dpdns.zerodep.flow.core.engine.handler;

import lombok.Data;
import org.dpdns.zerodep.flow.core.command.AddSignersCommand;
import org.dpdns.zerodep.flow.core.command.AssignCommand;
import org.dpdns.zerodep.flow.core.command.CcCommand;
import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.command.CommentCommand;
import org.dpdns.zerodep.flow.core.command.DelegateCommand;
import org.dpdns.zerodep.flow.core.command.ReadCcCommand;
import org.dpdns.zerodep.flow.core.command.ReleaseCommand;
import org.dpdns.zerodep.flow.core.command.RemindCommand;
import org.dpdns.zerodep.flow.core.command.RemoveSignersCommand;
import org.dpdns.zerodep.flow.core.command.TransferCommand;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.engine.DefinitionCache;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.Execution;
import org.dpdns.zerodep.flow.core.engine.FlowErrors;
import org.dpdns.zerodep.flow.core.engine.LoadedDefinition;
import org.dpdns.zerodep.flow.core.engine.rule.SubItems;
import org.dpdns.zerodep.flow.core.engine.store.Instances;
import org.dpdns.zerodep.flow.core.engine.store.WorkItems;
import org.dpdns.zerodep.flow.core.entity.CandidateEntity;
import org.dpdns.zerodep.flow.core.entity.CcEntity;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.entity.WorkItemEntity;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.view.workitem.WorkItemVO;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 处理之外的协作（0006 的 3.1–3.3）：转办、委派、加签与减签、退回收件箱、催办、留言、抄送与读抄送。
 * <p>动待办的命令（转办、委派、加签、减签、退回收件箱）同处理一样：锁住实例，重读待办，只有它的处理人能做
 * （还在收件箱里时候选人先认领），节点上 {@code allow} 关掉的动作不能做；最后重算正在处理的节点、写流转记录。
 * <p>委派、加签生成的待办挂在原来的待办下面（{@code parentItemId}、{@code addMode}），它们办完之后怎样回到原来的待办
 * 见 {@link SubItems}。
 */
public class CollaborationHandler {
    /** 同一个待办多久内只催一次 */
    private static final Duration REMIND_INTERVAL = Duration.ofHours(1);

    private final EngineContext context;
    private final DefinitionCache definitions;
    private final ClaimHandler claims;

    public CollaborationHandler(EngineContext context, ClaimHandler claims) {
        this.context = context;
        this.definitions = context.getDefinitions();
        this.claims = claims;
    }

    /** 转办：原来的待办取消，交给别人一份新的（会签的序号、权重照抄），自己不再参与 */
    public CommandResult transfer(Actor actor, TransferCommand command) {
        return onMyItem(actor, command.getWorkItemId(), (run, item, task) -> {
            requireAllowed(task.getAllow().isTransfer(), item, "transfer-not-allowed");
            requireSomeoneElse(actor, command.getUserId());
            WorkItems.cancel(context.getDb(), item.getId(), run.now());
            run.closed.add(item);
            run.addEvent(EventEntity.WORK_ITEM_TRANSFERRED, task, null, null, item.getId(), command.getReason(), to(command.getUserId()));
            run.handOver(item, command.getUserId(), "transfer", null, WorkItemEntity.OPEN);
        });
    }

    /** 委派：原来的待办等着（{@code waiting}），被委派的人办完后回到自己手里（{@link SubItems}） */
    public CommandResult delegate(Actor actor, DelegateCommand command) {
        return onMyItem(actor, command.getWorkItemId(), (run, item, task) -> {
            requireAllowed(task.getAllow().isDelegate(), item, "delegate-not-allowed");
            requireSomeoneElse(actor, command.getUserId());
            WorkItems.changeState(context.getDb(), item.getId(), WorkItemEntity.OPEN, WorkItemEntity.WAITING);
            run.addEvent(EventEntity.WORK_ITEM_DELEGATED, task, null, null, item.getId(), command.getReason(), to(command.getUserId()));
            run.handOver(item, command.getUserId(), "delegate", "delegate", WorkItemEntity.OPEN);
        });
    }

    /**
     * 加签：前加签时自己的待办等着、加的人先办；后加签时加的人的待办先生成但等着，自己办完才打开；并加时一起办。
     * 同一个人只加一次，不能加自己。
     */
    public CommandResult addSigners(Actor actor, AddSignersCommand command) {
        return onMyItem(actor, command.getWorkItemId(), (run, item, task) -> {
            requireAllowed(task.getAllow().isAddSign(), item, "add-sign-not-allowed");
            String mode = command.getMode();
            if (!Set.of("before", "after", "parallel").contains(mode)) throw FlowErrors.invalid("mode", "unknown-add-mode");
            List<Long> users = command.getUsers().stream().distinct().toList();
            if (users.isEmpty()) throw FlowErrors.invalid("users", "required");
            for (Long user : users) requireSomeoneElse(actor, user);
            if ("before".equals(mode)) WorkItems.changeState(context.getDb(), item.getId(), WorkItemEntity.OPEN, WorkItemEntity.WAITING);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("users", users);
            // 名字存快照：流转记录上显示"加签：张三、李四"，不用再查组织
            data.put("userNames", users.stream().map(context.getOrg()::userName).toList());
            data.put("mode", mode);
            run.addEvent(EventEntity.SIGNERS_ADDED, task, null, null, item.getId(), command.getReason(), data);
            String state = "after".equals(mode) ? WorkItemEntity.WAITING : WorkItemEntity.OPEN;
            for (Long user : users) run.handOver(item, user, "addSign", mode, state);
        });
    }

    /** 减签：取消自己加的、还没办的人；前加签的人都取消了，待办回到自己手里 */
    public CommandResult removeSigners(Actor actor, RemoveSignersCommand command) {
        return context.transaction(() -> {
            Locked locked = lock(command.getWorkItemId());
            WorkItemVO item = locked.getItem();
            if (!actor.getId().equals(item.getAssigneeId())) throw FlowErrors.notCandidate(item.getId());
            Execution run = locked.execution(actor);
            ProcessNode.Task task = (ProcessNode.Task) run.graph().node(item.getNode());
            for (WorkItemVO child : WorkItems.children(context.getDb(), item.getId())) {
                boolean pending = WorkItemEntity.OPEN.equals(child.getState()) || WorkItemEntity.WAITING.equals(child.getState());
                if (!pending || !command.getUsers().contains(child.getAssigneeId())) continue;
                WorkItems.cancel(context.getDb(), child.getId(), run.now());
                run.closed.add(child);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("userId", child.getAssigneeId());
                data.put("userName", child.getAssigneeName());
                run.addEvent(EventEntity.SIGNERS_REMOVED, task, null, null, child.getId(), null, data);
            }
            SubItems.resumeIfSettled(context, run, item);
            run.flush(null);
            return run.result();
        });
    }

    /** 退回收件箱：认领了又不办，放回去；自己以后不能再认领这一条 */
    public CommandResult release(Actor actor, ReleaseCommand command) {
        return context.transaction(() -> {
            Locked locked = lock(command.getWorkItemId());
            WorkItemVO item = locked.getItem();
            if (!WorkItemEntity.CLAIMED.equals(item.getAssignment()) || !actor.getId().equals(item.getAssigneeId())) {
                throw FlowErrors.forbidden("workItem", item.getId(), "not-claimed-by-you");
            }
            Execution run = locked.execution(actor);
            if (!release(item.getId(), actor.getId())) throw FlowErrors.notOpen(item.getId());
            run.addEvent(EventEntity.WORK_ITEM_RELEASED, run.graph().node(item.getNode()), null, null, item.getId(), command.getReason());
            run.flush(null);
            return run.result();
        });
    }

    /**
     * 催办：给待办的处理人一个提醒，通知由接入方监听流转记录去发。同一个待办一小时内只记一次；
     * 节点上 {@code allow.remind} 关掉的不催。不改实例，所以不锁。
     */
    public CommandResult remind(Actor actor, RemindCommand command) {
        return context.transaction(() -> {
            InstanceEntity instance = Instances.find(context.getDb(), command.getInstanceId());
            ClaimHandler.requireActive(instance);
            Execution run = new Execution(context, actor, context.getClock().instant(), definitions.of(instance), instance, FormValues.empty(),
                instance.getRev());
            Instant notBefore = run.now().minus(REMIND_INTERVAL);
            for (WorkItemVO item : WorkItems.ofInstance(context.getDb(), instance.getId())) {
                if (!WorkItemEntity.OPEN.equals(item.getState()) || (command.getWorkItemId() != null && !command.getWorkItemId().equals(item.getId()))) continue;
                ProcessNode.Task task = (ProcessNode.Task) run.graph().node(item.getNode());
                if (!task.getAllow().isRemind() || !WorkItems.remind(context.getDb(), item.getId(), run.now(), notBefore)) continue;
                run.addEvent(EventEntity.WORK_ITEM_REMINDED, task, null, null, item.getId(), command.getMessage());
            }
            run.publish();
            return run.result();
        });
    }

    /** 留言：记进流转记录，实例结束后也能留 */
    public CommandResult comment(Actor actor, CommentCommand command) {
        if (command.getText() == null || command.getText().isBlank()) throw FlowErrors.invalid("text", "required");
        return context.transaction(() -> {
            InstanceEntity instance = Instances.find(context.getDb(), command.getInstanceId());
            Execution run = new Execution(context, actor, context.getClock().instant(), definitions.of(instance), instance, FormValues.empty(),
                instance.getRev());
            run.addEvent(EventEntity.INSTANCE_COMMENTED, null, null, null, null, command.getText());
            run.publish();
            return run.result();
        });
    }

    /** 收件箱负责人指定承办人：只有等我指定的待办能指定；锁住实例，改成直接落到那个人，记一条流转记录 */
    public CommandResult assign(Actor actor, AssignCommand command) {
        if (command.getUserId() == null) throw FlowErrors.invalid("userId", "required");
        return context.transaction(() -> {
            Locked locked = lock(command.getWorkItemId());
            WorkItemVO item = locked.getItem();
            Execution run = locked.execution(actor);
            String name = context.getOrg().userName(command.getUserId());
            if (!WorkItems.assignTo(context.getDb(), item.getId(), command.getUserId(), name, WorkItemEntity.DESIGNATED, actor.getId())) {
                throw FlowErrors.forbidden("workItem", item.getId(), "not-awaiting-your-designation");
            }
            run.addEvent(EventEntity.WORK_ITEM_ASSIGNED, run.graph().node(item.getNode()), null, null, item.getId(), null, to(command.getUserId()));
            run.flush(null);
            return run.result();
        });
    }

    /** 抄送（知会）：每人一条抄送、记一条流转记录；实例结束后也能抄送。不改实例，所以不锁 */
    public CommandResult cc(Actor actor, CcCommand command) {
        if (command.getUsers().isEmpty()) throw FlowErrors.invalid("users", "required");
        return context.transaction(() -> {
            InstanceEntity instance = Instances.find(context.getDb(), command.getInstanceId());
            Execution run = new Execution(context, actor, context.getClock().instant(), definitions.of(instance), instance, FormValues.empty(),
                instance.getRev());
            run.copyTo(null, command.getUsers(), command.getMessage());
            run.publish();
            return run.result();
        });
    }

    /** 读了抄送：一个实例抄送给我的都标成已读，返回标了几条 */
    public int readCc(Actor actor, ReadCcCommand command) {
        Instant now = context.getClock().instant();
        return (int) context.getDb().updatable(CcEntity.class)
            .setColumns(c -> c.readAt().set(now))
            .where(c -> { c.instanceId().eq(command.getInstanceId()); c.recipientId().eq(actor.getId()); c.readAt().isNull(); })
            .executeRows();
    }

    // ───────── 共用的步骤 ─────────

    /** 对自己的一个待办做一件事 */
    @FunctionalInterface
    private interface ItemAction {
        void apply(Execution run, WorkItemVO item, ProcessNode.Task task);
    }

    /** 锁住实例、重读待办；还在收件箱里时候选人先认领；只有它的处理人能做；做完重算、写回 */
    private CommandResult onMyItem(Actor actor, Long workItemId, ItemAction action) {
        return context.transaction(() -> {
            Locked locked = lock(workItemId);
            WorkItemVO item = locked.getItem();
            if (item.getAssigneeId() == null) claims.claim(actor, item);
            else if (!item.getAssigneeId().equals(actor.getId())) throw FlowErrors.notCandidate(item.getId());
            Execution run = locked.execution(actor);
            action.apply(run, item, (ProcessNode.Task) run.graph().node(item.getNode()));
            run.flush(null);
            return run.result();
        });
    }

    /** 锁住的实例与重读的待办 */
    @Data
    private static class Locked {
        private final InstanceEntity instance;
        private final WorkItemVO item;
        private final LoadedDefinition definition;
        private final EngineContext context;

        Execution execution(Actor actor) {
            return new Execution(context, actor, context.getClock().instant(), definition, instance, Instances.form(context.getDb(), instance.getId()),
                instance.getRev());
        }
    }

    /** 锁住待办所在的实例（版本加一），重读实例与待办：待办要打开着、实例要进行中 */
    private Locked lock(Long workItemId) {
        WorkItemVO found = WorkItems.find(context.getDb(), workItemId);
        InstanceEntity instance = Instances.lock(context.getDb(), found.getInstanceId());
        ClaimHandler.requireActive(instance);
        WorkItemVO item = WorkItems.find(context.getDb(), workItemId);
        if (!WorkItemEntity.OPEN.equals(item.getState())) throw FlowErrors.notOpen(item.getId());
        return new Locked(instance, item, definitions.of(instance), context);
    }

    /** 认领的人退回收件箱：清掉处理人，这个人以后不能再认领这一条（加一行 excluded 候选）；返回是否改到 */
    private boolean release(Long workItemId, Long userId) {
        long changed = context.getDb().updatable(WorkItemEntity.class)
            .setColumns(w -> {
                w.assigneeId().set((Long) null);
                w.assigneeName().set((String) null);
                w.claimedAt().set((Instant) null);
                w.assignment().set(WorkItemEntity.POOL);
                w.rev().increment();
            })
            .where(w -> { w.id().eq(workItemId); w.state().eq(WorkItemEntity.OPEN); w.assigneeId().eq(userId); w.assignment().eq(WorkItemEntity.CLAIMED); })
            .executeRows();
        if (changed != 1) return false;
        CandidateEntity excluded = new CandidateEntity(CandidateEntity.EXCLUDED, userId);
        excluded.setWorkItemId(workItemId);
        context.getDb().insertable(excluded).executeRows();
        return true;
    }

    /** 节点上关掉了这个动作 */
    private static void requireAllowed(boolean allowed, WorkItemVO item, String reason) {
        if (!allowed) throw FlowErrors.forbidden("workItem", item.getId(), reason);
    }

    /** 转办、委派、加签要交给别人，不能是自己 */
    private static void requireSomeoneElse(Actor actor, Long userId) {
        if (userId == null) throw FlowErrors.invalid("userId", "required");
        if (userId.equals(actor.getId())) throw FlowErrors.invalid("userId", "yourself");
    }

    /** 流转记录的附加数据：交给谁（名字存快照） */
    private Map<String, Object> to(Long userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("toUserId", userId);
        data.put("toUserName", context.getOrg().userName(userId));
        return data;
    }
}
