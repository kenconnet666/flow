package org.dpdns.zerodep.flow.core.view;

import lombok.Data;

import java.util.List;

/** 提交时要选处理人的下一步（0005 的 5.6 {@code pick}）：审批面板为它显示一个选人框。 */
@Data
public class PickView {
    /** 要选人的节点 */
    private final String node;
    /** 节点名称 */
    private final String nodeName;
    /** 最多选几个；null 时不限 */
    private final Integer max;
    /** 能选的人；null 时谁都可以选（人员选择器的数据由接入方给） */
    private final List<Candidate> candidates;

    /** 能选的一个人。 */
    @Data
    public static class Candidate {
        /** 用户编号 */
        private final Long id;
        /** 名字 */
        private final String name;
    }
}
