package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

/** 改派（0006 的 3.4 {@code changeAssignees}，管理员；也是收件箱负责人的"指定承办人"）：待办直接落到这个人。 */
@Data
public class ReassignCommand implements FlowCommand {
    /** 待办 */
    private final Long workItemId;
    /** 改给谁 */
    private final Long userId;
    /** 原因 */
    private final String reason;
}
