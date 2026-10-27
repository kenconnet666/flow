package org.dpdns.zerodep.flow.example;

import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.listener.FlowListener;
import org.dpdns.zerodep.flow.core.listener.FlowNotice;
import org.dpdns.zerodep.flow.core.view.FlowEventVO;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 示例的"通知"：监听流转记录，该通知谁就打一行日志。真实应用在这里发站内信、邮件、企业微信……
 * 提交后才调（默认的 {@link Phase#AFTER_COMMIT}），发通知失败不影响流转。
 */
@Component
public class ExampleNotifications implements FlowListener {
    private static final System.Logger LOG = System.getLogger(ExampleNotifications.class.getName());

    @Override
    public Set<String> types() {
        return Set.of(EventEntity.WORK_ITEM_CREATED, EventEntity.WORK_ITEM_REMINDED, EventEntity.WORK_ITEM_OVERDUE, EventEntity.INSTANCE_CC,
            EventEntity.INSTANCE_FINISHED, EventEntity.JOB_FAILED);
    }

    @Override
    public void on(FlowNotice notice) {
        FlowEventVO event = notice.event();
        String title = notice.instance().getTitle();
        String text = switch (event.getType()) {
            case EventEntity.WORK_ITEM_CREATED -> "新待办「" + title + "」" + event.getNodeName() + "，处理人 " + dataText(event, "assigneeName", "收件箱");
            case EventEntity.WORK_ITEM_REMINDED -> "催办「" + title + "」" + event.getNodeName();
            case EventEntity.WORK_ITEM_OVERDUE -> "超时「" + title + "」" + event.getNodeName();
            case EventEntity.JOB_FAILED -> "异常「" + title + "」" + event.getNodeName() + "：" + dataText(event, "error", "") + "，通知管理员处理";
            case EventEntity.INSTANCE_CC -> "抄送「" + title + "」给 " + dataText(event, "userNames", "");
            default -> "「" + title + "」已结束，结果 " + event.getOutcome() + "，通知发起人 " + notice.instance().getStartedByName();
        };
        LOG.log(System.Logger.Level.INFO, "[通知] " + text);
    }

    private static String dataText(FlowEventVO event, String key, String fallback) {
        Object value = event.getData() == null ? null : event.getData().get(key);
        return value == null ? fallback : String.valueOf(value);
    }
}
