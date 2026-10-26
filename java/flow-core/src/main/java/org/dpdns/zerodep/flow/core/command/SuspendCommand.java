package org.dpdns.zerodep.flow.core.command;

/**
 * 暂停（0006 的 3.4，管理员）：实例停在原地，待办都不能处理，直到恢复。
 *
 * @param instanceId 实例
 * @param reason     原因
 */
public record SuspendCommand(Long instanceId, String reason) { }
