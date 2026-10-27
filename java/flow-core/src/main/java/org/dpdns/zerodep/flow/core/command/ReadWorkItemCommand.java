package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

/** 打开了自己的待办（待办详情里）：第一次打开时记下时间，列表里不再标"新"。 */
@Data
public class ReadWorkItemCommand implements FlowCommand {
    /** 待办 */
    private final Long workItemId;
}
