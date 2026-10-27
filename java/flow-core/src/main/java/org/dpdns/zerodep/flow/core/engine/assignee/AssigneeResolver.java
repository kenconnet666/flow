package org.dpdns.zerodep.flow.core.engine.assignee;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import lombok.Data;
import org.dpdns.zerodep.flow.core.engine.FlowErrors;
import org.dpdns.zerodep.flow.core.engine.form.RefValues;
import org.dpdns.zerodep.flow.core.engine.store.Instances;
import org.dpdns.zerodep.flow.core.engine.store.WorkItems;
import org.dpdns.zerodep.flow.core.entity.CandidateEntity;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.extension.Extensions;
import org.dpdns.zerodep.flow.core.extension.PeopleResolver;
import org.dpdns.zerodep.flow.core.identity.OrgDirectory;
import org.dpdns.zerodep.flow.model.definition.AssigneeSource;
import org.dpdns.zerodep.flow.model.definition.Lane;
import org.dpdns.zerodep.flow.model.definition.ProcessGraph;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.definition.Ref;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
 *   { "pick": { "from": […] } }   人：上一步的处理人提交时选的（命令的 picks），要在 from 解析出的人里、不超过 max
 *   （节点没写来源）              所在泳道的收件箱
 * </pre>
 * 几个来源取并集。
 */
public class AssigneeResolver {
    private final EasyEntityQuery db;
    private final OrgDirectory org;
    private final Extensions extensions;

    public AssigneeResolver(EasyEntityQuery db, OrgDirectory org, Extensions extensions) {
        this.db = db;
        this.org = org;
        this.extensions = extensions;
    }

    /** 会签的一个参与人（0005 的 5.2）。 */
    @Data
    public static class Participant {
        /** 用户 */
        private final Long user;
        /** 票签的权重：写在指定的人上（{@code { "user": 1001, "weight": 2 }}），其余是 1 */
        private final int weight;
    }

    /**
     * 会签的参与人：来源列表里的人、角色与部门下的人（{@link OrgDirectory#members} 展开），或引用的 people 值；
     * 同一个人只算一次（先出现的为准），保持先后。
     */
    public List<Participant> participants(ProcessNode.Task task, InstanceEntity instance, Function<String, Object> read,
                                   Map<String, List<Long>> picks) {
        Map<Long, Participant> out = new LinkedHashMap<>();
        if (task.getMulti().getParticipants() instanceof Ref ref) {
            for (Long id : OrgIds.all(read.apply(ref.getRef()))) out.putIfAbsent(id, new Participant(id, 1));
            return List.copyOf(out.values());
        }
        List<AssigneeSource> others = new ArrayList<>();
        for (AssigneeSource source : task.getMulti().participantSources()) {
            if (source instanceof AssigneeSource.Pick pick) {
                for (Long id : picked(task, pick, picks, instance, read)) out.putIfAbsent(id, new Participant(id, 1));
            } else {
                others.add(source);
            }
        }
        for (Participant p : people(others, instance, read)) out.putIfAbsent(p.getUser(), p);
        return List.copyOf(out.values());
    }

    /** 几个来源下的人的编号（抄送、通知的收件人）：同 {@link #people}，只要编号 */
    public List<Long> peopleOf(List<AssigneeSource> sources, InstanceEntity instance, Function<String, Object> read) {
        return people(sources, instance, read).stream().map(Participant::getUser).toList();
    }

