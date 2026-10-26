package org.dpdns.zerodep.flow.model.runtime;

import java.time.Instant;

/**
 * 一条抄送（0007 的 {@code flow_cc}）：抄送节点或处理人手动抄送给一个人。
 *
 * @param node          抄送节点；手动抄送时是抄送人当时所在的节点，可以为 null
 * @param nodeName      节点名称（快照）
 * @param recipientId   收到的人
 * @param recipientName 收到的人的名字（快照）
 * @param fromUserId    谁抄送的；抄送节点时为 null
 * @param fromUserName  那个人的名字（快照）
 * @param message       附言
 * @param readAt        读的时间；没读时为 null
 */
public record Cc(Long id, Long instanceId, String node, String nodeName, Long recipientId, String recipientName,
                 Long fromUserId, String fromUserName, String message, Instant createdAt, Instant readAt) { }
