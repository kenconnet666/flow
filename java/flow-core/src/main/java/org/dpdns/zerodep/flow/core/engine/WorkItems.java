package org.dpdns.zerodep.flow.core.engine;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import org.dpdns.zerodep.flow.core.entity.CandidateEntity;
import org.dpdns.zerodep.flow.core.entity.WorkItemEntity;
import org.dpdns.zerodep.flow.core.view.WorkItemVO;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * 几个处理类都要做的待办读写。状态的变化都写成带条件的更新（{@code WHERE state = 'open'}……），改到 0 行就是别人先改了；
 * 候选只在待办打开时存在（0007 的 1 节第 3 条），待办离开"打开"时一并删掉。只在一处用到的读写直接写在处理类里。
 */
final class WorkItems {
    private WorkItems() { }

    /** 读一个；不存在时报 {@code flow.not-found} */
    static WorkItemVO find(EasyEntityQuery db, Long workItemId) {
        return db.queryable(WorkItemEntity.class).whereById(workItemId).select(WorkItemVO.class)
            .singleNotNull(() -> FlowErrors.notFound("workItem", workItemId));
    }

    /** 实例的全部待办，按到达的先后 */
    static List<WorkItemVO> ofInstance(EasyEntityQuery db, Long instanceId) {
        return db.queryable(WorkItemEntity.class).where(w -> w.instanceId().eq(instanceId))
            .orderBy(w -> { w.createdAt().asc(); w.id().asc(); })
            .select(WorkItemVO.class).toList();
    }

    /** 委派、加签从这个待办生成的待办，按生成的先后 */
    static List<WorkItemVO> children(EasyEntityQuery db, Long parentItemId) {
        return db.queryable(WorkItemEntity.class).where(w -> w.parentItemId().eq(parentItemId)).orderBy(w -> w.id().asc())
            .select(WorkItemVO.class).toList();
    }

    /** 打开着的待办谁能处理、谁被排除 */
    static List<CandidateEntity> candidates(EasyEntityQuery db, Long workItemId) {
        return db.queryable(CandidateEntity.class).where(c -> c.workItemId().eq(workItemId)).orderBy(c -> c.id().asc()).toList();
    }

    /** 新建待办与它的候选（直接落到人的没有候选），返回读出来的样子 */
    static WorkItemVO insert(EasyEntityQuery db, WorkItemEntity item, List<CandidateEntity> candidates) {
        db.insertable(item).executeRows(true);
        if (!candidates.isEmpty()) {
            candidates.forEach(c -> c.setWorkItemId(item.getId()));
            db.insertable(candidates).executeRows();
        }
        return find(db, item.getId());
    }

    /** 处理：只在打开着时改到，并删掉候选；返回是否改到 */
    static boolean complete(EasyEntityQuery db, Long workItemId, String outcome, String comment, Long by, String byName, Instant at) {
        long changed = db.updatable(WorkItemEntity.class)
            .setColumns(w -> {
                w.outcome().set(outcome);
                w.comment().set(comment);
                w.completedBy().set(by);
                w.completedByName().set(byName);
                w.completedAt().set(at);
                w.state().set(WorkItemEntity.DONE);
                w.rev().increment();
            })
            .where(w -> { w.id().eq(workItemId); w.state().eq(WorkItemEntity.OPEN); })
            .executeRows();
        if (changed == 1) dropCandidates(db, List.of(workItemId));
        return changed == 1;
    }

    /** 取消实例上全部还没办完的待办（打开着的、等委派加签的），返回取消之前的样子 */
    static List<WorkItemVO> cancelOpen(EasyEntityQuery db, Long instanceId, Instant at) {
        return cancel(db, db.queryable(WorkItemEntity.class)
            .where(w -> { w.instanceId().eq(instanceId); w.state().in(WorkItemEntity.PENDING); })
            .orderBy(w -> w.id().asc()).select(WorkItemVO.class).toList(), at);
    }

    /** 只取消这些节点上还没办完的待办（退回跳出并行区域时区域里其余的分支、会签结束时其余的人） */
    static List<WorkItemVO> cancelOpenAt(EasyEntityQuery db, Long instanceId, Collection<String> nodes, Instant at) {
        if (nodes.isEmpty()) return List.of();
        return cancel(db, db.queryable(WorkItemEntity.class)
            .where(w -> { w.instanceId().eq(instanceId); w.state().in(WorkItemEntity.PENDING); w.node().in(nodes); })
            .orderBy(w -> w.id().asc()).select(WorkItemVO.class).toList(), at);
    }

