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
 * @param actions   处理之外我现在能做的协作（审批页上的"转办""委派"……按钮）
 */
public record WorkItemView(WorkItem workItem, ProcessInstance instance, FormView form, List<OutcomeView> outcomes,
                           boolean claimable, boolean completable, Actions actions) {
    /**
     * 处理之外我现在能做的协作：能处理、节点上的 {@code allow} 没关掉时才能做（0005 人工节点的 allow、0006 的 3.1）。
     *
     * @param transfer   转办
     * @param delegate   委派
     * @param addSigners 加签
     * @param release    退回收件箱（我从收件箱里认领的）
     * @param assign     指定承办人（没人认领、在等我这个收件箱负责人指定）
     * @param withdrawStep 撤回刚处理的一步（我处理的、下一步还没人动）
     */
    public record Actions(boolean transfer, boolean delegate, boolean addSigners, boolean release, boolean assign, boolean withdrawStep) { }

    /**
     * 一个处理结果（0005 的人工节点 {@code outcomes}）：审批面板上的一个按钮。
     *
     * @param key     结果的键
     * @param label   按钮上的字
     * @param kind    {@code approve}、{@code reject}、{@code return}、{@code escalate}、{@code terminate}
     * @param comment 意见：{@code required} 必填、{@code optional} 可写、{@code none} 不收
     * @param confirm 点了先确认
     * @param picks   选了这个结果后要选处理人的下一步（0005 的 5.6）；我不能处理时是空的
     */
    public record OutcomeView(String key, String label, String kind, String comment, boolean confirm, List<PickView> picks) { }
}
