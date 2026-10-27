package org.dpdns.zerodep.flow.core.view.workitem;

import lombok.Data;
import org.dpdns.zerodep.flow.core.view.form.FormView;
import org.dpdns.zerodep.flow.core.view.instance.InstanceVO;

import java.util.List;

/** 打开一个待办看到的：待办与实例、这个节点上的表单、能选的处理结果，以及我现在能做什么。 */
@Data
public class WorkItemView {
    /** 待办 */
    private final WorkItemVO workItem;
    /** 实例 */
    private final InstanceVO instance;
    /** 这个节点上的表单；流程没有表单时为 null */
    private final FormView form;
    /** 能选的处理结果，按定义里的先后 */
    private final List<OutcomeView> outcomes;
    /** 我能认领（在收件箱里、我是候选） */
    private final boolean claimable;
    /** 我能处理（落到我的；或在收件箱里、我是候选——处理时顺带认领） */
    private final boolean completable;
    /** 处理之外我现在能做的协作（审批页上的"转办""委派"……按钮） */
    private final Actions actions;
    /** 我暂存过的（表单里已经显示暂存的值）；没有、不是我的待办时为 null */
    private final DraftVO draft;

    /** 处理之外我现在能做的协作：能处理、节点上的 {@code allow} 没关掉时才能做（0005 人工节点的 allow、0006 的 3.1）。 */
    @Data
    public static class Actions {
        /** 转办 */
        private final boolean transfer;
        /** 委派 */
        private final boolean delegate;
        /** 加签 */
        private final boolean addSigners;
        /** 退回收件箱（我从收件箱里认领的） */
        private final boolean release;
        /** 指定承办人（没人认领、在等我这个收件箱负责人指定） */
        private final boolean assign;
        /** 撤回刚处理的一步（我处理的、下一步还没人动） */
        private final boolean withdrawStep;
        /** 申请改派（负责人指定给我的、还没在等他重新指定） */
        private boolean requestReassign;
    }

    /** 一个处理结果（0005 的人工节点 {@code outcomes}）：审批面板上的一个按钮。 */
    @Data
    public static class OutcomeView {
        /** 结果的键 */
        private final String key;
        /** 按钮上的字 */
        private final String label;
        /** {@code approve}、{@code reject}、{@code return}、{@code escalate}、{@code terminate} */
        private final String kind;
        /** 意见：{@code required} 必填、{@code optional} 可写、{@code none} 不收 */
        private final String comment;
        /** 点了先确认 */
        private final boolean confirm;
        /** 选了这个结果后要选处理人的下一步（0005 的 5.6）；我不能处理时是空的 */
        private final List<PickView> picks;
        /** 退回时能选的节点（结果写了 {@code target: "choose"}）；别的结果、我不能处理时是空的 */
        private final List<ReturnTarget> targets;
    }

    /** 退回时能选的一个节点。 */
    @Data
    public static class ReturnTarget {
        /** 节点 */
        private final String node;
        /** 节点名称 */
        private final String nodeName;
        /** 上次在那里处理的人（退回去落到他）；没人处理过时为 null */
        private final String handlerName;
    }
}
