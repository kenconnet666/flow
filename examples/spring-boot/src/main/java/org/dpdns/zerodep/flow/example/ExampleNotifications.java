package org.dpdns.zerodep.flow.example;

import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.listener.FlowListener;
import org.dpdns.zerodep.flow.core.listener.FlowNotice;
import org.dpdns.zerodep.flow.core.notify.Notification;
import org.dpdns.zerodep.flow.core.notify.NotificationChannel;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 示例的通知渠道：引擎算好发给谁、哪种、用哪个模板，这里打一行日志代替。真实应用在这里发站内信、邮件、企业微信……
 * 提交后才调，发失败不影响流转。自动处理失败（{@code job.failed}）不在标准的通知里，示例另外听这一种，通知管理员。
 */
@Component
public class ExampleNotifications implements NotificationChannel, FlowListener {
    private static final System.Logger LOG = System.getLogger(ExampleNotifications.class.getName());

    @Override
    public void send(Notification notification) {
        String title = notification.getInstance().getTitle();
        String node = notification.getEvent().getNodeName();
        String text = switch (notification.getKind()) {
            case Notification.ARRIVED -> "新待办「" + title + "」" + node;
            case Notification.RETURNED -> "被退回「" + title + "」" + node;
            case Notification.REMINDED -> "催办「" + title + "」" + node;
            case Notification.DUE_SOON -> "即将到期「" + title + "」" + node;
            case Notification.OVERDUE -> "已超时「" + title + "」" + node;
            case Notification.CC -> "抄送「" + title + "」";
            case Notification.MENTIONED -> notification.getEvent().getActorName() + " 在「" + title + "」里提到了你：" + notification.getEvent().getComment();
            case Notification.FINISHED -> "「" + title + "」已结束，结果 " + notification.getEvent().getOutcome();
            default -> notification.getKind() + "「" + title + "」";
        };
        String template = notification.getTemplate() == null ? "" : "（模板 " + notification.getTemplate() + "）";
        LOG.log(System.Logger.Level.INFO, "[通知] 发给 " + notification.getRecipients() + "：" + text + template);
    }

    @Override
    public Set<String> types() { return Set.of(EventEntity.JOB_FAILED); }

    @Override
    public void on(FlowNotice notice) {
        LOG.log(System.Logger.Level.INFO, "[通知] 异常「" + notice.getInstance().getTitle() + "」" + notice.getEvent().getNodeName() + "："
            + notice.getEvent().getData().get("error") + "，通知管理员处理");
    }
}