    /**
     * 几个来源下的人：指定的人、角色与部门下的人（{@link OrgDirectory#members} 展开）、发起人、某节点的处理人；
     * 同一个人只算一次（先出现的为准），保持先后。会签的参与人、抄送节点的收件人都按它展开。
     */
    public List<Participant> people(List<AssigneeSource> sources, InstanceEntity instance, Function<String, Object> read) {
        Map<Long, Participant> out = new LinkedHashMap<>();
        for (AssigneeSource source : sources) {
            if (source instanceof AssigneeSource.User user) {
                int weight = user.getWeight() == null ? 1 : user.getWeight();
                for (Long id : OrgIds.all(valueOf(user.getUser(), read))) out.putIfAbsent(id, new Participant(id, weight));
            } else if (source instanceof AssigneeSource.Role role) {
                for (Long id : OrgIds.all(valueOf(role.getRole(), read))) addMembers(out, CandidateEntity.ROLE, id);
            } else if (source instanceof AssigneeSource.Department dept) {
                for (Long id : OrgIds.all(valueOf(dept.getDepartment(), read))) addMembers(out, CandidateEntity.DEPARTMENT, id);
            } else if (source instanceof AssigneeSource.Initiator) {
                out.putIfAbsent(instance.getStartedBy(), new Participant(instance.getStartedBy(), 1));
            } else if (source instanceof AssigneeSource.SameAs same) {
                Long last = WorkItems.lastCompletedBy(db, instance.getId(), same.getNode());
                if (last != null) out.putIfAbsent(last, new Participant(last, 1));
            } else if (source instanceof AssigneeSource.Resolver resolver) {
                for (Long id : named(resolver, instance, read)) out.putIfAbsent(id, new Participant(id, 1));
            } else if (source instanceof AssigneeSource.Group group) {
                for (Long id : OrgIds.all(valueOf(group.getId(), read))) addMembers(out, group.getKind(), id);
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
    public Assignees resolve(ProcessNode.Task task, ProcessGraph graph, InstanceEntity instance, Function<String, Object> read,
                      Map<String, List<Long>> picks) {
        Assignees out = Assignees.empty();
        for (AssigneeSource source : sourcesOf(task, graph)) {
            if (source instanceof AssigneeSource.Pick pick) {
                out.getUsers().addAll(picked(task, pick, picks, instance, read));
            } else if (source instanceof AssigneeSource.User user) {
                out.getUsers().addAll(OrgIds.all(valueOf(user.getUser(), read)));
            } else if (source instanceof AssigneeSource.Role role) {
                for (Long id : OrgIds.all(valueOf(role.getRole(), read))) out.getInboxes().add(new CandidateEntity(CandidateEntity.ROLE, id));
            } else if (source instanceof AssigneeSource.Department dept) {
                for (Long id : OrgIds.all(valueOf(dept.getDepartment(), read))) out.getInboxes().add(new CandidateEntity(CandidateEntity.DEPARTMENT, id));
            } else if (source instanceof AssigneeSource.Initiator) {
                out.getUsers().add(instance.getStartedBy());
            } else if (source instanceof AssigneeSource.SameAs same) {
                Long last = WorkItems.lastCompletedBy(db, instance.getId(), same.getNode());
                if (last != null) out.getUsers().add(last);
            } else if (source instanceof AssigneeSource.Resolver resolver) {
                out.getUsers().addAll(named(resolver, instance, read));
            } else if (source instanceof AssigneeSource.Group group) {
                for (Long id : OrgIds.all(valueOf(group.getId(), read))) out.getInboxes().add(new CandidateEntity(group.getKind(), id));
            } else {
                throw new UnsupportedOperationException("assignee source " + source.getClass().getSimpleName() + " is not supported yet");
            }
        }
        return out;
    }

    /**
     * 上一步选的人：没选报 {@code picks.节点 required}，超过 max 报 {@code too-many}，不在 from 解析出的人里报 {@code not-allowed}
     * （from 不写时谁都可以）。
     */
    private List<Long> picked(ProcessNode.Task task, AssigneeSource.Pick pick, Map<String, List<Long>> picks, InstanceEntity instance,
                              Function<String, Object> read) {
        String field = "picks." + task.getId();
        List<Long> chosen = picks.getOrDefault(task.getId(), List.of()).stream().distinct().toList();
        if (chosen.isEmpty()) throw FlowErrors.invalid(field, "required");
        if (pick.getMax() != null && chosen.size() > pick.getMax()) throw FlowErrors.invalid(field, "too-many");
        if (!pick.getFrom().isEmpty()) {
            Set<Long> allowed = people(pick.getFrom(), instance, read).stream().map(Participant::getUser).collect(Collectors.toSet());
            if (!allowed.containsAll(chosen)) throw FlowErrors.invalid(field, "not-allowed");
        }
        return chosen;
    }

    /** 接入方注册的解析器算出的人；参数里的引用先换成值 */
    private List<Long> named(AssigneeSource.Resolver source, InstanceEntity instance, Function<String, Object> read) {
        PeopleResolver resolver = extensions.resolver(source.getName());
        return resolver.resolve(new PeopleResolver.Context(Instances.view(db, instance.getId()), RefValues.resolve(source.getArgs(), read), read));
    }

    /** 节点上写的来源；没写时用所在泳道的收件箱 */
    private static List<AssigneeSource> sourcesOf(ProcessNode.Task task, ProcessGraph graph) {
        if (!task.getAssignees().isEmpty()) return task.getAssignees();
        Lane lane = graph.lane(task.getLane());
        return lane == null || lane.getInbox() == null ? List.of() : List.of(lane.getInbox().getSource());
    }

    /** 定义里写的值：直接写的值原样，引用（{@link Ref}）去取 */
    private static Object valueOf(Object literalOrRef, Function<String, Object> read) {
        return literalOrRef instanceof Ref ref ? read.apply(ref.getRef()) : literalOrRef;
    }
}
