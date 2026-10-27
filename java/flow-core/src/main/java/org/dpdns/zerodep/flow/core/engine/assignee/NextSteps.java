package org.dpdns.zerodep.flow.core.engine.assignee;

import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.store.WorkItems;
import org.dpdns.zerodep.flow.core.entity.CandidateEntity;
import org.dpdns.zerodep.flow.core.view.workitem.NextStep;
import org.dpdns.zerodep.flow.core.view.workitem.PickView;
import org.dpdns.zerodep.flow.model.definition.AssigneeSource;
import org.dpdns.zerodep.flow.model.definition.ProcessEdge;
import org.dpdns.zerodep.flow.model.definition.ProcessGraph;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.definition.Ref;
import org.dpdns.zerodep.flow.model.definition.Routing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 预测下一步（0006 的 predict、0005 的 5.6）：从一个节点按某个结果、用现在的值往下走，穿过网关与抄送节点，
 * 停在第一批要等的节点（人工、自动、调用流程、等事件、定时、结束）。审批面板拿它显示"提交后到哪里、交给谁"，
 * 以及哪些下一步要选人（{@link #picks}）。
 * <p>
 * 能选的人按 {@code pick.from} 展开（指定的人、角色、部门、扩展的种类）；没写 from、或写了要看实例才知道的来源时不限，提交时引擎再核对。
 */
public class NextSteps {
    private final ProcessGraph graph;
    private final EngineContext context;
    /** 实例的发起人（发起页上是操作人自己） */
    private final Long starterId;
    /** 实例；发起页上还没有，为 null（"同某节点的处理人"就看不出来） */
    private final Long instanceId;

    public NextSteps(ProcessGraph graph, EngineContext context, Long starterId, Long instanceId) {
        this.graph = graph;
        this.context = context;
        this.starterId = starterId;
        this.instanceId = instanceId;
    }

    /** 从 nodeId 按 outcome 往下走会停下的节点，按先后 */
    public List<NextStep> after(String nodeId, String outcome, Function<String, Object> read) {
        Map<String, NextStep> out = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (ProcessEdge edge : Routing.selectEdges(graph, nodeId, outcome, read)) visit(edge.getTo(), read, out, seen);
        return List.copyOf(out.values());
    }

    /** 下一步里要选处理人的那些 */
    public static List<PickView> picks(List<NextStep> steps) {
        return steps.stream().map(NextStep::getPick).filter(p -> p != null).toList();
    }

    private void visit(String nodeId, Function<String, Object> read, Map<String, NextStep> out, Set<String> seen) {
        if (!seen.add(nodeId)) return;
        ProcessNode node = graph.node(nodeId);
        if (node instanceof ProcessNode.Gateway || node instanceof ProcessNode.Cc) {
            for (ProcessEdge edge : Routing.selectEdges(graph, nodeId, null, read)) visit(edge.getTo(), read, out, seen);
            return;
        }
        if (node instanceof ProcessNode.Task task && skipsStarter(task)) {
            // 发起时自动跳过的（处理人就是发起人）：按同意往下看，不停在这里
            for (ProcessEdge edge : Routing.selectEdges(graph, nodeId, task.approveOutcome(), read)) visit(edge.getTo(), read, out, seen);
            return;
        }
        out.put(nodeId, node instanceof ProcessNode.Task task ? taskStep(task)
            : new NextStep(node.getId(), node.getName(), node.getType(), List.of(), false, null));
    }

    /** 处理人只是发起人、又开了"处理人就是发起人时自动跳过"（同 TaskBehavior.skipReason 的 initiator） */
    private boolean skipsStarter(ProcessNode.Task task) {
        Map<String, Object> rules = task.getInfo().getAutoSkip().isEmpty() ? graph.definition().getSettings().getAutoSkip() : task.getInfo().getAutoSkip();
        if (!Boolean.TRUE.equals(rules.get("initiator")) || task.getMulti() != null || task.approveOutcome() == null) return false;
        List<AssigneeSource> sources = task.getAssignees();
        return !sources.isEmpty() && sources.stream().allMatch(s -> s instanceof AssigneeSource.Initiator
            || starterId != null && s instanceof AssigneeSource.User user && !(user.getUser() instanceof Ref) && OrgIds.all(user.getUser()).equals(List.of(starterId)));
    }

    private NextStep taskStep(ProcessNode.Task task) {
        List<AssigneeSource> sources = task.getMulti() != null ? task.getMulti().participantSources() : task.getAssignees();
        Set<String> handlers = new LinkedHashSet<>();
        boolean inbox = false;
        PickView pick = null;
        for (AssigneeSource source : sources) {
            if (source instanceof AssigneeSource.Pick p) pick = new PickView(task.getId(), task.getName(), p.getMax(), candidates(p.getFrom()));
            else if (source.isInbox()) {
                // 会签时角色、部门下的人每人一份，不进收件箱：写出是哪些人
                List<Long> members = task.getMulti() != null ? members(source) : null;
                if (members == null) inbox = true;
                else members.forEach(id -> handlers.add(name(id)));
            }
            else if (source instanceof AssigneeSource.User user && !(user.getUser() instanceof Ref)) OrgIds.all(user.getUser()).forEach(id -> handlers.add(name(id)));
            else if (source instanceof AssigneeSource.Initiator && starterId != null) handlers.add(name(starterId));
            else if (source instanceof AssigneeSource.SameAs same && instanceId != null) {
                Long last = WorkItems.lastCompletedBy(context.getDb(), instanceId, same.getNode());
                if (last != null) handlers.add(name(last));
            }
        }
        return new NextStep(task.getId(), task.getName(), task.getType(), List.copyOf(handlers), inbox, pick);
    }

    private String name(Long userId) { return context.getOrg().userName(userId); }

    /** from 展开成人；没写、或写了要看实例才知道的来源时为 null（不限） */
    private List<PickView.Candidate> candidates(List<AssigneeSource> from) {
        if (from.isEmpty()) return null;
        Set<Long> users = new LinkedHashSet<>();
        for (AssigneeSource source : from) {
            List<Long> members = members(source);
            if (members == null) return null;
            users.addAll(members);
        }
        List<PickView.Candidate> out = new ArrayList<>();
        for (Long id : users) out.add(new PickView.Candidate(id, name(id)));
        return out;
    }

    /** 一个来源下的人：指定的人、角色与部门与扩展组的成员；要看实例才知道的（引用、发起人……）为 null */
    private List<Long> members(AssigneeSource source) {
        List<Long> users = new ArrayList<>();
        if (source instanceof AssigneeSource.User user && !(user.getUser() instanceof Ref)) {
            users.addAll(OrgIds.all(user.getUser()));
        } else if (source instanceof AssigneeSource.Role role && !(role.getRole() instanceof Ref)) {
            for (Long id : OrgIds.all(role.getRole())) users.addAll(context.getOrg().members(CandidateEntity.ROLE, id));
        } else if (source instanceof AssigneeSource.Department dept && !(dept.getDepartment() instanceof Ref)) {
            for (Long id : OrgIds.all(dept.getDepartment())) users.addAll(context.getOrg().members(CandidateEntity.DEPARTMENT, id));
        } else if (source instanceof AssigneeSource.Group group && !(group.getId() instanceof Ref)) {
            for (Long id : OrgIds.all(group.getId())) users.addAll(context.getOrg().members(group.getKind(), id));
        } else {
            return null;
        }
        return users;
    }
}
