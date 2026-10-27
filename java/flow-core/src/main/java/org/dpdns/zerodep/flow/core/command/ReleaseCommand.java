package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

/** 退回收件箱（0006 的 3.2）：认领了又不办，放回去让别人认领；自己以后不能再认领这一条。 */
@Data
public class ReleaseCommand implements FlowCommand {
    /** 待办 */
    private final Long workItemId;
    /** 原因，记进流转记录 */
    private final String reason;
}
