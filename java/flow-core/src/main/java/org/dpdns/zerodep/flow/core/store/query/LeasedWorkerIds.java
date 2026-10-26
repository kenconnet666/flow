package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.id.WorkerIdProvider;
import org.dpdns.zerodep.query.annotation.Column;
import org.dpdns.zerodep.query.annotation.Table;
import org.dpdns.zerodep.query.exec.Database;
import org.dpdns.zerodep.query.exec.QueryExecutionException;
import org.dpdns.zerodep.query.table.Entity;
import org.dpdns.zerodep.query.table.column.ColInteger;
import org.dpdns.zerodep.query.table.column.ColText;
import org.dpdns.zerodep.query.table.column.ColTimestamp;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.dpdns.zerodep.query.Sql.from;
import static org.dpdns.zerodep.query.Sql.insertInto;
import static org.dpdns.zerodep.query.Sql.orders;
import static org.dpdns.zerodep.query.Sql.update;

/**
 * 在 {@code flow_worker} 表里租用雪花的机器号（docs/decisions/0001 的"雪花的机器号"，默认的 {@link WorkerIdProvider}）：
 * 先用带条件的 UPDATE 抢一个租约过期的号；没有就 INSERT 下一个没用过的号（主键挡住同时插入）；号用完报错。
 * 拿到号以后后台线程每隔租期的三分之一续一次；{@link #close} 时停止续租并放掉租约，下一个进程马上能用。
 * 续租失败（例如数据库连不上的时间超过了租期）时，这个号可能已经给了别人，后台线程停止续租并记下原因，
 * 下一次 {@link #workerId} 报错；已经造好的发号器照常发号，所以租期要远长于数据库可能中断的时间（默认 10 分钟）。
 */
public final class LeasedWorkerIds implements WorkerIdProvider, AutoCloseable {
    /** 机器号的哪一段：经典雪花只有一段 */
    private static final String SCOPE = "worker";

    @Table("flow_worker")
    interface Worker extends Entity {
        @Column("tenant_id") ColText<String> tenantId();
        @Column("scope") ColText<String> scope();
        @Column("worker_id") ColInteger<Integer> workerId();
        @Column("owner") ColText<String> owner();
        @Column("lease_until") ColTimestamp<LocalDateTime> leaseUntil();
        @Column("heartbeat_at") ColTimestamp<LocalDateTime> heartbeatAt();
    }

    /** flow_worker 的一行 */
    record WorkerRow(String tenantId, String scope, Integer workerId, String owner, LocalDateTime leaseUntil, LocalDateTime heartbeatAt) { }

    private final Database db;
    private final Clock clock;
    private final Duration lease;
    private final String owner;
    private ScheduledExecutorService heartbeat;
    private Integer leased;
    private volatile RuntimeException lost;

    /** 租期默认 10 分钟 */
    public LeasedWorkerIds(Database db, Clock clock) { this(db, clock, Duration.ofMinutes(10)); }

    /** lease 是租期 */
    public LeasedWorkerIds(Database db, Clock clock, Duration lease) {
        this.db = db;
        this.clock = clock;
        this.lease = lease;
        // 进程号@主机名，加随机串区分同一进程里的几个引擎；列宽 128
        String name = ManagementFactory.getRuntimeMXBean().getName() + "/" + UUID.randomUUID();
        this.owner = name.length() <= 128 ? name : name.substring(0, 128);
    }

    @Override
    public synchronized long workerId(long limit) {
        if (lost != null) throw lost;
        if (leased != null) return leased;
        for (int attempt = 0; attempt < 10; attempt++) {
            Integer id = takeExpired();
            if (id == null) id = insertNext(limit);
            if (id != null) {
                leased = id;
                startHeartbeat();
                return id;
            }
        }
        throw new IllegalStateException("could not lease a snowflake worker id from flow_worker after 10 attempts");
    }

    /** 抢一个租约过期的号；抢不到时为 null */
    private Integer takeExpired() {
        LocalDateTime now = now();
        List<WorkerRow> expired = db.list(from(Worker.class).where(w -> { w.scope().eq(SCOPE); w.leaseUntil().lt(now); })
            .orderBy(w -> orders(w.workerId())).limit(10).selectAs(WorkerRow.class));
        for (WorkerRow row : expired) {
            long changed = db.execute(update(Worker.class).set(w -> {
                w.owner().to(owner);
                w.leaseUntil().to(now.plus(lease));
                w.heartbeatAt().to(now);
            }).where(w -> { w.scope().eq(SCOPE); w.workerId().eq(row.workerId()); w.leaseUntil().eq(row.leaseUntil()); }));
            if (changed == 1) return row.workerId();
        }
        return null;
    }

    /** 插入下一个没用过的号；别人同时插了同一个号时为 null（重试） */
    private Integer insertNext(long limit) {
        Integer max = db.optional(from(Worker.class).where(w -> w.scope().eq(SCOPE)).orderBy(w -> w.workerId().desc()).limit(1)
            .select(w -> w.workerId())).orElse(-1);
        int next = max + 1;
        if (next >= limit) throw new IllegalStateException("all " + limit + " snowflake worker ids are leased; widen workerBits or wait for leases to expire");
        LocalDateTime now = now();
        try {
            db.execute(insertInto(Worker.class).values(new WorkerRow("", SCOPE, next, owner, now.plus(lease), now)));
            return next;
        } catch (QueryExecutionException e) {
            return null;
        }
    }

    private void startHeartbeat() {
        heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "flow-worker-lease");
            t.setDaemon(true);
            return t;
        });
        long every = Math.max(1, lease.toMillis() / 3);
        heartbeat.scheduleAtFixedRate(this::renew, every, every, TimeUnit.MILLISECONDS);
    }

    private void renew() {
        try {
            LocalDateTime now = now();
            long changed = db.execute(update(Worker.class).set(w -> {
                w.leaseUntil().to(now.plus(lease));
                w.heartbeatAt().to(now);
            }).where(w -> { w.scope().eq(SCOPE); w.workerId().eq(leased); w.owner().eq(owner); }));
            if (changed == 0) fail(new IllegalStateException("the lease on snowflake worker id " + leased + " was taken by another process"));
        } catch (RuntimeException e) {
            // 一次续租失败还不要紧：租期内下一次成功即可；拿到确切的"被别人占了"才停
        }
    }

    private void fail(RuntimeException reason) {
        lost = reason;
        heartbeat.shutdown();
    }

    /** 停止续租并放掉租约 */
    @Override
    public synchronized void close() {
        if (heartbeat != null) heartbeat.shutdownNow();
        if (leased == null || lost != null) return;
        LocalDateTime now = now();
        db.execute(update(Worker.class).set(w -> w.leaseUntil().to(now)).where(w -> { w.scope().eq(SCOPE); w.workerId().eq(leased); w.owner().eq(owner); }));
        leased = null;
    }

    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
}
