package org.dpdns.zerodep.flow.core.view.stats;

import lombok.Data;

import java.time.Instant;

/**
 * 运维看的几个数（全部租户）：作业有没有积压、有多少作业重试用完失败了（等人处理的异常）、进行中的实例有多少。
 * 作业轮询正常时到期没做的应当接近 0，最早的那个也只晚几个轮询间隔；一直涨说明轮询停了或者做不过来。
 */
@Data
public class OpsStats {
    /** 什么时候算的 */
    private Instant at;
    /** 已经到期、还没做的作业 */
    private long dueJobs;
    /** 到期没做的作业里最早的那个晚了多少秒；没有时 0 */
    private long oldestDueSeconds;
    /** 重试用完、失败了的作业（异常处理里的那些） */
    private long failedJobs;
    /** 进行中（含暂停）的实例 */
    private long activeInstances;
}