    /** 取消一个打开着、或在等委派加签的待办（转办时原来的那一份）；返回是否改到 */
    static boolean cancel(EasyEntityQuery db, Long workItemId, Instant at) {
        return !cancelIds(db, List.of(workItemId), at).isEmpty();
    }

    /** 把这些还没办完的待办改成取消、删掉候选；返回它们取消之前的样子 */
    private static List<WorkItemVO> cancel(EasyEntityQuery db, List<WorkItemVO> pending, Instant at) {
        if (pending.isEmpty()) return pending;
        cancelIds(db, pending.stream().map(WorkItemVO::getId).toList(), at);
        return pending;
    }

    /** 取消这些待办里还没办完的，返回改到了的编号 */
    private static List<Long> cancelIds(EasyEntityQuery db, List<Long> ids, Instant at) {
        List<Long> pending = db.queryable(WorkItemEntity.class)
            .where(w -> { w.id().in(ids); w.state().in(WorkItemEntity.PENDING); })
            .selectColumn(w -> w.id()).toList();
        if (pending.isEmpty()) return pending;
        db.updatable(WorkItemEntity.class)
            .setColumns(w -> {
                w.state().set(WorkItemEntity.CANCELED);
                w.completedAt().set(at);   // 取消也记下时间：列表上看得出什么时候结束的
                w.rev().increment();
            })
            .where(w -> { w.id().in(pending); w.state().in(WorkItemEntity.PENDING); })
            .executeRows();
        dropCandidates(db, pending);
        return pending;
    }

    /** 待办在"打开"与"等别人办完"之间切换（委派、前加签）：只在是 from 时改到；返回是否改到 */
    static boolean changeState(EasyEntityQuery db, Long workItemId, String from, String to) {
        return db.updatable(WorkItemEntity.class)
            .setColumns(w -> { w.state().set(to); w.rev().increment(); })
            .where(w -> { w.id().eq(workItemId); w.state().eq(from); })
            .executeRows() == 1;
    }

    /**
     * 直接落到这个人（管理员改派、收件箱负责人指定）：只在打开着时改到，删掉候选；返回是否改到。
     *
     * @param assignment {@link WorkItemEntity#DIRECT} 或 {@link WorkItemEntity#DESIGNATED}
     * @param designatorId 负责人指定时只改等这个人指定的待办；改派时为 null
     */
    static boolean assignTo(EasyEntityQuery db, Long workItemId, Long userId, String userName, String assignment, Long designatorId) {
        long changed = db.updatable(WorkItemEntity.class)
            .setColumns(w -> {
                w.assigneeId().set(userId);
                w.assigneeName().set(userName);
                w.assignment().set(assignment);
                w.needsDesignation().set(false);
                w.rev().increment();
            })
            .where(w -> {
                w.id().eq(workItemId);
                w.state().eq(WorkItemEntity.OPEN);
                if (designatorId != null) { w.needsDesignation().eq(true); w.designatorId().eq(designatorId); }
            })
            .executeRows();
        if (changed == 1) dropCandidates(db, List.of(workItemId));
        return changed == 1;
    }

    /** 记一次催办：次数加一、记下时间；上一次催办在 notBefore 之后时不记（频率限制）；返回记了没有 */
    static boolean remind(EasyEntityQuery db, Long workItemId, Instant at, Instant notBefore) {
        return db.updatable(WorkItemEntity.class)
            .setColumns(w -> { w.remindCount().increment(); w.lastRemindedAt().set(at); })
            .where(w -> {
                w.id().eq(workItemId);
                w.state().eq(WorkItemEntity.OPEN);
                w.or(() -> { w.lastRemindedAt().isNull(); w.lastRemindedAt().lt(notBefore); });
            })
            .executeRows() == 1;
    }

    /** 某个节点最后一次是谁处理的（{@code sameAs}、退回）；没处理过时为 null */
    static Long lastCompletedBy(EasyEntityQuery db, Long instanceId, String node) {
        return db.queryable(WorkItemEntity.class)
            .where(w -> { w.instanceId().eq(instanceId); w.node().eq(node); w.state().eq(WorkItemEntity.DONE); })
            .orderBy(w -> { w.completedAt().desc(); w.id().desc(); })
            .selectColumn(w -> w.completedBy()).firstOrNull();
    }

    /** 候选只在待办打开时存在 */
    private static void dropCandidates(EasyEntityQuery db, List<Long> workItemIds) {
        db.deletable(CandidateEntity.class).allowDeleteStatement(true).where(c -> c.workItemId().in(workItemIds)).executeRows();
    }
}
