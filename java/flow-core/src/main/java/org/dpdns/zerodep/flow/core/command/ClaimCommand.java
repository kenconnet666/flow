package org.dpdns.zerodep.flow.core.command;

/**
 * 认领收件箱里的一个待办（0006 的 3.2）：先认领先得，只有候选人能认领。
 *
 * @param workItemId 待办
 */
public record ClaimCommand(Long workItemId) implements FlowCommand { }
