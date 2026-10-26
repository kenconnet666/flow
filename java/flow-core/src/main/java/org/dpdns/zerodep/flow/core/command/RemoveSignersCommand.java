package org.dpdns.zerodep.flow.core.command;

import java.util.List;

/**
 * 减签（0006 的 3.3）：取消自己加的、还没办的人。前加签的人都取消了，待办回到自己手里。
 *
 * @param workItemId 自己的待办
 * @param users      减谁
 */
public record RemoveSignersCommand(Long workItemId, List<Long> users) implements FlowCommand {
    public RemoveSignersCommand { users = users == null ? List.of() : List.copyOf(users); }
}
