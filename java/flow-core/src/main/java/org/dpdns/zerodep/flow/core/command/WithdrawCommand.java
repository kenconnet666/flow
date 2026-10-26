package org.dpdns.zerodep.flow.core.command;

/**
 * 发起人撤回整个实例（0006 的 3.1），能不能撤回按定义的 {@code withdraw} 规则（0005 的 2.1）。
 *
 * @param instanceId 实例
 * @param reason     原因，记进流转记录
 */
public record WithdrawCommand(Long instanceId, String reason) implements FlowCommand { }
