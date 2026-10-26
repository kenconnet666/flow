package org.dpdns.zerodep.flow.core.command;

/**
 * 留言（0006 的 3.1）：在实例上说一句话，记进流转记录。
 *
 * @param instanceId 实例
 * @param text       内容
 */
public record CommentCommand(Long instanceId, String text) { }
