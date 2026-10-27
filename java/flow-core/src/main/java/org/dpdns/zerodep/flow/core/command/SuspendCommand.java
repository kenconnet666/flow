package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

/** 暂停（0006 的 3.4，管理员）：实例停在原地，待办都不能处理，直到恢复。 */
@Data
public class SuspendCommand implements FlowCommand {
    /** 实例 */
    private final Long instanceId;
    /** 原因 */
    private final String reason;
}
