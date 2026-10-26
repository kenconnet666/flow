package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.extension.Extensions;
import org.dpdns.zerodep.flow.core.extension.PeopleResolver;
import org.dpdns.zerodep.flow.core.identity.OrgDirectory;
import org.dpdns.zerodep.flow.core.store.InstanceStore.InstanceRecord;
import org.dpdns.zerodep.flow.core.store.WorkItemStore;
import org.dpdns.zerodep.flow.core.store.WorkItemStore.Candidate;
import org.dpdns.zerodep.flow.model.definition.AssigneeSource;
import org.dpdns.zerodep.flow.model.definition.Lane;
import org.dpdns.zerodep.flow.model.definition.ProcessGraph;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.definition.Ref;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 人工节点由谁处理（0005 的 3.2，0006 第 6 节"组织"一行的 {@code AssigneeResolver}）：把节点上写的几个来源解析成人和收件箱。
 * <pre>
 *   来源                          解析成
 *   { "user": 1001 } / { ref }     人（people 类型的值里有几个人就是几个）
 *   { "role": 11 }                收件箱：有这个角色的人认领
 *   { "department": 20 }          收件箱：这个部门的人认领
 *   { "initiator": true }         人：发起人
 *   { "sameAs": "check" }         人：那个节点最后的处理人
 *   { "resolver": "deptLeader" }  人：接入方注册的解析器算出来的（PeopleResolver）
 *   { "group": "post", "id": 5 }  收件箱：接入方扩展的种类（岗位、项目组……），候选按 Actor.groups 匹配
 *   （节点没写来源）              所在泳道的收件箱
 * </pre>
 * 几个来源取并集。上一步选（{@code pick}）随场景补上，遇到时报"还不支持"。
 */
final class AssigneeResolver {
    private final WorkItemStore workItems;
    private final OrgDirectory org;
    private final Extensions extensions;

    AssigneeResolver(WorkItemStore workItems, OrgDirectory org, Extensions extensions) {
        this.workItems = workItems;
        this.org = org;
        this.extensions = extensions;
    }

    /**
     * 会签的一个参与人（0005 的 5.2）。
     *
     * @param user   用户
     * @param weight 票签的权重：写在指定的人上（{@code { "user": 1001, "weight": 2 }}），其余是 1
     */
    record Participant(Long user, int weight) { }

    /**
     * 会签的参与人：来源列表里的人、角色与部门下的人（{@link OrgDirectory#members} 展开），或引用的 people 值；
     * 同一个人只算一次（先出现的为准），保持先后。
     */
    List<Participant> participants(ProcessNode.Task task, InstanceRecord instance, Function<String, Object> read) {
        if (task.multi().participants() instanceof Ref ref) {
            Map<Long, Participant> out = new LinkedHashMap<>();
            for (Long id : OrgIds.all(read.apply(ref.ref()))) out.putIfAbsent(id, new Participant(id, 1));
            return List.copyOf(out.values());
        }
        return people(task.multi().participantSources(), instance, read);
    }

    /**
     * 几个来源下的人：指定的人、角色与部门下的人（{@link OrgDirectory#members} 展开）、发起人、某节点的处理人；
     * 同一个人只算一次（先出现的为准），保持先后。会签的参与人、抄送节点的收件人都按它展开。
     */
    List<Participant> people(List<AssigneeSource> sources, InstanceRecord instance, Function<String, Object> read) {
        Map<Long, Participant> out = new LinkedHashMap<>();
        for (AssigneeSource source : sources) {
            if (source instanceof AssigneeSource.User user) {
                int weight = user.weight() == null ? 1 : user.weight();
                for (Long id : OrgIds.all(valueOf(user.user(), read))) out.putIfAbsent(id, new Participant(id, weight));
            } else if (source instanceof AssigneeSource.Role role) {
                for (Long id : OrgIds.all(valueOf(role.role(), read))) addMembers(out, Candidate.ROLE, id);
            } else if (source instanceof AssigneeSource.Department dept) {
                for (Long id : OrgIds.all(valueOf(dept.department(), read))) addMembers(out, Candidate.DEPARTMENT, id);
            } else if (source instanceof AssigneeSource.Initiator) {
                out.putIfAbsent(instance.startedBy(), new Participant(instance.startedBy(), 1));
            } else if (source instanceof AssigneeSource.SameAs same) {
                workItems.lastCompletedBy(instance.id(), same.node()).ifPresent(id -> out.putIfAbsent(id, new Participant(id, 1)));
            } else if (source instanceof AssigneeSource.Resolver resolver) {
                for (Long id : named(resolver, instance, read)) out.putIfAbsent(id, new Participant(id, 1));
            } else if (source instanceof AssigneeSource.Group group) {
                for (Long id : OrgIds.all(valueOf(group.id(), read))) addMembers(out, group.kind(), id);
            } else {
                throw new UnsupportedOperationException("assignee source " + source.getClass().getSimpleName() + " cannot be expanded into people yet");
            }
        }
        return List.copyOf(out.values());
    }

    /** 角色、部门下的人都加进来 */
    private void addMembers(Map<Long, Participant> out, String kind, Long id) {
        for (Long member : org.members(kind, id)) out.putIfAbsent(member, new Participant(member, 1));
    }

    /**
     * 解析一个人工节点的处理人。
     *
     * @param read 取引用的值（{@code { "ref": "form.buyer" }} 这类来源要用）
     */
    Assignees resolve(ProcessNode.Task task, ProcessGraph graph, InstanceRecord instance, Function<String, Object> read) {
        Assignees out = Assignees.empty();
        for (AssigneeSource source : sourcesOf(task, graph)) {
            if (source instanceof AssigneeSource.User user) {
                out.users().addAll(OrgIds.all(valueOf(user.user(), read)));
            } else if (source instanceof AssigneeSource.Role role) {
                for (Long id : OrgIds.all(valueOf(role.role(), read))) out.inboxes().add(new Candidate(Candidate.ROLE, id));
            } else if (source instanceof AssigneeSource.Department dept) {
                for (Long id : OrgIds.all(valueOf(dept.department(), read))) out.inboxes().add(new Candidate(Candidate.DEPARTMENT, id));
            } else if (source instanceof AssigneeSource.Initiator) {
                out.users().add(instance.startedBy());
            } else if (source instanceof AssigneeSource.SameAs same) {
                workItems.lastCompletedBy(instance.id(), same.node()).ifPresent(out.users()::add);
            } else if (source instanceof AssigneeSource.Resolver resolver) {
                out.users().addAll(named(resolver, instance, read));
            } else if (source instanceof AssigneeSource.Group group) {
                for (Long id : OrgIds.all(valueOf(group.id(), read))) out.inboxes().add(new Candidate(group.kind(), id));
            } else {
                throw new UnsupportedOperationException("assignee source " + source.getClass().getSimpleName() + " is not supported yet");
            }
        }
        return out;
    }

    /** 接入方注册的解析器算出的人；参数里的引用先换成值 */
    private List<Long> named(AssigneeSource.Resolver source, InstanceRecord instance, Function<String, Object> read) {
        PeopleResolver resolver = extensions.resolver(source.name());
        return resolver.resolve(new PeopleResolver.Context(instance.view(), RefValues.resolve(source.args(), read), read));
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
}
