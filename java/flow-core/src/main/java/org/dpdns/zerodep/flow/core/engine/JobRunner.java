package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.store.JobStore;
import org.dpdns.zerodep.flow.core.store.JobStore.Job;

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
 * <p>失败的作业事务回滚，过一会儿重试（1、4、9……分钟），重试用完标成失败、留在表里给管理员看。
 */
public final class JobRunner {
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
        Instant now = context.clock().instant();
        JobStore jobs = context.stores().jobs();
        int ran = 0;
        for (Job job : jobs.due(now, BATCH)) {
            if (!jobs.lock(job.id(), owner, now, now.plus(LEASE))) continue;   // 别的进程先领了
            TenantScope.run(job.tenantId(), () -> runOne(job, now));
            ran++;
        }
        return ran;
    }

    private void runOne(Job job, Instant now) {
        JobStore jobs = context.stores().jobs();
        try {
            JobHandler handler = handlers.get(job.kind());
            if (handler == null) throw new IllegalStateException("no JobHandler is registered for job kind '" + job.kind() + "'");
            context.db().transaction(() -> {
                JobHandler.Next next = handler.run(job, now);
                if (next == null) jobs.done(job.id());
                else jobs.reschedule(job.id(), next.at(), next.payload());
            });
        } catch (RuntimeException e) {
            int attempts = job.attempts() + 1;
            String error = e.getClass().getName() + ": " + e.getMessage();
            LOG.log(System.Logger.Level.WARNING, "flow job " + job.id() + " (" + job.kind() + ") failed, attempt " + attempts, e);
            if (attempts >= job.maxAttempts()) jobs.fail(job.id(), error);
            else jobs.retry(job.id(), now.plus(Duration.ofMinutes((long) attempts * attempts)), error);
        }
    }
}
