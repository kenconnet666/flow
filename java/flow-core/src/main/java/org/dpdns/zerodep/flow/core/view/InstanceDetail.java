package org.dpdns.zerodep.flow.core.view;

import java.util.List;

/**
 * 实例详情（0006 第 4 节的 {@code instance} 与 {@code timeline}）：发起人看进度、历史里打开都是它。
 *
 * @param instance  实例
 * @param form      表单：都只读，按流程级的 {@code formView} 去掉隐藏的字段（0004 的 4.3）；流程没有表单时为 null
 * @param workItems 全部待办，按到达的先后
 * @param timeline  流转记录，按先后
 * @param actions   我现在能对实例做的事
 * @param nodes     流程的节点（开始节点、网关除外）：管理员跳转时选目标
 * @param diagram   流程图：节点走到哪了、连线走过没有
 */
public record InstanceDetail(InstanceVO instance, FormView form, List<WorkItemVO> workItems, List<FlowEventVO> timeline,
                             Actions actions, List<NodeRef> nodes, ProcessDiagram diagram) {
    /**
     * 一个节点。
     *
     * @param node 节点标识
     * @param name 名称
     * @param type 类型
     */
    public record NodeRef(String node, String name, String type) { }

    /**
     * 我现在能对实例做的事（留言随时能留，不在这里）。
     *
     * @param withdraw 撤回：我是发起人、实例进行中、定义的 {@code withdraw} 规则还允许
     * @param remind   催办：我是发起人、实例进行中、有打开着的待办
     */
    public record Actions(boolean withdraw, boolean remind) { }
}
