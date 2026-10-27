package org.dpdns.zerodep.flow.core.engine.notify;

import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.store.WorkItems;
import org.dpdns.zerodep.flow.core.entity.CandidateEntity;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.entity.WorkItemEntity;
import org.dpdns.zerodep.flow.core.notify.Notification;
import org.dpdns.zerodep.flow.core.view.instance.FlowEventVO;
import org.dpdns.zerodep.flow.core.view.instance.InstanceVO;
import org.dpdns.zerodep.flow.core.view.workitem.WorkItemVO;
import org.dpdns.zerodep.flow.model.definition.AssigneeSource;
import org.dpdns.zerodep.flow.model.definition.Definitions;
import org.dpdns.zerodep.flow.model.definition.ProcessGraph;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 按这一步写下的流转记录算出要发的通知（0005 第 8 节）：在事务里算（要读待办、候选，要按定义的 {@code to} 解析人），提交后才交给渠道。
 * <pre>
 *   workItem.created        到达（arrived）；退回来的是被退回（returned）
 *   workItem.reminded       催办（reminded）；引擎按节点的 reminders 自动提醒时是即将到期（dueSoon）或已超时（overdue）
 *   workItem.overdue        已超时（overdue），另发收件箱的负责人
 *   instance.cc             抄送（cc）
 *   instance.commented      留言里 @ 到的人（mentioned），不发给留言的人自己
 *   instance.finished、instance.terminated   流程结束（finished），发给发起人
 * </pre>
 * 定义里的 {@code notify}（节点上的盖过流程级的）按种类改：{@code false} 不发；{@code { template, to }} 换模板、加收件人。
 */
public class Notifications {
    private final EngineContext context;
    private final ProcessGraph graph;
    private final InstanceVO instance;
    /** 定义里 {@code to} 写的来源展开成人（同处理人的来源，0005 的 3.2） */
    private final Function<List<AssigneeSource>, List<Long>> people;
    private final List<Notification> out = new ArrayList<>();

    private Notifications(EngineContext context, ProcessGraph graph, InstanceVO instance, Function<List<AssigneeSource>, List<Long>> people) {
        this.context = context;
        this.graph = graph;
        this.instance = instance;
        this.people = people;
    }

    public static List<Notification> plan(List<FlowEventVO> events, EngineContext context, ProcessGraph graph, InstanceVO instance,
                                   Function<List<AssigneeSource>, List<Long>> people) {
        Notifications plan = new Notifications(context, graph, instance, people);
        events.forEach(plan::add);
        return plan.out;
    }

    private void add(FlowEventVO event) {
        switch (event.getType()) {
            case EventEntity.WORK_ITEM_CREATED -> {
                WorkItemVO item = WorkItems.find(context.getDb(), event.getWorkItemId());
                // 后加签先生成、等着的待办打开时才算到达
                if (!WorkItemEntity.OPEN.equals(item.getState())) return;
                send("return".equals(item.getArrival()) ? Notification.RETURNED : Notification.ARRIVED, event, item, handlers(item));
            }
            case EventEntity.WORK_ITEM_REMINDED -> {
                WorkItemVO item = WorkItems.find(context.getDb(), event.getWorkItemId());
                Object reason = event.getData().get("reason");
                String kind = "dueSoon".equals(reason) ? Notification.DUE_SOON : "overdue".equals(reason) ? Notification.OVERDUE : Notification.REMINDED;
                send(kind, event, item, handlers(item));
            }
            case EventEntity.WORK_ITEM_OVERDUE -> {
                WorkItemVO item = WorkItems.find(context.getDb(), event.getWorkItemId());
                Set<Long> to = new LinkedHashSet<>(handlers(item));
                if (item.getInboxKind() != null) context.getOrg().inboxManager(item.getInboxKind(), item.getInboxId()).ifPresent(to::add);
                send(Notification.OVERDUE, event, item, to);
            }
            case EventEntity.INSTANCE_CC -> send(Notification.CC, event, null, ids(event.getData().get("users")));
            case EventEntity.INSTANCE_COMMENTED -> {
                Set<Long> to = ids(event.getData().get("mentions"));
                to.remove(event.getActorId());
                send(Notification.MENTIONED, event, null, to);
            }
            case EventEntity.INSTANCE_FINISHED, EventEntity.INSTANCE_TERMINATED ->
                send(Notification.FINISHED, event, null, Set.of(instance.getStartedBy()));
            default -> { }
        }
    }

    /** 按定义的 notify 定下模板、加上收件人；关掉了、没人可发时不发 */
    private void send(String kind, FlowEventVO event, WorkItemVO item, Set<Long> recipients) {
        Object setting = settingOf(kind, event.getNode());
        if (Boolean.FALSE.equals(setting)) return;
        Set<Long> to = new LinkedHashSet<>(recipients);
        String template = null;
        if (setting instanceof Map<?, ?> custom) {
            template = custom.get("template") instanceof String name ? name : null;
            if (custom.get("to") != null) to.addAll(people.apply(Definitions.parseSources(custom.get("to"))));
        }
        to.remove(null);
        if (!to.isEmpty()) out.add(new Notification(kind, template, List.copyOf(to), event, instance, item));
    }

    /** 这种通知的设置：节点上写了的用节点的，否则用流程级的；都没写时为 null（照默认发） */
    private Object settingOf(String kind, String nodeId) {
        ProcessNode node = nodeId == null ? null : graph.node(nodeId);
        if (node != null && node.getInfo().getNotifications().containsKey(kind)) return node.getInfo().getNotifications().get(kind);
        return graph.definition().getSettings().getNotifications().get(kind);
    }

    /** 待办的处理人：落到人的是他；在收件箱里的是候选人（角色、部门经 {@code OrgDirectory.members} 展开，接入方没实现时略过），去掉被排除的 */
    private Set<Long> handlers(WorkItemVO item) {
        Set<Long> out = new LinkedHashSet<>();
        if (item.getAssigneeId() != null) {
            out.add(item.getAssigneeId());
            return out;
        }
        Set<Long> excluded = new LinkedHashSet<>();
        for (CandidateEntity candidate : WorkItems.candidates(context.getDb(), item.getId())) {
            switch (candidate.getKind()) {
                case CandidateEntity.USER -> out.add(candidate.getTargetId());
                case CandidateEntity.EXCLUDED -> excluded.add(candidate.getTargetId());
                default -> {
                    try {
                        out.addAll(context.getOrg().members(candidate.getKind(), candidate.getTargetId()));
                    } catch (UnsupportedOperationException ignored) {
                        // 没实现 members 时收件箱只通知写明的人；角色、部门的人自己到收件箱里看
                    }
                }
            }
        }
        out.removeAll(excluded);
        return out;
    }

    private static Set<Long> ids(Object value) {
        Set<Long> out = new LinkedHashSet<>();
        if (value instanceof List<?> list) for (Object id : list) if (id instanceof Number n) out.add(n.longValue());
        return out;
    }
}
