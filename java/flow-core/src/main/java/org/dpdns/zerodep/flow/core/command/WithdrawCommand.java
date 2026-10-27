package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

/** 发起人撤回整个实例（0006 的 3.1），能不能撤回按定义的 {@code withdraw} 规则（0005 的 2.1）。 */
@Data
public class WithdrawCommand implements FlowCommand {
    /** 实例 */
    private final Long instanceId;
    /** 原因，记进流转记录 */
    private final String reason;
}
