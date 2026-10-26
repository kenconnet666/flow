package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.id.IdStrategy;
import org.dpdns.zerodep.flow.core.store.WorkItemStore;
import org.dpdns.zerodep.flow.model.runtime.WorkItem;
import org.dpdns.zerodep.query.exec.Database;
import org.dpdns.zerodep.query.exec.Page;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.dpdns.zerodep.query.Sql.all;
import static org.dpdns.zerodep.query.Sql.any;
import static org.dpdns.zerodep.query.Sql.deleteFrom;
import static org.dpdns.zerodep.query.Sql.from;
import static org.dpdns.zerodep.query.Sql.insertInto;
import static org.dpdns.zerodep.query.Sql.orders;
import static org.dpdns.zerodep.query.Sql.update;
import static org.dpdns.zerodep.query.Sql.val;

/** 待办与候选的默认存储：{@code flow_work_item} 与 {@code flow_candidate} 表；收件箱的查法见 {@link #todo} */
final class QueryWorkItemStore extends StoreSupport implements WorkItemStore {
    QueryWorkItemStore(Database db, IdStrategy ids, Supplier<String> tenant) { super(db, ids, tenant); }

    /** flow_work_item 的一行 */
    record WorkItemRow(String tenantId, Long id, String kind, Long instanceId, String processKey, Short priority, String node, String nodeName,
                       Integer round, String state, String assignment, String arrival, String inboxKind, String inboxId, String assigneeId,
                       String assigneeName, LocalDateTime dueAt, LocalDateTime claimedAt, LocalDateTime createdAt, Integer remindCount,
                       Boolean needsDesignation, String outcome, String comment, String completedBy, String completedByName,
                       LocalDateTime completedAt, Integer rev) { }

    /** flow_candidate 的一行 */
    record CandidateRow(String tenantId, Long id, Long workItemId, String kind, String value) { }

    @Override
    public void insert(WorkItem w, List<Candidate> candidates) {
        String t = tenant.get();
        Long id = key(w.id());
        db.execute(insertInto(FlowTables.WorkItem.class).values(new WorkItemRow(t, id, "task", key(w.instanceId()), w.processKey(), (short) w.priority(),
            w.node(), w.nodeName(), w.round(), w.state(), w.assignment(), w.arrival(), w.inboxKind(), w.inboxId(), w.assigneeId(),
            w.assigneeName(), time(w.dueAt()), time(w.claimedAt()), time(w.createdAt()), 0, false, w.outcome(), w.comment(),
            w.completedBy(), w.completedByName(), time(w.completedAt()), 0)));
        if (candidates.isEmpty()) return;
        List<CandidateRow> rows = new ArrayList<>();
        for (Candidate c : candidates) rows.add(new CandidateRow(t, newId("flow_candidate"), id, c.kind(), c.value()));
        db.execute(insertInto(FlowTables.Candidate.class).values(rows));
    }

    @Override
    public Optional<WorkItem> find(String workItemId) {
        Long id = key(workItemId);
        return db.optional(from(FlowTables.WorkItem.class).where(r -> r.id().eq(id)).selectAs(WorkItemRow.class)).map(this::workItem);
    }

    @Override
    public List<Candidate> candidates(String workItemId) {
        Long id = key(workItemId);
        return db.list(from(FlowTables.Candidate.class).where(c -> c.workItemId().eq(id)).orderBy(c -> c.id()).selectAs(CandidateRow.class))
            .stream().map(c -> new Candidate(c.kind(), c.value())).toList();
    }

    @Override
    public boolean claim(String workItemId, String userId, String userName, Instant at) {
        Long id = key(workItemId);
        return db.execute(update(FlowTables.WorkItem.class).set(r -> {
            r.assigneeId().to(userId);
            r.assigneeName().to(userName);
            r.assignment().to(WorkItem.CLAIMED);
            r.claimedAt().to(time(at));
            r.rev().to(r.rev().plus(val(1)));
        }).where(r -> { r.id().eq(id); r.state().eq(WorkItem.OPEN); r.assigneeId().isNull(); })) == 1;
    }

    @Override
    public boolean complete(String workItemId, Completion c) {
        Long id = key(workItemId);
        boolean changed = db.execute(update(FlowTables.WorkItem.class).set(r -> {
            r.state().to(WorkItem.DONE);
            r.outcome().to(c.outcome());
            r.comment().to(c.comment());
            r.completedBy().to(c.userId());
            r.completedByName().to(c.userName());
            r.completedAt().to(time(c.at()));
            r.rev().to(r.rev().plus(val(1)));
        }).where(r -> { r.id().eq(id); r.state().eq(WorkItem.OPEN); })) == 1;
        if (changed) db.execute(deleteFrom(FlowTables.Candidate.class).where(r -> r.workItemId().eq(id)));
        return changed;
    }

