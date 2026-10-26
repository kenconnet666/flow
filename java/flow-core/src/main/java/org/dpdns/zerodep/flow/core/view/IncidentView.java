package org.dpdns.zerodep.flow.core.view;

import java.time.Instant;
import java.util.List;

/**
 * 异常（管理端）：重试用完的作业，多半是异步的自动节点做不成。管理员修好外部系统后重试（{@code retryJob}），
 * 或者不再做这个动作、指定一个结果往下走（{@code skipJob}），或者终止实例。
 *
 * @param jobId         作业
 * @param kind          作业的种类：{@code action} 自动节点的动作、{@code timer}、{@code overdue}……
 * @param instanceId    实例
 * @param instanceTitle 实例的标题
 * @param processKey    流程标识
 * @param node          卡在哪个节点
 * @param nodeName      节点的名称
 * @param attempts      试了几次
 * @param error         最后一次失败的原因
 * @param failedAt      最后一次尝试的时间
 * @param skippable     能不能跳过、按结果往下走：自动节点的动作，实例还停在那个节点的那一轮上
 * @param outcomes      跳过时能选的结果（节点出边上写的）；只有一条没写结果的出边时是空的
 */
public record IncidentView(Long jobId, String kind, Long instanceId, String instanceTitle, String processKey,
                           String node, String nodeName, int attempts, String error, Instant failedAt,
                           boolean skippable, List<String> outcomes) { }
