package org.dpdns.zerodep.flow.core.notify;

/**
 * 通知的渠道（0005 第 8 节）：站内信、邮件、企业微信、钉钉由接入方实现，可以注册几个，每条通知每个渠道都收到。
 * 事务提交之后才调（命令回滚了就不发）；抛错只记日志，不影响别的渠道。
 * <p>
 * {@link #reliable()} 为 true 时走发件箱：要发的通知和流转记录在同一个事务里记成作业，提交后马上发一次，抛错按作业的规则重试，
 * 重试用完进管理端的异常处理，进程挂了也不丢；至少一次，同一条可能发不止一次（按 {@code notification.getEvent().getId()} 与种类去重）。
 * 重发时实例与待办是那时从库里读的样子。
 *
 * <pre>{@code
 * FlowEngine.builder(db).channel(notification -> inbox.save(notification.recipients(), render(notification)));
 * }</pre>
 */
@FunctionalInterface
public interface NotificationChannel {
    /** 发一条通知 */
    void send(Notification notification);

    /** 走发件箱（可靠投递）；默认不走 */
    default boolean reliable() { return false; }

    /** 名字：可靠投递的作业里记的是它，要稳定、不重复；默认类名，可靠的渠道写成匿名类时覆盖它（匿名类的类名会随代码变） */
    default String channelName() { return getClass().getName(); }
}
