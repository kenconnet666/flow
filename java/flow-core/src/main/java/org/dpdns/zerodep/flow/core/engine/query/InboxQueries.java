package org.dpdns.zerodep.flow.core.engine.query;

import com.easy.query.api.proxy.entity.select.EntityQueryable;
import com.easy.query.core.func.def.enums.OrderByModeEnum;
import org.dpdns.zerodep.flow.core.engine.DefinitionCache;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.entity.CandidateEntity;
import org.dpdns.zerodep.flow.core.entity.CcEntity;
import org.dpdns.zerodep.flow.core.entity.WorkItemEntity;
import org.dpdns.zerodep.flow.core.entity.proxy.WorkItemEntityProxy;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.view.Page;
import org.dpdns.zerodep.flow.core.view.instance.CcItem;
import org.dpdns.zerodep.flow.core.view.workitem.TodoItem;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 收件箱一侧的列表（0006 第 4 节）：我的待办、我已办、等我指定的、抄送我的。行都是带上实例的对象树（{@code selectAutoInclude}）。 */
public class InboxQueries {
    private final EngineContext context;
    private final DefinitionCache definitions;

    public InboxQueries(EngineContext context) {
        this.context = context;
        this.definitions = context.getDefinitions();
    }

    /**
     * 我的待办：落到我的，加上我、我的角色、部门或扩展种类（{@link Actor#groups()}）能认领的（我没被排除），按到达时间从新到旧；
     * 每行带上实例（标题、摘要列与状态，0006 第 4 节"待办的业务摘要列"）。
     * <p>分两段查（0007 第 4 节），各取前"偏移 + 页长"条再合并：落到我的走（租户, 处理人, 状态, 到达时间）的索引；
     * 我能认领的（没有处理人、候选里有我所在的、我没被排除）从候选的覆盖索引进。第二段要求没有处理人，两段不会重叠。
     */
    public Page<TodoItem> todo(Actor actor, int page, int size) {
        if (page < 1 || size < 1) throw new IllegalArgumentException("page and size start at 1");
        Map<String, Set<Long>> memberships = new LinkedHashMap<>(actor.getGroups());
        memberships.put(CandidateEntity.USER, Set.of(actor.getId()));
        memberships.put(CandidateEntity.ROLE, actor.getRoles());
        memberships.put(CandidateEntity.DEPARTMENT, actor.getDepartments());
        Long me = actor.getId();
        long mine = assignedTo(me).count();
        long claimable = claimableBy(me, memberships).count();
        int head = page * size;
        List<TodoItem> rows = new ArrayList<>();
        rows.addAll(assignedTo(me).orderBy(w -> { w.createdAt().desc(); w.id().desc(); }).limit(head).selectAutoInclude(TodoItem.class).toList());
        rows.addAll(claimableBy(me, memberships).orderBy(w -> { w.createdAt().desc(); w.id().desc(); }).limit(head).selectAutoInclude(TodoItem.class).toList());
        rows.sort(Comparator.comparing(TodoItem::getCreatedAt).thenComparing(TodoItem::getId).reversed());
        return Page.of(rows.stream().skip((long) (page - 1) * size).limit(size).toList(), mine + claimable, page, size);
    }

    /** 落到我的、打开着的 */
    private EntityQueryable<WorkItemEntityProxy, WorkItemEntity> assignedTo(Long me) {
        return context.getDb().queryable(WorkItemEntity.class).where(w -> { w.assigneeId().eq(me); w.state().eq(WorkItemEntity.OPEN); });
    }

    /** 收件箱里我能认领的：候选里有我、我的角色、部门或扩展种类，而且我没被排除 */
    private EntityQueryable<WorkItemEntityProxy, WorkItemEntity> claimableBy(Long me, Map<String, Set<Long>> memberships) {
        return context.getDb().queryable(WorkItemEntity.class).where(w -> {
            w.assigneeId().isNull();
            w.state().eq(WorkItemEntity.OPEN);
            w.candidates().any(c -> c.or(() -> memberships.forEach((kind, ids) -> {
                if (!ids.isEmpty()) c.and(() -> { c.kind().eq(kind); c.targetId().in(ids); });
            })));
            w.candidates().none(c -> { c.kind().eq(CandidateEntity.EXCLUDED); c.targetId().eq(me); });
        });
    }

    /** 我处理过的：按处理时间从新到旧，每行带实例与处理结果的名字 */
    public Page<TodoItem> done(Actor actor, ListFilter filter, int page, int size) {
        Page<TodoItem> rows = Page.of(context.getDb().queryable(WorkItemEntity.class)
            .where(w -> {
                w.completedBy().eq(actor.getId());
                w.state().eq(WorkItemEntity.DONE);
                if (filter.hasProcess()) w.processKey().eq(filter.getProcessKey());
                if (filter.getFrom() != null) w.completedAt().ge(filter.getFrom());
                if (filter.getTo() != null) w.completedAt().lt(filter.getTo());
            })
            .orderBy(w -> { w.completedAt().desc(); w.id().desc(); })
            .selectAutoInclude(TodoItem.class).toPageResult(page, size), page, size);
        rows.getRows().forEach(this::labelOutcome);
        return rows;
    }

    /** 等我指定承办人的待办（无人认领超时、我是收件箱负责人），每行带实例 */
    public Page<TodoItem> designations(Actor actor, int page, int size) {
        return Page.of(context.getDb().queryable(WorkItemEntity.class)
            .where(w -> { w.designatorId().eq(actor.getId()); w.needsDesignation().eq(true); w.state().eq(WorkItemEntity.OPEN); })
            .orderBy(w -> { w.createdAt().desc(); w.id().desc(); })
            .selectAutoInclude(TodoItem.class).toPageResult(page, size), page, size);
    }

    /** 处理过的行带上结果的名字：按实例的定义版本找节点上的 outcome；隐式的结果（加签办完）没有名字 */
    private void labelOutcome(TodoItem item) {
        if (item.getOutcome() == null || item.getInstance() == null) return;
        if (!(definitions.byId(item.getInstance().getDefinitionId()).getGraph().node(item.getNode()) instanceof ProcessNode.Task task)) return;
        task.getOutcomes().stream().filter(o -> o.getKey().equals(item.getOutcome())).findFirst().ifPresent(o -> item.setOutcomeLabel(o.getLabel()));
    }

    /** 抄送我的：未读在前，已读的按读的时间从新到旧，同样的按抄送时间从新到旧；每行带实例 */
    public Page<CcItem> ccList(Actor actor, ListFilter filter, int page, int size) {
        return Page.of(context.getDb().queryable(CcEntity.class)
            .where(c -> {
                c.recipientId().eq(actor.getId());
                // 抄送上没有流程标识：沿导航 instance 隐式连表
                if (filter.hasProcess()) c.instance().processKey().eq(filter.getProcessKey());
                if (filter.getFrom() != null) c.createdAt().ge(filter.getFrom());
                if (filter.getTo() != null) c.createdAt().lt(filter.getTo());
            })
            .orderBy(c -> { c.readAt().desc(true, OrderByModeEnum.NULLS_FIRST); c.createdAt().desc(); c.id().desc(); })
            .selectAutoInclude(CcItem.class).toPageResult(page, size), page, size);
    }
}
