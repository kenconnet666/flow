package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.store.WorkItemStore;
import org.dpdns.zerodep.flow.model.runtime.WorkItem;
import org.dpdns.zerodep.query.exec.Database;
import org.dpdns.zerodep.query.exec.Page;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.dpdns.zerodep.query.Sql.all;
import static org.dpdns.zerodep.query.Sql.any;
import static org.dpdns.zerodep.query.Sql.deleteFrom;
import static org.dpdns.zerodep.query.Sql.from;
import static org.dpdns.zerodep.query.Sql.insertInto;
import static org.dpdns.zerodep.query.Sql.orders;
import static org.dpdns.zerodep.query.Sql.update;
import static org.dpdns.zerodep.query.Sql.val;

/**
 * 待办与候选的默认存储：{@code flow_work_item} 与 {@code flow_candidate} 表。待办的记录（{@link WorkItem}）就是表的一行。
 * 状态的变化都写成带条件的更新（{@code WHERE state = 'open'}……），改到 0 行就是别人先改了；收件箱的查法见 {@link #todo}。
 */
final class QueryWorkItemStore implements WorkItemStore {
    /** 新到的待办的顺序：到达时间从新到旧，同一时刻按编号从大到小 */
    private static final Comparator<WorkItem> NEWEST_FIRST = Comparator.comparing(WorkItem::createdAt).thenComparing(WorkItem::id).reversed();

    private final Database db;

    QueryWorkItemStore(Database db) { this.db = db; }

    /** 候选插入的一行（编号由数据库自增） */
    record NewCandidate(Long workItemId, String kind, Long targetId) { }

    @Override
    public WorkItem insert(WorkItem draft, List<Candidate> candidates) {
        WorkItem saved = db.<WorkItem>insertReturning(insertInto(FlowTables.WorkItem.class).values(draft)).get(0);
        if (!candidates.isEmpty()) {
            db.execute(insertInto(FlowTables.Candidate.class).values(candidates.stream().map(c -> new NewCandidate(saved.id(), c.kind(), c.targetId())).toList()));
        }
        return saved;
    }

    @Override
    public Optional<WorkItem> find(Long workItemId) {
        return db.optional(from(FlowTables.WorkItem.class).where(r -> r.id().eq(workItemId)).selectAs(WorkItem.class));
    }

    @Override
    public List<Candidate> candidates(Long workItemId) {
        return db.list(from(FlowTables.Candidate.class).where(c -> c.workItemId().eq(workItemId)).orderBy(c -> c.id())
            .selectAs(Candidate.class));
    }

    @Override
    public boolean claim(Long workItemId, Claim claim) {
        return db.execute(update(FlowTables.WorkItem.class)
            .set(claim)
            .set(r -> { r.assignment().to(WorkItem.CLAIMED); r.rev().to(r.rev().plus(val(1))); })
            .where(r -> { r.id().eq(workItemId); r.state().eq(WorkItem.OPEN); r.assigneeId().isNull(); })) == 1;
    }

    @Override
    public boolean complete(Long workItemId, Completion completion) {
        boolean changed = db.execute(update(FlowTables.WorkItem.class)
            .set(completion)
            .set(r -> { r.state().to(WorkItem.DONE); r.rev().to(r.rev().plus(val(1))); })
            .where(r -> { r.id().eq(workItemId); r.state().eq(WorkItem.OPEN); })) == 1;
        // 候选只在待办打开时存在（0007 的 1 节第 3 条）
        if (changed) db.execute(deleteFrom(FlowTables.Candidate.class).where(r -> r.workItemId().eq(workItemId)));
        return changed;
    }

    @Override
    public List<WorkItem> cancelOpen(Long instanceId, Instant at) {
        List<WorkItem> open = db.list(from(FlowTables.WorkItem.class).where(r -> { r.instanceId().eq(instanceId); r.state().eq(WorkItem.OPEN); })
            .orderBy(r -> r.id()).selectAs(WorkItem.class));
        if (open.isEmpty()) return open;
        List<Long> ids = open.stream().map(WorkItem::id).toList();
        db.execute(update(FlowTables.WorkItem.class).set(r -> {
            r.state().to(WorkItem.CANCELED);
            r.completedAt().to(at);   // 取消也记下时间：列表上看得出什么时候结束的
            r.rev().to(r.rev().plus(val(1)));
        }).where(r -> { r.id().in(ids); r.state().eq(WorkItem.OPEN); }));
        db.execute(deleteFrom(FlowTables.Candidate.class).where(r -> r.workItemId().in(ids)));
        return open;
    }

    @Override
    public List<WorkItem> ofInstance(Long instanceId) {
        return db.list(from(FlowTables.WorkItem.class).where(r -> r.instanceId().eq(instanceId)).orderBy(r -> orders(r.createdAt(), r.id()))
            .selectAs(WorkItem.class));
    }

    @Override
    public Optional<Long> lastCompletedBy(Long instanceId, String node) {
        return db.optional(from(FlowTables.WorkItem.class)
            .where(r -> { r.instanceId().eq(instanceId); r.node().eq(node); r.state().eq(WorkItem.DONE); })
            .orderBy(r -> orders(r.completedAt().desc(), r.id().desc())).limit(1).select(r -> r.completedBy()));
    }

    /**
     * 收件箱分两段查（0007 第 4 节），各取前"偏移 + 页长"条再合并：
     * <ol>
     *   <li>落到我的：走（租户, 处理人, 状态, 到达时间）的索引；</li>
     *   <li>我能认领的：没有处理人，候选里有我、我的角色或部门，而且我没被排除——从候选的覆盖索引进。</li>
     * </ol>
     * 第二段要求没有处理人，两段不会重叠，总数是两段之和。
     */
    @Override
    public Page<WorkItem> todo(Member member, int page, int size) {
        if (page < 1 || size < 1) throw new IllegalArgumentException("page and size start at 1");
        int head = page * size;
        var mine = from(FlowTables.WorkItem.class).where(r -> { r.assigneeId().eq(member.userId()); r.state().eq(WorkItem.OPEN); });
        var claimable = from(FlowTables.WorkItem.class).where(r -> {
            r.assigneeId().isNull();
            r.state().eq(WorkItem.OPEN);
            r.candidates().any(c -> any(() -> {
                for (Map.Entry<String, Set<Long>> e : member.memberships().entrySet()) {
                    if (!e.getValue().isEmpty()) all(() -> { c.kind().eq(e.getKey()); c.targetId().in(e.getValue()); });
                }
            }));
            r.candidates().none(c -> { c.kind().eq(Candidate.EXCLUDED); c.targetId().eq(member.userId()); });
        });
        long total = db.count(mine.select(r -> r.id())) + db.count(claimable.select(r -> r.id()));
        List<WorkItem> rows = new ArrayList<>();
        rows.addAll(db.list(mine.orderBy(r -> orders(r.createdAt().desc(), r.id().desc())).limit(head).selectAs(WorkItem.class)));
        rows.addAll(db.list(claimable.orderBy(r -> orders(r.createdAt().desc(), r.id().desc())).limit(head).selectAs(WorkItem.class)));
        rows.sort(NEWEST_FIRST);
        return new Page<>(rows.stream().skip((long) (page - 1) * size).limit(size).toList(), total, page, size);
    }
}
