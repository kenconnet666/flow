package org.dpdns.zerodep.flow.model.definition;

import java.util.List;

/**
 * 泳道：流程图上的一行（0005 的 3.1）。
 *
 * @param id    标识
 * @param name  名称
 * @param inbox 这一行里人工节点没写处理人时的默认去处；null 时没有
 */
public record Lane(String id, String name, Inbox inbox) {
    /**
     * 收件箱。
     *
     * @param source  角色、部门或扩展的种类（{@link AssigneeSource#isInbox()}）
     * @param manager 负责人；空时调接入方的 {@code inboxManager}
     */
    public record Inbox(AssigneeSource source, List<AssigneeSource> manager) {
        public Inbox { manager = manager == null ? List.of() : List.copyOf(manager); }
    }
}
