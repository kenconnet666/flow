package org.dpdns.zerodep.flow.core.engine.assignee;

import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.entity.CandidateEntity;
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
 * 审批面板要为哪些下一步显示选人框（0005 的 5.6）：从一个节点按某个结果、用现在的值往下走，穿过网关与抄送节点，
 * 到人工节点为止；处理人来源里有 {@code pick} 的就是要选的。能选的人按 {@code from} 展开（指定的人、角色、部门、扩展的种类）；
 * 没写 from、或写了要看实例才知道的来源（解析器、发起人……）时不限，提交时引擎再核对。
 */
public class PickPreview {
    private final ProcessGraph graph;
    private final EngineContext context;

    public PickPreview(ProcessGraph graph, EngineContext context) {
        this.graph = graph;
        this.context = context;
    }

    /** 从 nodeId 按 outcome 往下走会到的、要选人的节点，按先后 */
    public List<PickView> after(String nodeId, String outcome, Function<String, Object> read) {
        Map<String, PickView> out = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (ProcessEdge edge : Routing.selectEdges(graph, nodeId, outcome, read)) visit(edge.getTo(), read, out, seen);
        return List.copyOf(out.values());
    }

    private void visit(String nodeId, Function<String, Object> read, Map<String, PickView> out, Set<String> seen) {
        if (!seen.add(nodeId)) return;
        ProcessNode node = graph.node(nodeId);
        if (node instanceof ProcessNode.Task task) {
            List<AssigneeSource> sources = task.getMulti() != null ? task.getMulti().participantSources() : task.getAssignees();
            for (AssigneeSource source : sources) {
                if (source instanceof AssigneeSource.Pick pick) out.putIfAbsent(task.getId(), view(task, pick));
            }
        } else if (node instanceof ProcessNode.Gateway || node instanceof ProcessNode.Cc) {
            for (ProcessEdge edge : Routing.selectEdges(graph, nodeId, null, read)) visit(edge.getTo(), read, out, seen);
        }
    }

    private PickView view(ProcessNode.Task task, AssigneeSource.Pick pick) {
        return new PickView(task.getId(), task.getName(), pick.getMax(), candidates(pick.getFrom()));
    }

    /** from 展开成人；没写、或写了要看实例才知道的来源时为 null（不限） */
    private List<PickView.Candidate> candidates(List<AssigneeSource> from) {
        if (from.isEmpty()) return null;
        Set<Long> users = new LinkedHashSet<>();
        for (AssigneeSource source : from) {
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
        }
        List<PickView.Candidate> out = new ArrayList<>();
        for (Long id : users) out.add(new PickView.Candidate(id, context.getOrg().userName(id)));
        return out;
    }
}
