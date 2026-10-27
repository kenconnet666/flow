package org.dpdns.zerodep.flow.core.engine.job;

import org.dpdns.zerodep.flow.core.engine.DefinitionCache;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.Execution;
import org.dpdns.zerodep.flow.core.engine.SystemActor;
import org.dpdns.zerodep.flow.core.engine.handler.CompleteHandler;
import org.dpdns.zerodep.flow.core.engine.node.ReceiveBehavior;
import org.dpdns.zerodep.flow.core.engine.node.ServiceBehavior;
import org.dpdns.zerodep.flow.core.engine.node.TimerBehavior;
import org.dpdns.zerodep.flow.core.engine.state.RunState;
import org.dpdns.zerodep.flow.core.engine.store.Instances;
import org.dpdns.zerodep.flow.core.engine.store.WorkItems;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.entity.JobEntity;
import org.dpdns.zerodep.flow.core.entity.WaitEntity;
import org.dpdns.zerodep.flow.core.entity.WorkItemEntity;
import org.dpdns.zerodep.flow.core.spi.FormValues;
import org.dpdns.zerodep.flow.core.spi.JobHandler;
import org.dpdns.zerodep.flow.core.view.workitem.WorkItemVO;
import org.dpdns.zerodep.flow.model.definition.Outcome;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.definition.TaskRules;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * 内置的四种作业（0005 的 5.3 与定时节点）。作业由 {@link Execution} 在待办生成、到达定时节点时排好，到点由 {@link JobRunner} 调：
 * <ul>
 *   <li>{@code timer}：定时节点到点了，往下走；</li>
 *   <li>{@code overdue}：待办超时，记一条"超时"，再按节点的 {@code overdue} 处理——{@code mark} 只记（默认）、
 *       {@code complete} 按指定的结果自动处理、{@code escalate} 按提级的结果走；</li>
 *   <li>{@code remind}：提醒处理人（记一条催办，通知由接入方监听流转记录去发）；超时后每隔一段时间的提醒排下一次；</li>
 *   <li>{@code unclaimed}：进收件箱很久没人认领，提醒，或转收件箱的负责人指定；</li>
 *   <li>{@code action}：异步的自动节点做动作，失败按节点的 retry 重试，重试用完在实例上记一条 {@code job.failed}（成了管理端的"异常"），
 *       节点写了 {@code onFailure: "error"} 时实例同时暂停；</li>
 *   <li>{@code receiveTimeout}：等待节点等超时，记一条 {@code node.overdue}，节点的 overdue 写了 complete 时按那个结果往下走。</li>
 * </ul>
 * 都先锁住实例再做，操作人是 {@link SystemActor}。待办已经办完（不再打开）、实例已经结束的作业直接跳过；实例暂停时往后推一小时。
 */
public class BuiltinJobs {
    public static final String TIMER = "timer";
    public static final String OVERDUE = "overdue";
    public static final String REMIND = "remind";
    public static final String UNCLAIMED = "unclaimed";
    public static final String ACTION = "action";
    public static final String RECEIVE_TIMEOUT = "receiveTimeout";

    /** 作业数据里的键：定时节点、它的轮次、第几个提醒、提醒过几次 */
    public static final String NODE = "node";
    public static final String ROUND = "round";
    public static final String REMINDER = "reminder";
    public static final String SENT = "sent";
    /** 自动节点失败后隔多少秒重试（节点的 retry.backoff 算好的） */
    public static final String BACKOFF_SECONDS = "backoffSeconds";

    /** 内置作业最多试几次 */
    public static final int MAX_ATTEMPTS = 3;
    /** 实例暂停时往后推多久再看 */
    private static final Duration POSTPONE = Duration.ofHours(1);

    private final EngineContext context;
    private final DefinitionCache definitions;
    private final CompleteHandler completions;

    public BuiltinJobs(EngineContext context, CompleteHandler completions) {
        this.context = context;
        this.definitions = context.getDefinitions();
        this.completions = completions;
    }

    /** 内置作业的处理 */
    public List<JobHandler> handlers() {
        return List.of(handler(TIMER, this::timer), handler(OVERDUE, this::overdue), handler(REMIND, this::remind),
            handler(UNCLAIMED, this::unclaimed), new ActionJob(), handler(RECEIVE_TIMEOUT, this::receiveTimeout));
    }

    /** 异步的自动节点：实例还停在这个节点的这一轮上时做动作、往下走；按节点的 retry.backoff 重试，重试用完记一笔 */
    private class ActionJob implements JobHandler {
        @Override public String kind() { return ACTION; }

        @Override
        public Next run(JobEntity job, Instant now) {
            return onInstance(job, now, run -> {
                if (stillWaiting(job, run.getInstance().getState())) ServiceBehavior.perform(run, (ProcessNode.Service) run.getGraph().node(nodeOf(job)));
                return null;
            });
        }

