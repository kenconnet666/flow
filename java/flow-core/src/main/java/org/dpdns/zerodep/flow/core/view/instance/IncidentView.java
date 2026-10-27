package org.dpdns.zerodep.flow.core.view.instance;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 异常（管理端）：重试用完的作业，多半是异步的自动节点做不成。管理员修好外部系统后重试（{@code retryJob}），
 * 或者不再做这个动作、指定一个结果往下走（{@code skipJob}），或者终止实例。
 */
@Data
public class IncidentView {
    /** 作业 */
    private final Long jobId;
    /** 作业的种类：{@code action} 自动节点的动作、{@code timer}、{@code overdue}…… */
    private final String kind;
    /** 实例 */
    private final Long instanceId;
    /** 实例的标题 */
    private final String instanceTitle;
    /** 流程标识 */
    private final String processKey;
    /** 卡在哪个节点 */
    private final String node;
    /** 节点的名称 */
    private final String nodeName;
    /** 试了几次 */
    private final int attempts;
    /** 最后一次失败的原因 */
    private final String error;
    /** 最后一次尝试的时间 */
    private final Instant failedAt;
    /** 能不能跳过、按结果往下走：自动节点的动作，实例还停在那个节点的那一轮上 */
    private final boolean skippable;
    /** 跳过时能选的结果（节点出边上写的）；只有一条没写结果的出边时是空的 */
    private final List<String> outcomes;
}
