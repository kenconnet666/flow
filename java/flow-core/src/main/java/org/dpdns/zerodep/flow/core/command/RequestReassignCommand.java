package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

/**
 * 申请改派（0002 §4）：收件箱负责人指定给我的待办，我申请换人。待办回到负责人的"待我指定"里，由他重新指定；指定之前仍由我处理。
 */
@Data
public class RequestReassignCommand implements FlowCommand {
    /** 待办 */
    private final Long workItemId;
    /** 原因（必填：负责人看了决定换给谁） */
    private final String reason;
}