        @Override
        public Duration retryDelay(JobEntity job, int attempts) {
            Object seconds = job.getPayload().get(BACKOFF_SECONDS);
            return seconds instanceof Number n ? Duration.ofSeconds(n.longValue()) : JobHandler.super.retryDelay(job, attempts);
        }

        @Override
        public void gaveUp(JobEntity job, String error, Instant now) {
            onInstance(job, now, run -> {
                ProcessNode node = run.getGraph().node(nodeOf(job));
                Map<String, Object> data = Map.of("jobId", job.getId(), "kind", job.getKind(), "error", error);
                run.addEvent(EventEntity.JOB_FAILED, node, null, null, null, null, data);
                // onFailure: error——整个实例停下（别的分支也不能处理），管理员恢复后重试或跳过；默认 manual 只停在这个节点
                if (node instanceof ProcessNode.Service service && "error".equals(service.getOnFailure())) {
                    run.changeStatus(InstanceEntity.SUSPENDED, EventEntity.INSTANCE_SUSPENDED, null);
                }
                return null;
            });
        }
    }

    /** 定时节点到点：实例还停在这个节点的这一轮上时往下走（跳转、退回跳出区域后不再等了的跳过） */
    private JobHandler.Next timer(JobEntity job, Instant now) {
        return onInstance(job, now, run -> {
            if (stillWaiting(job, run.getInstance().getState())) TimerBehavior.fired(run, nodeOf(job));
            return null;
        });
    }

    /** 待办超时：记一条"超时"，再按节点的 overdue 处理 */
    private JobHandler.Next overdue(JobEntity job, Instant now) {
        return onOpenItem(job, now, (run, item, task) -> {
            run.addEvent(EventEntity.WORK_ITEM_OVERDUE, task, null, null, item.getId(), null, Map.of("dueAt", item.getDueAt().toString()));
            overdueOutcome(task).ifPresent(outcome -> completions.finishItem(run, task, item, outcome.getKey(), outcome, null));
            return null;
        });
    }

    /** 超时自动处理按哪个结果：{@code complete} 是写的那个，{@code escalate} 是提级的那个；{@code mark}（默认）不处理 */
    private static Optional<Outcome> overdueOutcome(ProcessNode.Task task) {
        TaskRules.Overdue rule = task.getOverdue();
        if (rule == null) return Optional.empty();
        return switch (rule.getAction()) {
            case "complete" -> Optional.of(CompleteHandler.chosenOutcome(task, rule.getOutcome()));
            case "escalate" -> task.getOutcomes().stream().filter(o -> "escalate".equals(o.kindOrDefault())).findFirst();
            default -> Optional.empty();
        };
    }

    /** 等待节点等超时：还在等这一轮时记一条，overdue 写了 complete 就当作事件按那个结果到了，否则接着等 */
    private JobHandler.Next receiveTimeout(JobEntity job, Instant now) {
        String node = nodeOf(job);
        return onInstance(job, now, run -> {
            if (!stillWaiting(job, run.getInstance().getState())) return null;
            ProcessNode.Receive receive = (ProcessNode.Receive) run.getGraph().node(node);
            run.addEvent(EventEntity.NODE_OVERDUE, receive, null, null, null, null, Map.of("dueAt", job.getDueAt().toString()));
            TaskRules.Overdue rule = receive.getOverdue();
            if (rule != null && "complete".equals(rule.getAction())) {
                context.getDb().deletable(WaitEntity.class).allowDeleteStatement(true)
                    .where(w -> { w.instanceId().eq(job.getInstanceId()); w.event().eq(receive.getEvent()); w.node().eq(node); })
                    .executeRows();
                ReceiveBehavior.received(run, receive, rule.getOutcome(), Map.of());
            }
            return null;
        });
    }

    /** 提醒：记一条催办；超时后每隔一段时间的，次数没用完时排下一次 */
    private JobHandler.Next remind(JobEntity job, Instant now) {
        int index = ((Number) job.getPayload().get(REMINDER)).intValue();
        int sent = ((Number) job.getPayload().get(SENT)).intValue();
        return onOpenItem(job, now, (run, item, task) -> {
            if (index >= task.getReminders().size()) return null;   // 定义改了：按实例的版本不会走到这里，留个保险
            TaskRules.Reminder reminder = task.getReminders().get(index);
            WorkItems.remind(context.getDb(), item.getId(), now, now);
            remindEvent(run, task, item, reminder.getBefore() != null ? "dueSoon" : "overdue");
            boolean more = reminder.getEvery() != null && (reminder.getTimes() == null || sent + 1 < reminder.getTimes());
            return more ? new JobHandler.Next(run.getDeadlines().plus(reminder.getEvery(), now), Map.of(REMINDER, index, SENT, sent + 1)) : null;
        });
    }

