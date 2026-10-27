package org.dpdns.zerodep.flow.core.engine;

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
import org.dpdns.zerodep.flow.core.entity.CandidateEntity;
import org.dpdns.zerodep.flow.core.entity.CcEntity;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.entity.WorkItemEntity;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.view.WorkItemVO;
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
public final class CollaborationHandler {
    /** 同一个待办多久内只催一次 */
    private static final Duration REMIND_INTERVAL = Duration.ofHours(1);

    private final EngineContext context;
    private final DefinitionCache definitions;
    private final ClaimHandler claims;

    public CollaborationHandler(EngineContext context, ClaimHandler claims) {
        this.context = context;
        this.definitions = context.definitions();
        this.claims = claims;
    }

    /** 转办：原来的待办取消，交给别人一份新的（会签的序号、权重照抄），自己不再参与 */
    public CommandResult transfer(Actor actor, TransferCommand command) {
        return onMyItem(actor, command.workItemId(), (run, item, task) -> {
            requireAllowed(task.allow().transfer(), item, "transfer-not-allowed");
            requireSomeoneElse(actor, command.userId());
            WorkItems.cancel(context.db(), item.getId(), run.now());
            run.closed.add(item);
            run.addEvent(EventEntity.WORK_ITEM_TRANSFERRED, task, null, null, item.getId(), command.reason(), to(command.userId()));
            run.handOver(item, command.userId(), "transfer", null, WorkItemEntity.OPEN);
        });
    }

    /** 委派：原来的待办等着（{@code waiting}），被委派的人办完后回到自己手里（{@link SubItems}） */
    public CommandResult delegate(Actor actor, DelegateCommand command) {
        return onMyItem(actor, command.workItemId(), (run, item, task) -> {
            requireAllowed(task.allow().delegate(), item, "delegate-not-allowed");
            requireSomeoneElse(actor, command.userId());
            WorkItems.changeState(context.db(), item.getId(), WorkItemEntity.OPEN, WorkItemEntity.WAITING);
            run.addEvent(EventEntity.WORK_ITEM_DELEGATED, task, null, null, item.getId(), command.reason(), to(command.userId()));
            run.handOver(item, command.userId(), "delegate", "delegate", WorkItemEntity.OPEN);
        });
    }

    /**
     * 加签：前加签时自己的待办等着、加的人先办；后加签时加的人的待办先生成但等着，自己办完才打开；并加时一起办。
     * 同一个人只加一次，不能加自己。
     */
    public CommandResult addSigners(Actor actor, AddSignersCommand command) {
        return onMyItem(actor, command.workItemId(), (run, item, task) -> {
            requireAllowed(task.allow().addSign(), item, "add-sign-not-allowed");
            String mode = command.mode();
            if (!Set.of("before", "after", "parallel").contains(mode)) throw FlowErrors.invalid("mode", "unknown-add-mode");
            List<Long> users = command.users().stream().distinct().toList();
            if (users.isEmpty()) throw FlowErrors.invalid("users", "required");
            for (Long user : users) requireSomeoneElse(actor, user);
            if ("before".equals(mode)) WorkItems.changeState(context.db(), item.getId(), WorkItemEntity.OPEN, WorkItemEntity.WAITING);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("users", users);
            // 名字存快照：流转记录上显示"加签：张三、李四"，不用再查组织
            data.put("userNames", users.stream().map(context.org()::userName).toList());
            data.put("mode", mode);
            run.addEvent(EventEntity.SIGNERS_ADDED, task, null, null, item.getId(), command.reason(), data);
            String state = "after".equals(mode) ? WorkItemEntity.WAITING : WorkItemEntity.OPEN;
            for (Long user : users) run.handOver(item, user, "addSign", mode, state);
        });
    }

