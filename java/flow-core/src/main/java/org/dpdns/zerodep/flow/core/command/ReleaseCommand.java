package org.dpdns.zerodep.flow.core.command;

/**
 * 退回收件箱（0006 的 3.2）：认领了又不办，放回去让别人认领；自己以后不能再认领这一条。
 *
 * @param workItemId 待办
 * @param reason     原因，记进流转记录
 */
public record ReleaseCommand(Long workItemId, String reason) { }
