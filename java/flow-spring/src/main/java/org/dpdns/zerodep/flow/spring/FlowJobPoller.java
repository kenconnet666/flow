package org.dpdns.zerodep.flow.spring;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 定时做到期的作业（定时节点、超时、提醒、无人认领）：应用启动后每隔 {@code flow.jobs.interval} 调一次 {@code engine.runDueJobs()}，
 * 一批做满了接着做，直到没有到期的。多个实例同时跑不会重复做（引擎抢占作业）。顺带每小时做一次维护：删过期的幂等记录
 * （{@code engine.purgeRequests}，留 {@code flow.idempotency.retention}），打开了归档时搬一次（{@code engine.archive}，
 * {@code flow.archive.after}）；几个实例同时做也无妨（搬过的行已经不在热表里）。
 * <p>交给已有的调度平台时设 {@code flow.jobs.enabled=false}，由平台定时调 {@code engine.runDueJobs()}、{@code engine.purgeRequests(...)}
 * 与 {@code engine.archive(...)}。
 */
public class FlowJobPoller implements SmartLifecycle {
    private static final System.Logger LOG = System.getLogger(FlowJobPoller.class.getName());

    @SuppressWarnings("rawtypes")
    private final FlowEngine engine;
    /** 多久做一次维护（删过期的幂等记录、归档） */
    private static final Duration MAINTAIN_EVERY = Duration.ofHours(1);

    private final Duration interval;
    private final Duration retention;
    /** 结束多久之后归档；不归档时为 null */
    private final Duration archiveAfter;
    private ScheduledExecutorService scheduler;
    /** 上次维护的时间：启动后的第一轮就做一次 */
    private Instant lastMaintained = Instant.MIN;
    private volatile boolean running;

    @SuppressWarnings("rawtypes")
    public FlowJobPoller(FlowEngine engine, Duration interval, Duration retention, Duration archiveAfter) {
        this.engine = engine;
        this.interval = interval;
        this.retention = retention;
        this.archiveAfter = archiveAfter;
    }

    @Override
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "flow-jobs");
            thread.setDaemon(true);
            return thread;
        });
        running = true;
        long millis = interval.toMillis();
        scheduler.scheduleWithFixedDelay(this::poll, millis, millis, TimeUnit.MILLISECONDS);
    }

    /** 做到没有到期的为止；数据库暂时连不上这类错误记日志，下一轮再试 */
    private void poll() {
        try {
            while (running && engine.runDueJobs() > 0) {
                // 一批做完马上看还有没有：积压时尽快做完
            }
            maintainIfDue();
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "flow job polling failed; retrying in " + interval, e);
        }
    }

    /** 离上次维护过了一小时：删过期的幂等记录，打开了归档时搬一次 */
    private void maintainIfDue() {
        Instant now = Instant.now();
        if (Duration.between(lastMaintained, now).compareTo(MAINTAIN_EVERY) < 0) return;
        lastMaintained = now;
        long purged = engine.purgeRequests(retention);
        if (purged > 0) LOG.log(System.Logger.Level.DEBUG, "purged " + purged + " expired flow idempotency records");
        if (archiveAfter == null) return;
        int archived = engine.archive(archiveAfter);
        if (archived > 0) LOG.log(System.Logger.Level.INFO, "archived " + archived + " flow instances that ended more than " + archiveAfter + " ago");
    }

    @Override
    public void stop() {
        running = false;
        if (scheduler != null) scheduler.shutdown();
    }

    @Override
    public boolean isRunning() { return running; }
}
