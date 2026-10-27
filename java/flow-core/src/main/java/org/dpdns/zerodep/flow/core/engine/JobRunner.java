package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.db.FlowInterceptors;
import org.dpdns.zerodep.flow.core.entity.JobEntity;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 做到期的作业（0006 第 6 节的 {@code JobRunner}）：{@link #runDue} 读出一批到期的作业，逐个抢（带条件的更新，0001 第 4 条），
 * 抢到的切到它的租户、在一个事务里交给它那一种的 {@link JobHandler}。几个进程同时跑也不会重复做。
 * <p>谁来定时调 {@link #runDue} 由接入方定：Spring Boot 里默认每隔几秒调一次（{@code flow.jobs.*}），
 * 也可以关掉它、交给已有的调度平台调 {@code engine.runDueJobs()}。
 * <p>失败的作业事务回滚，过一会儿重试（间隔由那一种的 {@link JobHandler#retryDelay} 定，默认 1、4、9……分钟），
 * 重试用完标成失败、留在表里给管理员看，再交给 {@link JobHandler#gaveUp} 善后。
 */
public class JobRunner {
    private static final System.Logger LOG = System.getLogger(JobRunner.class.getName());
    /** 一次最多做多少个 */
    private static final int BATCH = 50;
    /** 领到的作业多久没做完算这个进程挂了，别的进程可以再领 */
    private static final Duration LEASE = Duration.ofMinutes(5);

    private final EngineContext context;
    private final Map<String, JobHandler> handlers = new LinkedHashMap<>();
    private final String owner;

    /**
     * @param handlers 各种作业的处理；同一种出现两次时后面的换掉前面的（接入方注册的放在内置的后面）
     */
    public JobRunner(EngineContext context, List<JobHandler> handlers) {
        this.context = context;
        for (JobHandler handler : handlers) this.handlers.put(handler.kind(), handler);
        // 领取人记成"进程号@主机名"：排查卡住的作业时知道是哪个进程领的
        this.owner = ManagementFactory.getRuntimeMXBean().getName();
    }

    /** 做一批到期的作业，返回做了几个（含失败的）；一批做满时调用方可以马上再调 */
    public int runDue() {
        Instant now = context.getClock().instant();
        int ran = 0;
        for (JobEntity job : due(now)) {
            if (!lock(job.getId(), now)) continue;   // 别的进程先领了
            TenantScope.run(job.getTenantId(), () -> runOne(job, now));
            ran++;
        }
        return ran;
    }

    /** 到期、没人领着（或领的人超时了）的作业，按到期时间先后；跨租户 */
    private List<JobEntity> due(Instant now) {
        return context.getDb().queryable(JobEntity.class).noInterceptor(FlowInterceptors.TENANT)
            .where(j -> {
                j.state().eq(JobEntity.PENDING);
                j.dueAt().le(now);
                j.or(() -> { j.lockedUntil().isNull(); j.lockedUntil().lt(now); });
            })
            .orderBy(j -> { j.dueAt().asc(); j.id().asc(); })
            .limit(BATCH).toList();
    }

    /** 抢一个作业：还等着、没人领着时记下是谁领的、领到什么时候；跨租户 */
    private boolean lock(Long jobId, Instant now) {
        return context.getDb().updatable(JobEntity.class).noInterceptor(FlowInterceptors.TENANT)
            .setColumns(j -> { j.lockedBy().set(owner); j.lockedUntil().set(now.plus(LEASE)); })
            .where(j -> {
                j.id().eq(jobId);
                j.state().eq(JobEntity.PENDING);
                j.or(() -> { j.lockedUntil().isNull(); j.lockedUntil().lt(now); });
            })
            .executeRows() == 1;
    }

    private void runOne(JobEntity job, Instant now) {
        JobHandler handler = handlers.get(job.getKind());
        try {
            if (handler == null) throw new IllegalStateException("no JobHandler is registered for job kind '" + job.getKind() + "'");
            context.transaction(() -> {
                JobHandler.Next next = handler.run(job, now);
                if (next == null) {
                    context.getDb().deletable(JobEntity.class).allowDeleteStatement(true).whereById(job.getId()).executeRows();
                } else {
                    // 下次再做（每隔一段时间的提醒、实例暂停时往后推）：换上新的时刻与数据，放开
                    context.getDb().updatable(JobEntity.class)
                        .setColumns(j -> { j.dueAt().set(next.getAt()); j.payload().set(next.getPayload()); j.lockedBy().set((String) null); j.lockedUntil().set((Instant) null); })
                        .whereById(job.getId()).executeRows();
                }
            });
        } catch (RuntimeException e) {
            int attempts = job.getAttempts() + 1;
            String error = describe(e);
            LOG.log(System.Logger.Level.WARNING, "flow job " + job.getId() + " (" + job.getKind() + ") failed, attempt " + attempts, e);
            boolean retry = attempts < job.getMaxAttempts();
            Instant again = retry ? now.plus(handler == null ? Duration.ofMinutes(1) : handler.retryDelay(job, attempts)) : null;
            // 失败了：失败次数加一、记下原因、放开；还能重试的过一会儿再做，重试用完的标成失败、留着给管理员看
            context.getDb().updatable(JobEntity.class)
                .setColumns(j -> {
                    j.attempts().increment();
                    j.lastError().set(error);
                    j.lockedBy().set((String) null);
                    j.lockedUntil().set((Instant) null);
                    if (retry) j.dueAt().set(again);
                    else j.state().set(JobEntity.FAILED);
                })
                .whereById(job.getId()).executeRows();
            if (!retry && handler != null) giveUp(handler, job, error, now);
        }
    }

    /**
     * 给管理员看的失败原因：最里层异常的话（动作抛的异常常被包成 flow.extension-failed，外层的话没有用）；没有话时写类名。完整的堆栈在日志里
     */
    static String describe(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root.getMessage() != null ? root.getMessage() : root.getClass().getName();
    }

    /** 重试用完的善后（记一笔流转记录……）；它再失败只记日志，作业已经是失败的了 */
    private void giveUp(JobHandler handler, JobEntity job, String error, Instant now) {
        try {
            context.transaction(() -> handler.gaveUp(job, error, now));
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "flow job " + job.getId() + " gave up and its handler failed to record it", e);
        }
    }
}
