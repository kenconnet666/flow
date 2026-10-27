package org.dpdns.zerodep.flow.core.engine.node;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.dpdns.zerodep.flow.core.engine.Execution;
import org.dpdns.zerodep.flow.core.engine.assignee.AssigneeResolver;
import org.dpdns.zerodep.flow.core.engine.assignee.Assignees;
import org.dpdns.zerodep.flow.core.engine.job.BuiltinJobs;
import org.dpdns.zerodep.flow.core.engine.store.WorkItems;
import org.dpdns.zerodep.flow.core.entity.CandidateEntity;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.entity.WorkItemEntity;
import org.dpdns.zerodep.flow.core.identity.OrgDirectory;
import org.dpdns.zerodep.flow.core.view.workitem.WorkItemVO;
import org.dpdns.zerodep.flow.model.definition.Outcome;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.definition.TaskRules;
import org.dpdns.zerodep.flow.model.error.ErrorDetail;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 人工节点（0005 第 5 节）：到达时解析处理人、生成待办，停下来等人处理。
 * <pre>
 *   arrive
 *     ├─ 会签（multi）：每个参与人一份待办；依次会签只给第一个人，其余排队 ──► nextSigner 轮到下一个
 *     ├─ 处理人为空：按 emptyAssignee 自动通过、转给管理员或报错
 *     ├─ 自动跳过（autoSkip）：处理人是发起人或上一步的处理人时不生成待办，按"同意"往下走
 *     └─ 生成一个待办：一个人时直接落到他，否则进收件箱；有时限的排上超时、提醒、无人认领的作业
 * </pre>
 * 转办、委派、加签生成的待办也经这里（{@link #handOver}），会签结束时取消其余的份（{@link #closeCountersign}）。
 */
public class TaskBehavior {
    private TaskBehavior() { }

    /** 待办从哪来：转办、委派、加签的人与原来的待办；流转生成的待办是 {@link #NONE} */
    @Getter
    @AllArgsConstructor
    private static class Origin {
        static final Origin NONE = new Origin(null, null, null, null);

        /** 转办、委派、加签的人 */
        private final Long fromUserId;
        /** 那个人的名字（快照） */
        private final String fromUserName;
        /** 委派、加签时原来的待办；转办时为 null（原来的那一份取消了） */
        private final Long parentItemId;
        /** {@code delegate}、{@code before}、{@code after}、{@code parallel}；转办时为 null */
        private final String addMode;
    }

    /** 到达人工节点：轮次加一，按上面的顺序决定生成什么 */
    public static void arrive(Execution run, ProcessNode.Task task, String arrival) {
        run.updateState(s -> s.enter(task.getId()));
        if (task.getMulti() != null) {
            arriveAtCountersign(run, task, arrival);
            return;
        }
        Assignees who = assigneesFor(run, task, arrival);
        if (who.isEmpty()) {
            handleEmptyAssignee(run, task);
            return;
        }
        String skip = skipReason(run, task, arrival, who);
        if (skip != null) {
            Long user = who.directUser();
            run.addEvent(EventEntity.NODE_SKIPPED, task, null, approveOutcome(task), null, null,
                Map.of("reason", skip, "userId", user, "userName", run.getContext().getOrg().userName(user)));
            run.leave(task.getId(), approveOutcome(task));
            return;
        }
        createWorkItem(run, task, arrival, who, null, null);
    }

    /**
     * 这次到达由谁处理：退回来的、重新提交直接回来的，落到这个节点原来的处理人（没人处理过时照常解析）；提级来的只进收件箱，
     * 不直接落到人（0005 第 6 节连线的 kind）；其余照节点上写的来源解析。
     */
    private static Assignees assigneesFor(Execution run, ProcessNode.Task task, String arrival) {
        InstanceEntity instance = run.getInstance();
        if ("return".equals(arrival) || "resubmit".equals(arrival)) {
            Long last = WorkItems.lastCompletedBy(run.getContext().getDb(), instance.getId(), task.getId());
            if (last != null) return Assignees.of(last);
        }
        Assignees who = run.getAssignees().resolve(task, run.getGraph(), instance, run::read, run.getPicks());
        return "escalate".equals(arrival) && !who.getInboxes().isEmpty() ? who.inboxesOnly() : who;
    }

    /**
     * 自动跳过（0005 的 2.1 {@code autoSkip}，节点上写的盖过流程级的）：顺着流转到达、直接落到一个人、节点有"同意"的结果时，
     * 这个人就是上一步的处理人（{@code sameAsPrevious}）或发起人（{@code initiator}）就不生成待办。
     * 退回、重新提交、提级、跳转来的不跳过：那是要这个人再看一次。返回跳过的原因，不跳过时为 null。
     */
    private static String skipReason(Execution run, ProcessNode.Task task, String arrival, Assignees who) {
        if (!("start".equals(arrival) || "forward".equals(arrival)) || !who.isDirect() || approveOutcome(task) == null) return null;
        Map<String, Object> rules = task.getInfo().getAutoSkip().isEmpty()
            ? run.getGraph().definition().getSettings().getAutoSkip()
            : task.getInfo().getAutoSkip();
        Long user = who.directUser();
        InstanceEntity instance = run.getInstance();
        if (Boolean.TRUE.equals(rules.get("initiator")) && user.equals(instance.getStartedBy())) return "initiator";
        if (Boolean.TRUE.equals(rules.get("sameAsPrevious")) && user.equals(previousHandler(run))) return "sameAsPrevious";
        return null;
    }

    /** 上一步的处理人：这个实例最近处理完的待办的处理人；还没人处理过时是发起人（发起算第一步） */
    private static Long previousHandler(Execution run) {
        Long last = WorkItems.lastCompletedBy(run.getContext().getDb(), run.getInstance().getId(), null);
        return last != null ? last : run.getInstance().getStartedBy();
    }

    /** 节点上第一个"同意"类的结果；没有时为 null */
    private static String approveOutcome(ProcessNode.Task task) {
        return task.getOutcomes().stream().filter(o -> "approve".equals(o.kindOrDefault())).map(Outcome::getKey).findFirst().orElse(null);
    }

    /**
     * 处理人解析为空（0005 的 2.1 {@code emptyAssignee}，节点上写的盖过流程级的）：{@code autoPass} 按第一个"同意"的结果自动通过；
     * {@code admin} 转给流程管理员（{@code OrgDirectory.administrators}，几个人时进收件箱）；其余（{@code error}，默认）报
     * {@code assignee-empty}，整个命令回滚。
     */
    private static void handleEmptyAssignee(Execution run, ProcessNode.Task task) {
        String rule = task.getInfo().getEmptyAssignee() != null
            ? task.getInfo().getEmptyAssignee()
            : run.getGraph().definition().getSettings().getEmptyAssignee();
        switch (rule == null ? "error" : rule) {
            case "autoPass" -> run.leave(task.getId(), approveOutcome(task));
            case "admin" -> {
                List<Long> admins = run.getContext().getOrg().administrators();
                if (admins.isEmpty()) throw nobodyCanHandle(task);
                Assignees who = Assignees.empty();
                who.getUsers().addAll(admins);
                createWorkItem(run, task, "emptyAssignee", who, null, null);
            }
            default -> throw nobodyCanHandle(task);
        }
    }

    private static FlowException nobodyCanHandle(ProcessNode.Task task) {
        return new FlowException(FlowErrorCode.VALIDATION_FAILED, "nobody can handle node " + task.getId(),
            List.of(ErrorDetail.node(task.getId(), "assignee-empty")));
    }

    // ───────── 会签 ─────────

    /**
     * 到达会签节点：每个参与人一份待办（直接落到人）；依次会签只给第一个人，其余排队（{@code RunState.queue}），
     * 前一个人同意后轮到下一个（{@link #nextSigner}）。
     */
    private static void arriveAtCountersign(Execution run, ProcessNode.Task task, String arrival) {
        List<AssigneeResolver.Participant> participants = run.getAssignees().participants(task, run.getInstance(), run::read, run.getPicks());
        if (participants.isEmpty()) {
            handleEmptyAssignee(run, task);
            return;
        }
        boolean sequential = "sequential".equals(task.getMulti().getComplete());
        boolean weighted = "votes".equals(task.getMulti().getComplete());
        List<AssigneeResolver.Participant> first = sequential ? participants.subList(0, 1) : participants;
        for (int i = 0; i < first.size(); i++) {
            AssigneeResolver.Participant p = first.get(i);
            createWorkItem(run, task, arrival, Assignees.of(p.getUser()), i + 1, weighted ? p.getWeight() : null);
        }
        if (sequential) {
            List<Long> rest = participants.subList(1, participants.size()).stream().map(AssigneeResolver.Participant::getUser).toList();
            run.updateState(s -> s.queue(task.getId(), rest));
        }
    }

    /** 依次会签：轮到排队的下一个人（前一个人同意了） */
    public static void nextSigner(Execution run, ProcessNode.Task task, int seq) {
        List<Long> queue = run.getInstance().getState().queue(task.getId());
        run.updateState(s -> s.queue(task.getId(), queue.subList(1, queue.size())));
        createWorkItem(run, task, "forward", Assignees.of(queue.get(0)), seq, null);
    }

    /** 会签节点结束了：其余还开着的待办取消，排队的人也不用再等 */
    public static void closeCountersign(Execution run, ProcessNode.Task task) {
        run.updateState(s -> s.queue(task.getId(), List.of()));
        run.cancelItems(WorkItems.cancelOpenAt(run.getContext().getDb(), run.getInstance().getId(), List.of(task.getId()), run.getNow()));
    }

    // ───────── 生成待办 ─────────

    /**
     * 把一个待办交给别人（转办、委派、加签）：直接落到这个人，会签的序号与权重、截止时间照抄原来的那一份。
     *
     * @param from    原来的待办
     * @param arrival {@code transfer}、{@code delegate}、{@code addSign}
     * @param addMode 委派、加签的方式；转办时为 null（新的待办不挂在原来的下面）
     * @param state   {@code open}，或后加签时的 {@code waiting}
     */
    public static WorkItemVO handOver(Execution run, WorkItemVO from, Long userId, String arrival, String addMode, String state) {
        ProcessNode.Task task = (ProcessNode.Task) run.getGraph().node(from.getNode());
        Origin origin = new Origin(run.getActor().getId(), run.getActor().getName(), addMode == null ? null : from.getId(), addMode);
        return createWorkItem(run, task, arrival, Assignees.of(userId), from.getSignSeq(), from.getWeight(), origin, state, from.getDueAt());
    }

    /** 流转生成的待办：截止时间按节点的时限算 */
    private static WorkItemVO createWorkItem(Execution run, ProcessNode.Task task, String arrival, Assignees who, Integer signSeq, Integer weight) {
        Instant due = task.getDue() == null ? null : run.getDeadlines().of(task.getDue(), run.getNow(), run::read);
        return createWorkItem(run, task, arrival, who, signSeq, weight, Origin.NONE, WorkItemEntity.OPEN, due);
    }

    /**
     * 生成一个待办：只有一个人、没有收件箱时直接落到他；否则进收件箱（{@code pool}），人和角色、部门都存成候选，谁先认领谁办。
     *
     * @param signSeq 会签时是第几个参与人；不是会签时为 null
     * @param weight  票签的权重；不是票签时为 null
     * @param state   {@code open}，或后加签时先生成、原来的人处理完才打开的 {@code waiting}
     * @param dueAt   截止时间；没有时限时为 null
     */
    private static WorkItemVO createWorkItem(Execution run, ProcessNode.Task task, String arrival, Assignees named, Integer signSeq,
                                             Integer weight, Origin origin, String state, Instant dueAt) {
        InstanceEntity instance = run.getInstance();
        OrgDirectory org = run.getContext().getOrg();
        Map<Long, Long> standIns = standIns(run, named.getUsers());
        Assignees who = named.replacing(standIns);
        Long assigneeId = who.isDirect() ? who.directUser() : null;
        String assigneeName = assigneeId == null ? null : org.userName(assigneeId);
        Long onBehalfOf = assigneeId == null ? null
            : standIns.entrySet().stream().filter(e -> e.getValue().equals(assigneeId)).map(Map.Entry::getKey).findFirst().orElse(null);
        CandidateEntity inbox = who.firstInbox();

        WorkItemEntity row = new WorkItemEntity();
        row.setInstanceId(instance.getId());
        row.setProcessKey(instance.getProcessKey());
        row.setPriority(instance.getPriority());
        row.setNode(task.getId());
        row.setNodeName(task.getName());
        row.setRound(instance.getState().round(task.getId()));
        row.setSignSeq(signSeq);
        row.setWeight(weight);
        row.setState(state);
        row.setAssignment(assigneeId != null ? WorkItemEntity.DIRECT : WorkItemEntity.POOL);
        row.setArrival(arrival);
        row.setInboxKind(inbox == null ? null : inbox.getKind());
        row.setInboxId(inbox == null ? null : inbox.getTargetId());
        row.setAssigneeId(assigneeId);
        row.setAssigneeName(assigneeName);
        row.setOnBehalfOfId(onBehalfOf);
        row.setOnBehalfOfName(onBehalfOf == null ? null : org.userName(onBehalfOf));
        row.setFromUserId(origin.getFromUserId());
        row.setFromUserName(origin.getFromUserName());
        row.setParentItemId(origin.getParentItemId());
        row.setAddMode(origin.getAddMode());
        row.setCreatedAt(run.getNow());
        row.setDueAt(dueAt);
        row.setRemindCount(0);
        row.setNeedsDesignation(false);
        row.setRev(0);
        WorkItemVO item = WorkItems.insert(run.getContext().getDb(), row, assigneeId != null ? List.of() : who.candidates());
        run.getCreated().add(item);
        scheduleTimeouts(run, task, item);
        run.addEvent(EventEntity.WORK_ITEM_CREATED, task, null, null, item.getId(), null, createdData(org, assigneeName, standIns));
        return item;
    }

    /**
     * 不在岗的人换成谁（{@link OrgDirectory#standIn}）：代理人也不在岗时接着往下找，绕回来就停在最后一个没走过的人。
     * 返回 原来的人 → 代理人；都在岗时为空。
     */
    private static Map<Long, Long> standIns(Execution run, Set<Long> users) {
        OrgDirectory org = run.getContext().getOrg();
        String processKey = run.getInstance().getProcessKey();
        Map<Long, Long> out = new LinkedHashMap<>();
        for (Long user : users) {
            Set<Long> seen = new HashSet<>(Set.of(user));
            Long current = user;
            Optional<Long> next;
            while ((next = org.standIn(current, processKey, run.getNow())).isPresent() && seen.add(next.get())) current = next.get();
            if (!current.equals(user)) out.put(user, current);
        }
        return out;
    }

    /**
     * 到达记录的附加数据：直接落到人的记下名字快照，流转记录上显示"到达 某节点 · 处理人 张三"（在收件箱里的没有）；
     * 换了代理人的记下谁代谁（{@code standIns}），看记录就知道为什么不是原来的人。
     */
    private static Map<String, Object> createdData(OrgDirectory org, String assigneeName, Map<Long, Long> standIns) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (assigneeName != null) data.put("assigneeName", assigneeName);
        if (!standIns.isEmpty()) {
            data.put("standIns", standIns.entrySet().stream().map(e -> {
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("userId", e.getKey());
                one.put("userName", org.userName(e.getKey()));
                one.put("standInId", e.getValue());
                one.put("standInName", org.userName(e.getValue()));
                return one;
            }).toList());
        }
        return data;
    }

    /**
     * 待办的超时、提醒、无人认领（0005 的 5.3）各排一个作业：有截止时间时到期那一刻一个超时作业（超时怎么办见节点的 overdue）、
     * 每个提醒一个（到期前的；超时后每隔一段时间的从到期起算）；进了收件箱、写了 unclaimed 时到点看还有没有人认领。
     * 待办先办完了也不用删：作业到时候看到待办不再打开就跳过（BuiltinJobs）。
     */
    private static void scheduleTimeouts(Execution run, ProcessNode.Task task, WorkItemVO item) {
        Instant now = run.getNow();
        if (item.getDueAt() != null) {
            run.schedule(BuiltinJobs.OVERDUE, item.getId(), item.getDueAt(), Map.of(), BuiltinJobs.MAX_ATTEMPTS);
            for (int i = 0; i < task.getReminders().size(); i++) {
                TaskRules.Reminder reminder = task.getReminders().get(i);
                Instant at = reminder.getBefore() != null
                    ? run.getDeadlines().minus(reminder.getBefore(), item.getDueAt())
                    : run.getDeadlines().plus(reminder.getEvery(), item.getDueAt());
                if (at.isAfter(now)) {
                    run.schedule(BuiltinJobs.REMIND, item.getId(), at, Map.of(BuiltinJobs.REMINDER, i, BuiltinJobs.SENT, 0), BuiltinJobs.MAX_ATTEMPTS);
                }
            }
        }
        if (task.getUnclaimed() != null && item.getAssigneeId() == null) {
            run.schedule(BuiltinJobs.UNCLAIMED, item.getId(), run.getDeadlines().plus(task.getUnclaimed().getAfter(), now), Map.of(),
                BuiltinJobs.MAX_ATTEMPTS);
        }
    }
}
