package org.dpdns.zerodep.flow.core.spi;

import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.view.instance.FlowEventVO;

import java.util.Set;

/**
 * 流转的监听（0006 第 5 节）：每条流转记录写下时都会给它看一眼。用来发通知（站内信、邮件、消息队列）、同步业务表、审计。
 * <ul>
 *   <li>{@link Phase#AFTER_COMMIT}（默认）：命令的事务提交之后调；抛错只记日志，不影响流转。发通知用它；</li>
 *   <li>{@link Phase#IN_TRANSACTION}：写流转记录的那一刻在同一个事务里调；抛错整个命令回滚（可以用来拒绝），可以同事务写业务表；</li>
 *   <li>{@link Phase#RELIABLE}：可靠投递（发件箱）。要投给它的记录和流转记录在同一个事务里记成作业，提交后马上投一次；
 *       抛错按作业的规则重试（1、4、9、16 分钟），重试用完进管理端的异常处理，进程挂了也不丢。投的是"至少一次"：
 *       同一条可能收到不止一次，按 {@code notice.getEvent().getId()} 去重。往消息队列、外部系统同步用它。</li>
 * </ul>
 * 按事件种类、流程标识过滤（{@link #types}、{@link #processes}），节点上的 {@code meta} 随通知给出。
 * <pre>{@code
 * FlowEngine.builder(dataSource).listener(notice -> {
 *     if (EventEntity.WORK_ITEM_REMINDED.equals(notice.event().type())) mail.send(...);
 * })
 * }</pre>
 */
@FunctionalInterface
public interface FlowListener {
    /** 什么时候调 */
    enum Phase {
        /** 和写流转记录在同一个事务里：抛错整个命令回滚 */
        IN_TRANSACTION,
        /** 事务提交之后：抛错只记日志 */
        AFTER_COMMIT,
        /** 发件箱：同事务记下、提交后投，失败重试，至少一次 */
        RELIABLE
    }

    /** 收到一条流转记录 */
    void on(FlowNotice notice);

    /** 什么时候调；默认提交之后 */
    default Phase phase() { return Phase.AFTER_COMMIT; }

    /** 只听这几种事件（{@code FlowEventVO} 的常量，如 {@code workItem.created}）；空的表示全部 */
    default Set<String> types() { return Set.of(); }

    /** 只听这几个流程（流程标识）；空的表示全部 */
    default Set<String> processes() { return Set.of(); }

    /**
     * 名字：可靠投递的作业里记的是它（重启后按它找回这个监听），所以要稳定、不重复；默认类名。
     * 匿名类的类名（{@code Xxx$1}）会随代码的改动变，可靠的监听写成匿名类时覆盖它。
     */
    default String listenerName() { return getClass().getName(); }
}
