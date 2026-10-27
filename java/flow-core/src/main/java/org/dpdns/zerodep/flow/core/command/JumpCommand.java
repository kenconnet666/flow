package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

/** 跳转（0006 的 3.4，管理员）：把实例移到任意节点，原来打开着的待办都取消，从那个节点重新走。 */
@Data
public class JumpCommand implements FlowCommand {
    /** 实例 */
    private final Long instanceId;
    /** 移到哪个节点 */
    private final String node;
    /** 原因 */
    private final String reason;
}
