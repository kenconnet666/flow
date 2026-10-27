package org.dpdns.zerodep.flow.core.view.instance;

import lombok.Data;

import java.time.Instant;

/** 一条抄送（按 {@code select(CcVO.class)} 读出）：抄送节点或处理人手动抄送给一个人 */
@Data
public class CcVO {
    private Long id;
    private Long instanceId;
    /** 抄送节点；手动抄送时为 null */
    private String node;
    private String nodeName;
    private Long recipientId;
    private String recipientName;
    /** 谁抄送的；抄送节点时为 null */
    private Long fromUserId;
    private String fromUserName;
    private String message;
    private Instant createdAt;
    /** 读的时间；没读时为 null */
    private Instant readAt;
}
