package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

import java.util.List;

/** 留言（0006 的 3.1）：在实例上说一句话，记进流转记录；@ 到的人收到通知（{@code mentioned}）。 */
@Data
public class CommentCommand implements FlowCommand {
    /** 实例 */
    private final Long instanceId;
    /** 内容 */
    private final String text;
    /** @ 的人（用户编号）；没有时为空 */
    private final List<Long> mentions;
}
