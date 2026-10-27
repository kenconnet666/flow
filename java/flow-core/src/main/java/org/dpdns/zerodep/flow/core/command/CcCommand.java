package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

import java.util.List;

/**
 * 抄送（知会）：处理人或看得到实例的人把实例抄送给几个人，他们在"抄送我的"里看到（0006 第 4 节的 {@code cc}）。
 * 抄送节点（0005 的 {@code cc}）由引擎自己抄送，不走这个命令。
 */
@Data
public class CcCommand implements FlowCommand {
    /** 实例 */
    private final Long instanceId;
    /** 抄送给谁 */
    private final List<Long> users;
    /** 附言，记进流转记录 */
    private final String message;

    public CcCommand(Long instanceId, List<Long> users, String message) { users = users == null ? List.of() : List.copyOf(users);
        this.instanceId = instanceId;
        this.users = users;
        this.message = message;
    }
}
