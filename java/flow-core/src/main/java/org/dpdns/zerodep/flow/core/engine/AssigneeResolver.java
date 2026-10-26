package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.store.InstanceStore.InstanceRecord;
import org.dpdns.zerodep.flow.core.store.WorkItemStore;
import org.dpdns.zerodep.flow.core.store.WorkItemStore.Candidate;
import org.dpdns.zerodep.flow.model.Values;
import org.dpdns.zerodep.flow.model.definition.AssigneeSource;
import org.dpdns.zerodep.flow.model.definition.Lane;
import org.dpdns.zerodep.flow.model.definition.ProcessGraph;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.definition.Ref;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * 人工节点由谁处理（0005 的 3.2，0006 第 6 节"组织"一行的 {@code AssigneeResolver}）：把节点上写的几个来源解析成人和收件箱。
 * <pre>
 *   来源                          解析成
 *   { "user": "u1" } / { ref }    人（people 类型的值里有几个人就是几个）
 *   { "role": "finance" }         收件箱：有这个角色的人认领
 *   { "department": "d20" }       收件箱：这个部门的人认领
 *   { "initiator": true }         人：发起人
 *   { "sameAs": "check" }         人：那个节点最后的处理人
 *   （节点没写来源）              所在泳道的收件箱
 * </pre>
 * 几个来源取并集。解析器（{@code resolver}）、扩展的种类（{@code group}）、上一步选（{@code pick}）随场景补上，遇到时报"还不支持"。
 */
final class AssigneeResolver {
    private final WorkItemStore workItems;

    AssigneeResolver(WorkItemStore workItems) { this.workItems = workItems; }

    /**
     * 解析一个人工节点的处理人。
     *
     * @param read 取引用的值（{@code { "ref": "form.buyer" }} 这类来源要用）
     */
    Assignees resolve(ProcessNode.Task task, ProcessGraph graph, InstanceRecord instance, Function<String, Object> read) {
        Assignees out = Assignees.empty();
        for (AssigneeSource source : sourcesOf(task, graph)) {
            if (source instanceof AssigneeSource.User user) {
                out.users().addAll(idsOf(valueOf(user.user(), read)));
            } else if (source instanceof AssigneeSource.Role role) {
                for (String id : idsOf(valueOf(role.role(), read))) out.inboxes().add(new Candidate(Candidate.ROLE, id));
            } else if (source instanceof AssigneeSource.Department dept) {
                for (String id : idsOf(valueOf(dept.department(), read))) out.inboxes().add(new Candidate(Candidate.DEPARTMENT, id));
            } else if (source instanceof AssigneeSource.Initiator) {
                out.users().add(instance.startedBy());
            } else if (source instanceof AssigneeSource.SameAs same) {
                workItems.lastCompletedBy(instance.id(), same.node()).ifPresent(out.users()::add);
            } else {
                throw new UnsupportedOperationException("assignee source " + source.getClass().getSimpleName() + " is not supported yet");
            }
        }
        return out;
    }

    /** 节点上写的来源；没写时用所在泳道的收件箱 */
    private static List<AssigneeSource> sourcesOf(ProcessNode.Task task, ProcessGraph graph) {
        if (!task.assignees().isEmpty()) return task.assignees();
        Lane lane = graph.lane(task.lane());
        return lane == null || lane.inbox() == null ? List.of() : List.of(lane.inbox().source());
    }

    /** 定义里写的值：直接写的值原样，引用（{@link Ref}）去取 */
    private static Object valueOf(Object literalOrRef, Function<String, Object> read) {
        return literalOrRef instanceof Ref ref ? read.apply(ref.ref()) : literalOrRef;
    }

    /** 一个编号，或一组编号（{@code people} 类型的值是列表）；空的值不算 */
    private static List<String> idsOf(Object value) {
        if (value instanceof Collection<?> items) return items.stream().filter(v -> !Values.isBlank(v)).map(Values::toText).toList();
        return Values.isBlank(value) ? List.of() : List.of(Values.toText(value));
    }
}
