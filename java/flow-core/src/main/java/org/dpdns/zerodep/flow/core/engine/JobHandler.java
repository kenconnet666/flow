package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.store.JobStore.Job;

import java.time.Instant;
import java.util.Map;

/**
 * 一种作业怎么做（0006 第 6 节的 {@code JobHandler}）。内置的是定时节点、超时、提醒、无人认领（{@link BuiltinJobs}）；
 * 接入方在 {@code FlowEngine.Builder.jobHandler} 注册新的种类，同名的换掉内置的。
 * <p>{@link JobRunner} 在一个事务里调 {@link #run}，调之前已经切到作业的租户；抛异常时事务回滚、过一会儿重试，重试用完标成失败。
 */
public interface JobHandler {
    /** 处理的作业种类（{@code flow_job.kind}） */
    String kind();

    /** 做一个作业；返回下次再做的时刻与数据（每隔一段时间的提醒、实例暂停时往后推），做完了返回 null（作业删掉） */
    Next run(Job job, Instant now);

    /**
     * 下次再做。
     *
     * @param at      什么时候
     * @param payload 那时作业带的数据
     */
    record Next(Instant at, Map<String, Object> payload) { }
}
