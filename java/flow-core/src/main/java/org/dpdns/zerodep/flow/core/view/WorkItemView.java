package org.dpdns.zerodep.flow.core.view;

import org.dpdns.zerodep.flow.model.runtime.ProcessInstance;
import org.dpdns.zerodep.flow.model.runtime.WorkItem;

import java.util.List;

/**
 * 打开一个待办看到的：待办与实例、这个节点上的表单、能选的处理结果，以及我现在能做什么。
 *
 * @param workItem  待办
 * @param instance  实例
 * @param form      这个节点上的表单；流程没有表单时为 null
 * @param outcomes  能选的处理结果，按定义里的先后
 * @param claimable 我能认领（在收件箱里、我是候选）
 * @param completable 我能处理（落到我的；或在收件箱里、我是候选——处理时顺带认领）
 */
public record WorkItemView(WorkItem workItem, ProcessInstance instance, FormView form, List<OutcomeView> outcomes,
                           boolean claimable, boolean completable) {
    /**
     * 一个处理结果（0005 的人工节点 {@code outcomes}）：审批面板上的一个按钮。
     *
     * @param key     结果的键
     * @param label   按钮上的字
     * @param kind    {@code approve}、{@code reject}、{@code return}、{@code escalate}、{@code terminate}
     * @param comment 意见：{@code required} 必填、{@code optional} 可写、{@code none} 不收
     * @param confirm 点了先确认
     */
    public record OutcomeView(String key, String label, String kind, String comment, boolean confirm) { }
}
