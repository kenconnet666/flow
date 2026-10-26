package org.dpdns.zerodep.flow.example;

import org.dpdns.zerodep.flow.core.listener.FlowListener;
import org.dpdns.zerodep.flow.core.listener.FlowNotice;
import org.dpdns.zerodep.flow.model.runtime.FlowEvent;
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
        return Set.of(FlowEvent.WORK_ITEM_CREATED, FlowEvent.WORK_ITEM_REMINDED, FlowEvent.WORK_ITEM_OVERDUE, FlowEvent.INSTANCE_CC,
            FlowEvent.INSTANCE_FINISHED);
    }

    @Override
    public void on(FlowNotice notice) {
        FlowEvent event = notice.event();
        String title = notice.instance().title();
        String text = switch (event.type()) {
            case FlowEvent.WORK_ITEM_CREATED -> "新待办「" + title + "」" + event.nodeName() + "，处理人 " + dataText(event, "assigneeName", "收件箱");
            case FlowEvent.WORK_ITEM_REMINDED -> "催办「" + title + "」" + event.nodeName();
            case FlowEvent.WORK_ITEM_OVERDUE -> "超时「" + title + "」" + event.nodeName();
            case FlowEvent.INSTANCE_CC -> "抄送「" + title + "」给 " + dataText(event, "userNames", "");
            default -> "「" + title + "」已结束，结果 " + event.outcome() + "，通知发起人 " + notice.instance().startedByName();
        };
        LOG.log(System.Logger.Level.INFO, "[通知] " + text);
    }

    private static String dataText(FlowEvent event, String key, String fallback) {
        Object value = event.data() == null ? null : event.data().get(key);
        return value == null ? fallback : String.valueOf(value);
    }
}
