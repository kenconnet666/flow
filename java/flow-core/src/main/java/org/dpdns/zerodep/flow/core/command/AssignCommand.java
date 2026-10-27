package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

/** 收件箱负责人指定承办人（0006 的 3.2 {@code assign}）：无人认领超时、等他指定的待办，直接落到选的人。 */
@Data
public class AssignCommand implements FlowCommand {
    /** 待办 */
    private final Long workItemId;
    /** 承办人 */
    private final Long userId;
}
