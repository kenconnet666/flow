package org.dpdns.zerodep.flow.core.command;

/**
 * 转办（0006 的 3.3）：把自己的待办交给别人，自己不再参与。节点上 {@code allow.transfer} 关掉时不能转办。
 *
 * @param workItemId 待办
 * @param userId     交给谁
 * @param reason     原因，记进流转记录
 */
public record TransferCommand(Long workItemId, Long userId, String reason) implements FlowCommand { }
