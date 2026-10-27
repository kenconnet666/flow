package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

/** 处理人撤回刚处理的一步（0006 的 3.3 {@code withdrawStep}）：下一步还没人动时，待办回到自己手里重新处理。 */
@Data
public class WithdrawStepCommand implements FlowCommand {
    /** 刚处理的待办 */
    private final Long workItemId;
    /** 原因，记进流转记录 */
    private final String reason;
}
