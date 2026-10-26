package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.store.JobStore;
import org.dpdns.zerodep.query.exec.Database;
import org.dpdns.zerodep.query.exec.Page;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.dpdns.zerodep.query.Sql.any;
import static org.dpdns.zerodep.query.Sql.deleteFrom;
import static org.dpdns.zerodep.query.Sql.from;
import static org.dpdns.zerodep.query.Sql.insertInto;
import static org.dpdns.zerodep.query.Sql.orders;
import static org.dpdns.zerodep.query.Sql.update;
import static org.dpdns.zerodep.query.Sql.val;

/**
 * 作业的默认存储：{@code flow_job} 表。领取走（{@code state}, {@code due_at}）的索引，跨租户（关掉租户过滤器）；
 * 抢占是带条件的更新，改到 0 行就是别的进程先领了。
 */
final class QueryJobStore implements JobStore {
    /** 租户过滤器的名字（FlowDatabase 里起的） */
    private static final String TENANT = "flow-tenant";

    private final Database db;

    QueryJobStore(Database db) { this.db = db; }

    @Override
    public void schedule(NewJob job) {
        db.execute(insertInto(FlowTables.Job.class).values(job));
    }

    @Override
    public List<Job> due(Instant now, int limit) {
        return db.list(from(FlowTables.Job.class).unfiltered(TENANT)
            .where(r -> {
                r.state().eq(Job.PENDING);
                r.dueAt().le(now);
                any(() -> { r.lockedUntil().isNull(); r.lockedUntil().lt(now); });
            })
            .orderBy(r -> orders(r.dueAt(), r.id()))
            .selectAs(Job.class).limit(limit));
    }

    @Override
    public boolean lock(Long jobId, String owner, Instant now, Instant until) {
        return db.execute(update(FlowTables.Job.class).unfiltered(TENANT)
            .set(r -> { r.lockedBy().to(owner); r.lockedUntil().to(until); })
            .where(r -> {
                r.id().eq(jobId);
                r.state().eq(Job.PENDING);
                any(() -> { r.lockedUntil().isNull(); r.lockedUntil().lt(now); });
            })) == 1;
    }

    @Override
    public void done(Long jobId) {
        db.execute(deleteFrom(FlowTables.Job.class).unfiltered(TENANT).where(r -> r.id().eq(jobId)));
    }

    @Override
    public void reschedule(Long jobId, Instant dueAt, Map<String, Object> payload) {
        db.execute(update(FlowTables.Job.class).unfiltered(TENANT).set(r -> {
            r.dueAt().to(dueAt);
            r.payload().to(payload);
            r.lockedBy().to((String) null);
            r.lockedUntil().to((Instant) null);
        }).where(r -> r.id().eq(jobId)));
    }

    @Override
    public void retry(Long jobId, Instant dueAt, String error) {
        db.execute(update(FlowTables.Job.class).unfiltered(TENANT).set(r -> {
            r.dueAt().to(dueAt);
            r.attempts().to(r.attempts().plus(val(1)));
            r.lastError().to(error);
            r.lockedBy().to((String) null);
            r.lockedUntil().to((Instant) null);
        }).where(r -> r.id().eq(jobId)));
    }

    @Override
    public void fail(Long jobId, String error) {
        db.execute(update(FlowTables.Job.class).unfiltered(TENANT).set(r -> {
            r.state().to(Job.FAILED);
            r.attempts().to(r.attempts().plus(val(1)));
            r.lastError().to(error);
            r.lockedBy().to((String) null);
            r.lockedUntil().to((Instant) null);
        }).where(r -> r.id().eq(jobId)));
    }

    @Override
    public boolean revive(Long jobId, Instant now) {
        return db.execute(update(FlowTables.Job.class).set(r -> {
            r.state().to(Job.PENDING);
            r.attempts().to(0);
            r.dueAt().to(now);
            r.lockedBy().to((String) null);
            r.lockedUntil().to((Instant) null);
        }).where(r -> { r.id().eq(jobId); r.state().eq(Job.FAILED); })) == 1;
    }

    @Override
    public Page<Job> failed(int page, int size) {
        return db.page(from(FlowTables.Job.class).where(r -> r.state().eq(Job.FAILED)).orderBy(r -> orders(r.dueAt().desc(), r.id().desc()))
            .selectAs(Job.class), page, size);
    }

    @Override
    public void cancelForInstance(Long instanceId) {
        db.execute(deleteFrom(FlowTables.Job.class).where(r -> { r.instanceId().eq(instanceId); r.state().eq(Job.PENDING); }));
    }
}
