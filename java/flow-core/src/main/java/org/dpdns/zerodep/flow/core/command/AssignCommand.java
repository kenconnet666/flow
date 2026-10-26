package org.dpdns.zerodep.flow.core.command;

/**
 * 收件箱负责人指定承办人（0006 的 3.2 {@code assign}）：无人认领超时、等他指定的待办，直接落到选的人。
 *
 * @param workItemId 待办
 * @param userId     承办人
 */
public record AssignCommand(Long workItemId, Long userId) implements FlowCommand { }
