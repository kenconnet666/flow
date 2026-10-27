package org.dpdns.zerodep.flow.core.view;

import lombok.Data;

import java.util.List;

/** 实例详情（0006 第 4 节的 {@code instance} 与 {@code timeline}）：发起人看进度、历史里打开都是它。 */
@Data
public class InstanceDetail {
    /** 实例 */
    private final InstanceVO instance;
    /** 表单：都只读，按流程级的 {@code formView} 去掉隐藏的字段（0004 的 4.3）；流程没有表单时为 null */
    private final FormView form;
    /** 全部待办，按到达的先后 */
    private final List<WorkItemVO> workItems;
    /** 流转记录，按先后 */
    private final List<FlowEventVO> timeline;
    /** 我现在能对实例做的事 */
    private final Actions actions;
    /** 流程的节点（开始节点、网关除外）：管理员跳转时选目标 */
    private final List<NodeRef> nodes;
    /** 流程图：节点走到哪了、连线走过没有 */
    private final ProcessDiagram diagram;

    /** 一个节点。 */
    @Data
    public static class NodeRef {
        /** 节点标识 */
        private final String node;
        /** 名称 */
        private final String name;
        /** 类型 */
        private final String type;
    }

    /** 我现在能对实例做的事（留言随时能留，不在这里）。 */
    @Data
    public static class Actions {
        /** 撤回：我是发起人、实例进行中、定义的 {@code withdraw} 规则还允许 */
        private final boolean withdraw;
        /** 催办：我是发起人、实例进行中、有打开着的待办 */
        private final boolean remind;
    }
}
