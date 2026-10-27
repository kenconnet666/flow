package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

/** 留言（0006 的 3.1）：在实例上说一句话，记进流转记录。 */
@Data
public class CommentCommand implements FlowCommand {
    /** 实例 */
    private final Long instanceId;
    /** 内容 */
    private final String text;
}
