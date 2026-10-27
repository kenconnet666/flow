package org.dpdns.zerodep.flow.core.view.stats;

import lombok.Data;

/** 一个流程的实例数（0002 §9 统计）：进行中（含暂停）、正常结束、终止与撤回 */
@Data
public class ProcessStats {
    private String processKey;
    private long running;
    private long completed;
    private long stopped;
}
