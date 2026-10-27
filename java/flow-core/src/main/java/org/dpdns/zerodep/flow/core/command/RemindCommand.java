package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

/**
 * 催办（0006 的 3.1）：给待办的处理人一个提醒（通知由接入方的监听发）。给了待办时只催它，否则催实例上全部打开着的待办。
 * 同一个待办一小时内只记一次。
 */
@Data
public class RemindCommand implements FlowCommand {
    /** 实例 */
    private final Long instanceId;
    /** 待办；不给时是实例上全部打开着的 */
    private final Long workItemId;
    /** 附言 */
    private final String message;
}
