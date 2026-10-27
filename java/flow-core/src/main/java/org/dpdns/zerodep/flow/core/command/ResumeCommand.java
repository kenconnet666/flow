package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

/** 恢复（0006 的 3.4，管理员）：暂停的实例接着走。 */
@Data
public class ResumeCommand implements FlowCommand {
    /** 实例 */
    private final Long instanceId;
}
