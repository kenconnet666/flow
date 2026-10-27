package org.dpdns.zerodep.flow.core.view.stats;

import lombok.Data;

/** 一个处理人的工作量（0002 §9 统计）：一段时间里处理完的、现在手上打开着的 */
@Data
public class HandlerStats {
    private Long userId;
    private String userName;
    private long done;
    private long open;
}
