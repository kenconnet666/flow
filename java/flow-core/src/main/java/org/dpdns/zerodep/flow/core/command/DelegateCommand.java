package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

/** 委派（0006 的 3.3）：请别人先办，办完回到自己手里再处理（{@code allow.delegate}）。 */
@Data
public class DelegateCommand implements FlowCommand {
    /** 待办 */
    private final Long workItemId;
    /** 请谁办 */
    private final Long userId;
    /** 说明，记进流转记录 */
    private final String reason;
}
