package org.dpdns.zerodep.flow.spring;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.view.stats.OpsStats;

import java.time.Duration;
import java.time.Instant;
import java.util.function.ToDoubleFunction;

/**
 * 引擎的指标（有 Micrometer 时；Spring Boot Actuator 会把 MeterBinder 自动绑到它的注册表上）：
 * <ul>
 *   <li>{@code flow.jobs.due}：到期没做的作业；{@code flow.jobs.due.oldest}：其中最早的晚了多少秒——一直涨说明作业轮询停了或做不过来；</li>
 *   <li>{@code flow.jobs.failed}：重试用完失败的作业（等人在异常处理里重试或跳过）；</li>
 *   <li>{@code flow.instances.active}：进行中的实例；</li>
 *   <li>{@code flow.jobs.run}、{@code flow.archive.instances}：这个进程做了几个作业、归档了几个实例（有作业轮询时）。</li>
 * </ul>
 * 几个量来自同一次查询（{@code engine.ops()}），抓取时最多每 {@link #REFRESH} 查一次库。
 */
public class FlowMetrics implements MeterBinder {
    /** 几个量共用一次查询，抓取再频繁也不会每个量查一遍库 */
    static final Duration REFRESH = Duration.ofSeconds(10);

    @SuppressWarnings("rawtypes")
    private final FlowEngine engine;
    /** 没有作业轮询（交给调度平台）时为 null */
    private final FlowJobPoller poller;
    private OpsStats last;

    @SuppressWarnings("rawtypes")
    public FlowMetrics(FlowEngine engine, FlowJobPoller poller) {
        this.engine = engine;
        this.poller = poller;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        gauge(registry, "flow.jobs.due", "flow jobs that are due but not run yet", OpsStats::getDueJobs);
        gauge(registry, "flow.jobs.due.oldest", "seconds the oldest due flow job is late", OpsStats::getOldestDueSeconds);
        gauge(registry, "flow.jobs.failed", "flow jobs that ran out of retries", OpsStats::getFailedJobs);
        gauge(registry, "flow.instances.active", "active or suspended flow instances", OpsStats::getActiveInstances);
        if (poller == null) return;
        FunctionCounter.builder("flow.jobs.run", poller, FlowJobPoller::getJobsRun).description("flow jobs run by this process").register(registry);
        FunctionCounter.builder("flow.archive.instances", poller, FlowJobPoller::getArchived).description("flow instances archived by this process")
            .register(registry);
    }

    private void gauge(MeterRegistry registry, String name, String description, ToDoubleFunction<OpsStats> value) {
        Gauge.builder(name, this, metrics -> value.applyAsDouble(metrics.snapshot())).description(description).register(registry);
    }

    /** 最近一次查询的结果，过了 REFRESH 再查 */
    synchronized OpsStats snapshot() {
        if (last == null || Duration.between(last.getAt(), Instant.now()).compareTo(REFRESH) > 0) last = engine.ops();
        return last;
    }
}
