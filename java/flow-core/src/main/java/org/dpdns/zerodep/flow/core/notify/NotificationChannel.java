package org.dpdns.zerodep.flow.core.notify;

/**
 * 通知的渠道（0005 第 8 节）：站内信、邮件、企业微信、钉钉由接入方实现，可以注册几个，每条通知每个渠道都收到。
 * 事务提交之后才调（命令回滚了就不发）；抛错只记日志，不影响别的渠道。
 *
 * <pre>{@code
 * FlowEngine.builder(db).channel(notification -> inbox.save(notification.recipients(), render(notification)));
 * }</pre>
 */
@FunctionalInterface
public interface NotificationChannel {
    /** 发一条通知 */
    void send(Notification notification);
}