    /** 减签：取消自己加的、还没办的人；前加签的人都取消了，待办回到自己手里 */
    public CommandResult removeSigners(Actor actor, RemoveSignersCommand command) {
        return context.transaction(() -> {
            Locked locked = lock(command.workItemId());
            WorkItemVO item = locked.item();
            if (!actor.id().equals(item.getAssigneeId())) throw FlowErrors.notCandidate(item.getId());
            Execution run = locked.execution(actor);
            ProcessNode.Task task = (ProcessNode.Task) run.graph().node(item.getNode());
            for (WorkItemVO child : WorkItems.children(context.db(), item.getId())) {
                boolean pending = WorkItemEntity.OPEN.equals(child.getState()) || WorkItemEntity.WAITING.equals(child.getState());
                if (!pending || !command.users().contains(child.getAssigneeId())) continue;
                WorkItems.cancel(context.db(), child.getId(), run.now());
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
            Locked locked = lock(command.workItemId());
            WorkItemVO item = locked.item();
            if (!WorkItemEntity.CLAIMED.equals(item.getAssignment()) || !actor.id().equals(item.getAssigneeId())) {
                throw FlowErrors.forbidden("workItem", item.getId(), "not-claimed-by-you");
            }
            Execution run = locked.execution(actor);
            if (!release(item.getId(), actor.id())) throw FlowErrors.notOpen(item.getId());
            run.addEvent(EventEntity.WORK_ITEM_RELEASED, run.graph().node(item.getNode()), null, null, item.getId(), command.reason());
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
            InstanceEntity instance = Instances.find(context.db(), command.instanceId());
            ClaimHandler.requireActive(instance);
            Execution run = new Execution(context, actor, context.clock().instant(), definitions.of(instance), instance, FormValues.empty(),
                instance.getRev());
            Instant notBefore = run.now().minus(REMIND_INTERVAL);
            for (WorkItemVO item : WorkItems.ofInstance(context.db(), instance.getId())) {
                if (!WorkItemEntity.OPEN.equals(item.getState()) || (command.workItemId() != null && !command.workItemId().equals(item.getId()))) continue;
                ProcessNode.Task task = (ProcessNode.Task) run.graph().node(item.getNode());
                if (!task.allow().remind() || !WorkItems.remind(context.db(), item.getId(), run.now(), notBefore)) continue;
                run.addEvent(EventEntity.WORK_ITEM_REMINDED, task, null, null, item.getId(), command.message());
            }
            run.publish();
            return run.result();
        });
    }

    /** 留言：记进流转记录，实例结束后也能留 */
    public CommandResult comment(Actor actor, CommentCommand command) {
        if (command.text() == null || command.text().isBlank()) throw FlowErrors.invalid("text", "required");
        return context.transaction(() -> {
            InstanceEntity instance = Instances.find(context.db(), command.instanceId());
            Execution run = new Execution(context, actor, context.clock().instant(), definitions.of(instance), instance, FormValues.empty(),
                instance.getRev());
            run.addEvent(EventEntity.INSTANCE_COMMENTED, null, null, null, null, command.text());
            run.publish();
            return run.result();
        });
    }

    /** 收件箱负责人指定承办人：只有等我指定的待办能指定；锁住实例，改成直接落到那个人，记一条流转记录 */
    public CommandResult assign(Actor actor, AssignCommand command) {
        if (command.userId() == null) throw FlowErrors.invalid("userId", "required");
        return context.transaction(() -> {
            Locked locked = lock(command.workItemId());
            WorkItemVO item = locked.item();
            Execution run = locked.execution(actor);
            String name = context.org().userName(command.userId());
            if (!WorkItems.assignTo(context.db(), item.getId(), command.userId(), name, WorkItemEntity.DESIGNATED, actor.id())) {
                throw FlowErrors.forbidden("workItem", item.getId(), "not-awaiting-your-designation");
            }
            run.addEvent(EventEntity.WORK_ITEM_ASSIGNED, run.graph().node(item.getNode()), null, null, item.getId(), null, to(command.userId()));
            run.flush(null);
            return run.result();
        });
    }

    /** 抄送（知会）：每人一条抄送、记一条流转记录；实例结束后也能抄送。不改实例，所以不锁 */
    public CommandResult cc(Actor actor, CcCommand command) {
        if (command.users().isEmpty()) throw FlowErrors.invalid("users", "required");
        return context.transaction(() -> {
            InstanceEntity instance = Instances.find(context.db(), command.instanceId());
            Execution run = new Execution(context, actor, context.clock().instant(), definitions.of(instance), instance, FormValues.empty(),
                instance.getRev());
            run.copyTo(null, command.users(), command.message());
            run.publish();
            return run.result();
        });
    }

    /** 读了抄送：一个实例抄送给我的都标成已读，返回标了几条 */
    public int readCc(Actor actor, ReadCcCommand command) {
        Instant now = context.clock().instant();
        return (int) context.db().updatable(CcEntity.class)
            .setColumns(c -> c.readAt().set(now))
            .where(c -> { c.instanceId().eq(command.instanceId()); c.recipientId().eq(actor.id()); c.readAt().isNull(); })
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
            WorkItemVO item = locked.item();
            if (item.getAssigneeId() == null) claims.claim(actor, item);
            else if (!item.getAssigneeId().equals(actor.id())) throw FlowErrors.notCandidate(item.getId());
            Execution run = locked.execution(actor);
            action.apply(run, item, (ProcessNode.Task) run.graph().node(item.getNode()));
            run.flush(null);
            return run.result();
        });
    }

    /** 锁住的实例与重读的待办 */
    private record Locked(InstanceEntity instance, WorkItemVO item, LoadedDefinition definition, EngineContext context) {
        Execution execution(Actor actor) {
            return new Execution(context, actor, context.clock().instant(), definition, instance, Instances.form(context.db(), instance.getId()),
                instance.getRev());
        }
    }

    /** 锁住待办所在的实例（版本加一），重读实例与待办：待办要打开着、实例要进行中 */
    private Locked lock(Long workItemId) {
        WorkItemVO found = WorkItems.find(context.db(), workItemId);
        InstanceEntity instance = Instances.lock(context.db(), found.getInstanceId());
        ClaimHandler.requireActive(instance);
        WorkItemVO item = WorkItems.find(context.db(), workItemId);
        if (!WorkItemEntity.OPEN.equals(item.getState())) throw FlowErrors.notOpen(item.getId());
        return new Locked(instance, item, definitions.of(instance), context);
    }

    /** 认领的人退回收件箱：清掉处理人，这个人以后不能再认领这一条（加一行 excluded 候选）；返回是否改到 */
    private boolean release(Long workItemId, Long userId) {
        long changed = context.db().updatable(WorkItemEntity.class)
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
        context.db().insertable(excluded).executeRows();
        return true;
    }

    /** 节点上关掉了这个动作 */
    private static void requireAllowed(boolean allowed, WorkItemVO item, String reason) {
        if (!allowed) throw FlowErrors.forbidden("workItem", item.getId(), reason);
    }

    /** 转办、委派、加签要交给别人，不能是自己 */
    private static void requireSomeoneElse(Actor actor, Long userId) {
        if (userId == null) throw FlowErrors.invalid("userId", "required");
        if (userId.equals(actor.id())) throw FlowErrors.invalid("userId", "yourself");
    }

    /** 流转记录的附加数据：交给谁（名字存快照） */
    private Map<String, Object> to(Long userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("toUserId", userId);
        data.put("toUserName", context.org().userName(userId));
        return data;
    }
}
