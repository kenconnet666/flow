package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

/** 失败的作业重新来过（管理员，0006 的 3.4 {@code retryJob}）：异步的自动节点重试用完后，修好外部系统再让它做一次。 */
@Data
public class RetryJobCommand implements FlowCommand {
    /** 作业（流转记录 {@code job.failed} 的 data 里有） */
    private final Long jobId;
}