    /** 没人认领：还在收件箱里时提醒，或标成等收件箱的负责人指定 */
    private JobHandler.Next unclaimed(JobEntity job, Instant now) {
        return onOpenItem(job, now, (run, item, task) -> {
            if (item.getAssigneeId() != null || task.getUnclaimed() == null) return null;   // 已经有人认领了
            if ("assignByManager".equals(task.getUnclaimed().getAction())) {
                Long manager = item.getInboxKind() == null ? null : context.getOrg().inboxManager(item.getInboxKind(), item.getInboxId()).orElse(null);
                // 标成等收件箱的负责人指定（不知道负责人时 designator 为空）
                context.getDb().updatable(WorkItemEntity.class)
                    .setColumns(w -> { w.needsDesignation().set(true); w.designatorId().set(manager); w.rev().increment(); })
                    .where(w -> { w.id().eq(item.getId()); w.state().eq(WorkItemEntity.OPEN); w.assigneeId().isNull(); })
                    .executeRows();
                Map<String, Object> data = new LinkedHashMap<>();
                if (manager != null) {
                    data.put("designatorId", manager);
                    data.put("designatorName", context.getOrg().userName(manager));
                }
                run.addEvent(EventEntity.WORK_ITEM_UNCLAIMED, task, null, null, item.getId(), null, data);
            } else {
                remindEvent(run, task, item, "unclaimed");
            }
            return null;
        });
    }

    private static void remindEvent(Execution run, ProcessNode.Task task, WorkItemVO item, String reason) {
        run.addEvent(EventEntity.WORK_ITEM_REMINDED, task, null, null, item.getId(), null, Map.of("auto", true, "reason", reason));
    }

    // ───────── 共用的步骤 ─────────

    /** 作业对应的节点（定时、自动节点、等待超时的作业）；待办上的作业（超时、提醒、没人认领）为 null */
    public static String nodeOf(JobEntity job) {
        return (String) job.getPayload().get(NODE);
    }

    /** 实例还停在作业的那个节点的那一轮上（跳转、退回跳出区域后就对不上了，作业跳过） */
    public static boolean stillWaiting(JobEntity job, RunState state) {
        String node = nodeOf(job);
        return node != null && job.getPayload().get(ROUND) instanceof Number round
            && state.getWaits().containsKey(node) && state.round(node) == round.intValue();
    }

    /** 对实例做一件事 */
    @FunctionalInterface
    private interface InstanceAction {
        JobHandler.Next apply(Execution run);
    }

    /** 对实例上一个打开着的待办做一件事 */
    @FunctionalInterface
    private interface ItemAction {
        JobHandler.Next apply(Execution run, WorkItemVO item, ProcessNode.Task task);
    }

    /**
     * 锁住实例再做：实例没了、结束了跳过；暂停时往后推（数据不变）。做完写回。
     * {@link JobRunner} 已经开了事务。
     */
    private JobHandler.Next onInstance(JobEntity job, Instant now, InstanceAction action) {
        InstanceEntity instance = Instances.lockIfExists(context.getDb(), job.getInstanceId());
        if (instance == null || instance.getEndedAt() != null) return null;
        if (InstanceEntity.SUSPENDED.equals(instance.getStatus())) return new JobHandler.Next(now.plus(POSTPONE), job.getPayload());
        FormValues form = Instances.form(context.getDb(), instance.getId());
        Execution run = new Execution(context, SystemActor.INSTANCE, now, definitions.of(instance), instance, form, instance.getRev());
        JobHandler.Next next = action.apply(run);
        run.flush(null);
        return next;
    }

    /** 待办还打开着才做：先不加锁看一眼（办完了的直接跳过，不去锁实例），锁住实例后再重读一次 */
    private JobHandler.Next onOpenItem(JobEntity job, Instant now, ItemAction action) {
        if (!isOpen(job.getWorkItemId())) return null;
        return onInstance(job, now, run -> {
            WorkItemVO item = WorkItems.find(context.getDb(), job.getWorkItemId());
            if (!WorkItemEntity.OPEN.equals(item.getState())) return null;
            return action.apply(run, item, (ProcessNode.Task) run.getGraph().node(item.getNode()));
        });
    }

    private boolean isOpen(Long workItemId) {
        String state = context.getDb().queryable(WorkItemEntity.class).whereById(workItemId).selectColumn(w -> w.state()).singleOrNull();
        return WorkItemEntity.OPEN.equals(state);
    }

    /** 一种作业的处理 */
    private static JobHandler handler(String kind, BiFunction<JobEntity, Instant, JobHandler.Next> run) {
        return new JobHandler() {
            @Override public String kind() { return kind; }

            @Override public Next run(JobEntity job, Instant now) { return run.apply(job, now); }
        };
    }
}
