package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

/** 认领收件箱里的一个待办（0006 的 3.2）：先认领先得，只有候选人能认领。 */
@Data
public class ClaimCommand implements FlowCommand {
    /** 待办 */
    private final Long workItemId;
}
