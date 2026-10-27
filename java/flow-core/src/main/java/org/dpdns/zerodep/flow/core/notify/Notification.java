package org.dpdns.zerodep.flow.core.notify;

import org.dpdns.zerodep.flow.core.view.FlowEventVO;
import org.dpdns.zerodep.flow.core.view.InstanceVO;
import org.dpdns.zerodep.flow.core.view.WorkItemVO;

import java.util.List;

/**
 * 一条要发的通知（0005 第 8 节）：引擎只给出发给谁、哪种、用哪个模板与渲染用的数据，渠道（站内信、邮件、企业微信……）由接入方实现。
 *
 * @param kind       哪种：{@link #ARRIVED}、{@link #REMINDED}、{@link #DUE_SOON}、{@link #OVERDUE}、{@link #CC}、{@link #RETURNED}、{@link #FINISHED}
 * @param template   定义的 {@code notify} 里写的模板名；没写时为 null（渠道用这种通知的默认模板）
 * @param recipients 收件人（用户编号，不重复）：默认收件人加上定义里写的 {@code to}
 * @param event      引起这条通知的流转记录
 * @param instance   实例
 * @param workItem   相关的待办（到达、催办、到期、超时、被退回）；别的为 null
 */
public record Notification(String kind, String template, List<Long> recipients, FlowEventVO event, InstanceVO instance, WorkItemVO workItem) {
    /** 待办到达：处理人，或收件箱里的候选人 */
    public static final String ARRIVED = "arrived";
    /** 催办：当前处理人 */
    public static final String REMINDED = "reminded";
    /** 即将到期：当前处理人 */
    public static final String DUE_SOON = "dueSoon";
    /** 已超时：当前处理人；第一次超时另发收件箱的负责人 */
    public static final String OVERDUE = "overdue";
    /** 抄送：抄送的人 */
    public static final String CC = "cc";
    /** 被退回：退回的目标处理人 */
    public static final String RETURNED = "returned";
    /** 流程结束（正常结束或终止，带结束结果）：发起人 */
    public static final String FINISHED = "finished";

    public Notification {
        recipients = List.copyOf(recipients);
    }
}
