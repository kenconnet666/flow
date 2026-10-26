package org.dpdns.zerodep.flow.core.command;

/**
 * 恢复（0006 的 3.4，管理员）：暂停的实例接着走。
 *
 * @param instanceId 实例
 */
public record ResumeCommand(Long instanceId) { }
