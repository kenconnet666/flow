package org.dpdns.zerodep.flow.core.command;

import java.util.List;

/**
 * 加签（0006 的 3.3，{@code allow.addSign}）：
 * <ul>
 *   <li>{@code before} 前加签：加的人先办，都办完回到自己手里；</li>
 *   <li>{@code after} 后加签：自己办完后加的人再办，都办完才往下走；</li>
 *   <li>{@code parallel} 并加：自己和加的人一起办，都办完才往下走。</li>
 * </ul>
 * 加的人办的结果只是意见：节点按自己的结果往下走。
 *
 * @param workItemId 待办
 * @param users      加谁
 * @param mode       {@code before}、{@code after}、{@code parallel}
 * @param reason     说明，记进流转记录
 */
public record AddSignersCommand(Long workItemId, List<Long> users, String mode, String reason) implements FlowCommand {
    public AddSignersCommand { users = users == null ? List.of() : List.copyOf(users); }
}
