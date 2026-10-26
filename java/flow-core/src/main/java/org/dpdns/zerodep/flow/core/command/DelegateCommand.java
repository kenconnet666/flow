package org.dpdns.zerodep.flow.core.command;

/**
 * 委派（0006 的 3.3）：请别人先办，办完回到自己手里再处理（{@code allow.delegate}）。
 *
 * @param workItemId 待办
 * @param userId     请谁办
 * @param reason     说明，记进流转记录
 */
public record DelegateCommand(Long workItemId, Long userId, String reason) implements FlowCommand { }
