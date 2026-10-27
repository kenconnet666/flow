package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

import java.util.Map;

/**
 * 实例迁移到流程的另一个版本（0002 §1"实例迁移到新版本"）：管理员给出节点的对应关系，校验后换版本、留痕。
 * 正在用的节点（开着的待办、在等的定时与事件、调用）没写对应关系时按同名的节点；新版本里没有、或类型不同时报错，不迁。
 */
@Data
public class MigrateCommand implements FlowCommand {
    private final Long instanceId;
    /** 迁到哪个版本 */
    private final int version;
    /** 旧节点 → 新节点；同名的可以不写 */
    private final Map<String, String> nodes;
    /** 原因（必填） */
    private final String reason;
}
