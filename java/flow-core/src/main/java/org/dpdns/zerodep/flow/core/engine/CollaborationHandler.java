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
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.store.InstanceStore.InstanceRecord;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.runtime.FlowEvent;
import org.dpdns.zerodep.flow.model.runtime.WorkItem;

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
            context.stores().workItems().cancel(item.id(), run.now());
            run.closed.add(item);
            run.addEvent(FlowEvent.WORK_ITEM_TRANSFERRED, task, null, null, item.id(), command.reason(), to(command.userId()));
            run.handOver(item, command.userId(), "transfer", null, WorkItem.OPEN);
        });
    }

    /** 委派：原来的待办等着（{@code waiting}），被委派的人办完后回到自己手里（{@link SubItems}） */
    public CommandResult delegate(Actor actor, DelegateCommand command) {
        return onMyItem(actor, command.workItemId(), (run, item, task) -> {
            requireAllowed(task.allow().delegate(), item, "delegate-not-allowed");
            requireSomeoneElse(actor, command.userId());
            context.stores().workItems().changeState(item.id(), WorkItem.OPEN, WorkItem.WAITING);
            run.addEvent(FlowEvent.WORK_ITEM_DELEGATED, task, null, null, item.id(), command.reason(), to(command.userId()));
            run.handOver(item, command.userId(), "delegate", "delegate", WorkItem.OPEN);
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
            if ("before".equals(mode)) context.stores().workItems().changeState(item.id(), WorkItem.OPEN, WorkItem.WAITING);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("users", users);
            // 名字存快照：流转记录上显示"加签：张三、李四"，不用再查组织
            data.put("userNames", users.stream().map(context.org()::userName).toList());
            data.put("mode", mode);
            run.addEvent(FlowEvent.SIGNERS_ADDED, task, null, null, item.id(), command.reason(), data);
            String state = "after".equals(mode) ? WorkItem.WAITING : WorkItem.OPEN;
            for (Long user : users) run.handOver(item, user, "addSign", mode, state);
        });
    }

    /** 减签：取消自己加的、还没办的人；前加签的人都取消了，待办回到自己手里 */
    public CommandResult removeSigners(Actor actor, RemoveSignersCommand command) {
        return context.transaction(() -> {
            Locked locked = lock(command.workItemId());
            WorkItem item = locked.item();
            if (!actor.id().equals(item.assigneeId())) throw FlowErrors.notCandidate(item.id());
            Execution run = locked.execution(actor);
            ProcessNode.Task task = (ProcessNode.Task) run.graph().node(item.node());
            for (WorkItem child : context.stores().workItems().children(item.id())) {
                boolean pending = WorkItem.OPEN.equals(child.state()) || WorkItem.WAITING.equals(child.state());
                if (!pending || !command.users().contains(child.assigneeId())) continue;
                context.stores().workItems().cancel(child.id(), run.now());
                run.closed.add(child);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("userId", child.assigneeId());
                data.put("userName", child.assigneeName());
                run.addEvent(FlowEvent.SIGNERS_REMOVED, task, null, null, child.id(), null, data);
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
            WorkItem item = locked.item();
            if (!WorkItem.CLAIMED.equals(item.assignment()) || !actor.id().equals(item.assigneeId())) {
                throw FlowErrors.forbidden("workItem", item.id(), "not-claimed-by-you");
            }
            Execution run = locked.execution(actor);
            if (!context.stores().workItems().release(item.id(), actor.id())) throw FlowErrors.notOpen(item.id());
            run.addEvent(FlowEvent.WORK_ITEM_RELEASED, run.graph().node(item.node()), null, null, item.id(), command.reason());
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
            InstanceRecord instance = context.stores().instances().find(command.instanceId())
                .orElseThrow(() -> FlowErrors.notFound("instance", command.instanceId()));
            ClaimHandler.requireActive(instance);
            Execution run = new Execution(context, actor, context.clock().instant(), definitions.of(instance), instance, FormValues.empty(),
                instance.rev());
            Instant notBefore = run.now().minus(REMIND_INTERVAL);
            for (WorkItem item : context.stores().workItems().ofInstance(instance.id())) {
                if (!WorkItem.OPEN.equals(item.state()) || (command.workItemId() != null && !command.workItemId().equals(item.id()))) continue;
                ProcessNode.Task task = (ProcessNode.Task) run.graph().node(item.node());
                if (!task.allow().remind() || !context.stores().workItems().remind(item.id(), run.now(), notBefore)) continue;
                run.addEvent(FlowEvent.WORK_ITEM_REMINDED, task, null, null, item.id(), command.message());
            }
            run.publish();
            return run.result();
        });
    }

    /** 留言：记进流转记录，实例结束后也能留 */
    public CommandResult comment(Actor actor, CommentCommand command) {
        if (command.text() == null || command.text().isBlank()) throw FlowErrors.invalid("text", "required");
        return context.transaction(() -> {
            InstanceRecord instance = context.stores().instances().find(command.instanceId())
                .orElseThrow(() -> FlowErrors.notFound("instance", command.instanceId()));
            Execution run = new Execution(context, actor, context.clock().instant(), definitions.of(instance), instance, FormValues.empty(),
                instance.rev());
            run.addEvent(FlowEvent.INSTANCE_COMMENTED, null, null, null, null, command.text());
            run.publish();
            return run.result();
        });
    }

    /** 收件箱负责人指定承办人：只有等我指定的待办能指定；锁住实例，改成直接落到那个人，记一条流转记录 */
    public CommandResult assign(Actor actor, AssignCommand command) {
        if (command.userId() == null) throw FlowErrors.invalid("userId", "required");
        return context.transaction(() -> {
            Locked locked = lock(command.workItemId());
            WorkItem item = locked.item();
            Execution run = locked.execution(actor);
            String name = context.org().userName(command.userId());
            if (!context.stores().workItems().designate(item.id(), actor.id(), command.userId(), name)) {
                throw FlowErrors.forbidden("workItem", item.id(), "not-awaiting-your-designation");
            }
            run.addEvent(FlowEvent.WORK_ITEM_ASSIGNED, run.graph().node(item.node()), null, null, item.id(), null, to(command.userId()));
            run.flush(null);
            return run.result();
        });
    }

    /** 抄送（知会）：每人一条抄送、记一条流转记录；实例结束后也能抄送。不改实例，所以不锁 */
    public CommandResult cc(Actor actor, CcCommand command) {
        if (command.users().isEmpty()) throw FlowErrors.invalid("users", "required");
        return context.transaction(() -> {
            InstanceRecord instance = context.stores().instances().find(command.instanceId())
                .orElseThrow(() -> FlowErrors.notFound("instance", command.instanceId()));
            Execution run = new Execution(context, actor, context.clock().instant(), definitions.of(instance), instance, FormValues.empty(),
                instance.rev());
            run.copyTo(null, command.users(), command.message());
            run.publish();
            return run.result();
        });
    }

    /** 读了抄送：一个实例抄送给我的都标成已读，返回标了几条 */
    public int readCc(Actor actor, ReadCcCommand command) {
        return context.stores().ccs().markRead(command.instanceId(), actor.id(), context.clock().instant());
    }

    // ───────── 共用的步骤 ─────────

    /** 对自己的一个待办做一件事 */
    @FunctionalInterface
    private interface ItemAction {
        void apply(Execution run, WorkItem item, ProcessNode.Task task);
    }

    /** 锁住实例、重读待办；还在收件箱里时候选人先认领；只有它的处理人能做；做完重算、写回 */
    private CommandResult onMyItem(Actor actor, Long workItemId, ItemAction action) {
        return context.transaction(() -> {
            Locked locked = lock(workItemId);
            WorkItem item = locked.item();
            if (item.assigneeId() == null) claims.claim(actor, item);
            else if (!item.assigneeId().equals(actor.id())) throw FlowErrors.notCandidate(item.id());
            Execution run = locked.execution(actor);
            action.apply(run, item, (ProcessNode.Task) run.graph().node(item.node()));
            run.flush(null);
            return run.result();
        });
    }

    /** 锁住的实例与重读的待办 */
    private record Locked(InstanceRecord instance, int rev, WorkItem item, LoadedDefinition definition, EngineContext context) {
        Execution execution(Actor actor) {
            return new Execution(context, actor, context.clock().instant(), definition, instance, formOf(), rev);
        }

        private FormValues formOf() {
            return context.stores().formData().read(instance.id()).map(f -> f.values()).orElse(FormValues.empty());
        }
    }

    /** 锁住待办所在的实例（版本加一），重读实例与待办：待办要打开着、实例要进行中 */
    private Locked lock(Long workItemId) {
        WorkItem found = context.stores().workItems().find(workItemId).orElseThrow(() -> FlowErrors.notFound("workItem", workItemId));
        int rev = context.stores().instances().lock(found.instanceId()).orElseThrow(() -> FlowErrors.notFound("instance", found.instanceId()));
        InstanceRecord instance = context.stores().instances().find(found.instanceId()).orElseThrow();
        ClaimHandler.requireActive(instance);
        WorkItem item = context.stores().workItems().find(workItemId).orElseThrow();
        if (!WorkItem.OPEN.equals(item.state())) throw FlowErrors.notOpen(item.id());
        return new Locked(instance, rev, item, definitions.of(instance), context);
    }

    /** 节点上关掉了这个动作 */
    private static void requireAllowed(boolean allowed, WorkItem item, String reason) {
        if (!allowed) throw FlowErrors.forbidden("workItem", item.id(), reason);
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
