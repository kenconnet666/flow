package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

/** 转办（0006 的 3.3）：把自己的待办交给别人，自己不再参与。节点上 {@code allow.transfer} 关掉时不能转办。 */
@Data
public class TransferCommand implements FlowCommand {
    /** 待办 */
    private final Long workItemId;
    /** 交给谁 */
    private final Long userId;
    /** 原因，记进流转记录 */
    private final String reason;
}
