package org.dpdns.zerodep.flow.core.command;

/**
 * 改派（0006 的 3.4 {@code changeAssignees}，管理员；也是收件箱负责人的"指定承办人"）：待办直接落到这个人。
 *
 * @param workItemId 待办
 * @param userId     改给谁
 * @param reason     原因
 */
public record ReassignCommand(Long workItemId, Long userId, String reason) implements FlowCommand { }
