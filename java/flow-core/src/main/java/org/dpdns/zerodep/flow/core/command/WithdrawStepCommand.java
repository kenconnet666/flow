package org.dpdns.zerodep.flow.core.command;

/**
 * 处理人撤回刚处理的一步（0006 的 3.3 {@code withdrawStep}）：下一步还没人动时，待办回到自己手里重新处理。
 *
 * @param workItemId 刚处理的待办
 * @param reason     原因，记进流转记录
 */
public record WithdrawStepCommand(Long workItemId, String reason) implements FlowCommand { }
