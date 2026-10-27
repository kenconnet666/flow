package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

import java.util.List;

/** 减签（0006 的 3.3）：取消自己加的、还没办的人。前加签的人都取消了，待办回到自己手里。 */
@Data
public class RemoveSignersCommand implements FlowCommand {
    /** 自己的待办 */
    private final Long workItemId;
    /** 减谁 */
    private final List<Long> users;

    public RemoveSignersCommand(Long workItemId, List<Long> users) { users = users == null ? List.of() : List.copyOf(users);
        this.workItemId = workItemId;
        this.users = users;
    }
}
