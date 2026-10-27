package org.dpdns.zerodep.flow.model.definition;

import lombok.Data;

import java.util.List;

/** 泳道：流程图上的一行（0005 的 3.1）。 */
@Data
public class Lane {
    /** 标识 */
    private final String id;
    /** 名称 */
    private final String name;
    /** 这一行里人工节点没写处理人时的默认去处；null 时没有 */
    private final Inbox inbox;

    /** 收件箱。 */
    @Data
    public static class Inbox {
        /** 角色、部门或扩展的种类（{@link AssigneeSource#isInbox()}） */
        private final AssigneeSource source;
        /** 负责人；空时调接入方的 {@code inboxManager} */
        private final List<AssigneeSource> manager;

        public Inbox(AssigneeSource source, List<AssigneeSource> manager) { manager = manager == null ? List.of() : List.copyOf(manager);
            this.source = source;
            this.manager = manager;
        }
    }
}
