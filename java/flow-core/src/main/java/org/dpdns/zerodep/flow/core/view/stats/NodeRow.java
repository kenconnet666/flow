package org.dpdns.zerodep.flow.core.view.stats;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

import java.math.BigDecimal;

/** 按节点分组的一行（统计内部用）：处理完的个数、平均停留秒数、有截止时间的个数、其中超时的个数 */
@Data
@FieldNameConstants
public class NodeRow {
    private String node;
    private String nodeName;
    private Long done;
    private BigDecimal averageSeconds;
    private Long withDue;
    private Long overdue;
}
