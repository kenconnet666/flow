package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

/** 终止（0006 的 3.4，管理员）：实例直接结束，打开着的待办都取消。 */
@Data
public class TerminateCommand implements FlowCommand {
    /** 实例 */
    private final Long instanceId;
    /** 原因 */
    private final String reason;
}