    @Override
    public List<WorkItem> cancelOpen(String instanceId, Instant at) {
        Long instance = key(instanceId);
        List<WorkItem> open = db.list(from(FlowTables.WorkItem.class)
            .where(r -> { r.instanceId().eq(instance); r.state().eq(WorkItem.OPEN); })
            .orderBy(r -> r.id()).selectAs(WorkItemRow.class)).stream().map(this::workItem).toList();
        if (open.isEmpty()) return open;
        List<Long> keys = open.stream().map(w -> key(w.id())).toList();
        db.execute(update(FlowTables.WorkItem.class).set(r -> {
            r.state().to(WorkItem.CANCELED);
            r.rev().to(r.rev().plus(val(1)));
        }).where(r -> { r.id().in(keys); r.state().eq(WorkItem.OPEN); }));
        db.execute(deleteFrom(FlowTables.Candidate.class).where(r -> r.workItemId().in(keys)));
        return open;
    }

    @Override
    public List<WorkItem> ofInstance(String instanceId) {
        Long instance = key(instanceId);
        return db.list(from(FlowTables.WorkItem.class).where(r -> r.instanceId().eq(instance)).orderBy(r -> orders(r.createdAt(), r.id()))
            .selectAs(WorkItemRow.class)).stream().map(this::workItem).toList();
    }

    @Override
    public Optional<String> lastCompletedBy(String instanceId, String node) {
        Long instance = key(instanceId);
        return db.optional(from(FlowTables.WorkItem.class)
            .where(r -> { r.instanceId().eq(instance); r.node().eq(node); r.state().eq(WorkItem.DONE); })
            .orderBy(r -> orders(r.completedAt().desc(), r.id().desc())).limit(1).select(r -> r.completedBy()));
    }

    /**
     * 两段各取前"偏移 + 页长"条再合并（0007 第 4 节）：第一段按（租户, 处理人, 状态, 到达时间）的索引，第二段从候选的覆盖索引进；
     * 第二段要求没有处理人，两段不重叠，总数是两段之和。
     */
    @Override
    public Page<WorkItem> todo(Member member, int page, int size) {
        if (page < 1 || size < 1) throw new IllegalArgumentException("page and size start at 1");
        String t = tenant.get();
        String open = WorkItem.OPEN;
        int head = (page - 1) * size + size;
        var mine = from(FlowTables.WorkItem.class).where(r -> { r.tenantId().eq(t); r.assigneeId().eq(member.userId()); r.state().eq(open); });
        var claimable = from(FlowTables.WorkItem.class).where(r -> {
            r.tenantId().eq(t);
            r.assigneeId().isNull();
            r.state().eq(open);
            r.candidates().any(c -> any(() -> {
                for (Map.Entry<String, Set<String>> e : member.memberships().entrySet()) {
                    if (!e.getValue().isEmpty()) all(() -> { c.kind().eq(e.getKey()); c.value().in(e.getValue()); });
                }
            }));
            r.candidates().none(c -> { c.kind().eq(Candidate.EXCLUDED); c.value().eq(member.userId()); });
        });
        long total = db.count(mine.select(r -> r.id())) + db.count(claimable.select(r -> r.id()));
        List<WorkItemRow> rows = new ArrayList<>(db.list(mine.orderBy(r -> orders(r.createdAt().desc(), r.id().desc())).limit(head)
            .selectAs(WorkItemRow.class)));
        rows.addAll(db.list(claimable.orderBy(r -> orders(r.createdAt().desc(), r.id().desc())).limit(head).selectAs(WorkItemRow.class)));
        rows.sort(Comparator.comparing(WorkItemRow::createdAt).thenComparing(WorkItemRow::id).reversed());
        List<WorkItem> slice = rows.stream().skip((long) (page - 1) * size).limit(size)
            .map(this::workItem).toList();
        return new Page<>(slice, total, page, size);
    }

    private WorkItem workItem(WorkItemRow r) {
        return new WorkItem(text(r.id()), text(r.instanceId()), r.processKey(),
            r.priority() == null ? 0 : r.priority(), r.node(), r.nodeName(), r.round(), r.state(), r.assignment(), r.arrival(), r.inboxKind(),
            r.inboxId(), r.assigneeId(), r.assigneeName(), instant(r.createdAt()), instant(r.claimedAt()), instant(r.dueAt()), r.outcome(),
            r.comment(), r.completedBy(), r.completedByName(), instant(r.completedAt()), null, null);
    }
}
