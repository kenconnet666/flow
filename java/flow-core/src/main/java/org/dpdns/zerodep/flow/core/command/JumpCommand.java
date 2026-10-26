package org.dpdns.zerodep.flow.core.command;

/**
 * 跳转（0006 的 3.4，管理员）：把实例移到任意节点，原来打开着的待办都取消，从那个节点重新走。
 *
 * @param instanceId 实例
 * @param node       移到哪个节点
 * @param reason     原因
 */
public record JumpCommand(Long instanceId, String node, String reason) { }
