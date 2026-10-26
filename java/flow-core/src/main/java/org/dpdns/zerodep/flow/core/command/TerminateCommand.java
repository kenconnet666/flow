package org.dpdns.zerodep.flow.core.command;

/**
 * 终止（0006 的 3.4，管理员）：实例直接结束，打开着的待办都取消。
 *
 * @param instanceId 实例
 * @param reason     原因
 */
public record TerminateCommand(Long instanceId, String reason) implements FlowCommand { }
