package org.dpdns.zerodep.flow.core.view.stats;

import lombok.Data;

/** 一个人工节点上的处理情况（0002 §9 统计）：处理了多少、平均停留多久、超时的比例 */
@Data
public class NodeStats {
    private String node;
    private String nodeName;
    /** 处理完的待办数 */
    private long done;
    /** 平均停留（到达到处理完），小时 */
    private double averageHours;
    /** 处理完时已经过了截止时间的比例（0–1）；没有截止时间的不算 */
    private double overdueRate;
}
