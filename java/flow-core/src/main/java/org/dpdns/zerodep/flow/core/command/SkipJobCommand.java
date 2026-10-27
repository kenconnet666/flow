package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

/** 跳过失败的作业（管理员，0005 的 4.1 {@code onFailure}）：异步的自动节点重试用完后，不再做这个动作，按指定的结果往下走。 */
@Data
public class SkipJobCommand implements FlowCommand {
    /** 作业（异常列表里的，或流转记录 {@code job.failed} 的 data 里有） */
    private final Long jobId;
    /** 走哪个结果的出边；节点只有一条没写结果的出边时不填 */
    private final String outcome;
    /** 原因（写进流转记录） */
    private final String reason;
}
