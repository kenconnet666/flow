package org.dpdns.zerodep.flow.core.command;

import java.util.List;

/**
 * 抄送（知会）：处理人或看得到实例的人把实例抄送给几个人，他们在"抄送我的"里看到（0006 第 4 节的 {@code cc}）。
 * 抄送节点（0005 的 {@code cc}）由引擎自己抄送，不走这个命令。
 *
 * @param instanceId 实例
 * @param users      抄送给谁
 * @param message    附言，记进流转记录
 */
public record CcCommand(Long instanceId, List<Long> users, String message) implements FlowCommand {
    public CcCommand { users = users == null ? List.of() : List.copyOf(users); }
}
