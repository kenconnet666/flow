package org.dpdns.zerodep.flow.core.engine;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import org.dpdns.zerodep.flow.core.db.FlowEasyQuery;
import org.dpdns.zerodep.flow.core.listener.AfterCommit;
import org.dpdns.zerodep.flow.core.listener.FlowListener;
import org.dpdns.zerodep.flow.core.listener.FlowNotice;
import org.dpdns.zerodep.flow.core.notify.Notification;
import org.dpdns.zerodep.flow.core.notify.NotificationChannel;
import org.dpdns.zerodep.flow.core.view.instance.FlowEventVO;
import org.dpdns.zerodep.flow.core.view.instance.InstanceVO;
import org.dpdns.zerodep.flow.model.definition.ProcessGraph;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 把写下的流转记录交给监听（{@link FlowListener}，0006 第 5 节）、把算好的通知交给渠道（{@link NotificationChannel}，0005 第 8 节），
 * 并且管着引擎的事务边界：
 * <ul>
 *   <li>写记录时（{@link #published}）：事务内的监听马上调，抛错整个命令回滚；提交后的监听先记在这个线程上；</li>
 *   <li>引擎最外层的事务做完时（{@link #transaction}）：记下的监听与通知交给 {@link AfterCommit} 去调；事务回滚了就丢掉。</li>
 * </ul>
 * 作业里调处理类会嵌套事务：只在最外层做完时发。
 */
public class EventDispatcher {
    private static final System.Logger LOG = System.getLogger(EventDispatcher.class.getName());

    private final List<FlowListener> inTransaction;
    private final List<FlowListener> afterCommit;
    private final List<NotificationChannel> channels;
    private final AfterCommit scheduler;
    /** 这个线程上正在做的引擎事务：嵌套几层，提交后要发的通知 */
    private final ThreadLocal<Pending> pending = ThreadLocal.withInitial(Pending::new);

    private static class Pending {
        int depth;
        final List<FlowNotice> notices = new ArrayList<>();
        final List<Notification> notifications = new ArrayList<>();

        void clear() {
            notices.clear();
            notifications.clear();
        }
    }

    /**
     * @param listeners 全部监听，按注册的先后调
     * @param channels  通知的渠道
     * @param scheduler 提交后的监听、通知怎么排
     */
    public EventDispatcher(List<FlowListener> listeners, List<NotificationChannel> channels, AfterCommit scheduler) {
        this.inTransaction = listeners.stream().filter(l -> l.phase() == FlowListener.Phase.IN_TRANSACTION).toList();
        this.afterCommit = listeners.stream().filter(l -> l.phase() == FlowListener.Phase.AFTER_COMMIT).toList();
        this.channels = List.copyOf(channels);
        this.scheduler = scheduler;
    }

    /** 有没有通知的渠道：没有时不用算通知 */
    boolean notifies() { return !channels.isEmpty(); }

    /** 在引擎的事务里做；最外层做完（提交了）发提交后的通知，出错时丢掉 */
    <T> T transaction(EasyEntityQuery db, Supplier<T> work) {
        Pending current = pending.get();
        current.depth++;
        try {
            T result = FlowEasyQuery.transaction(db, work);
            if (current.depth == 1) deliverLater(current);
            return result;
        } catch (RuntimeException e) {
            if (current.depth == 1) current.clear();
            throw e;
        } finally {
            if (--current.depth == 0) pending.remove();
        }
    }

    /** 写下了这些流转记录（带编号）与由它们算出的通知：事务内的监听马上调，提交后的监听与通知记下 */
    void published(List<FlowEventVO> events, InstanceVO instance, ProcessGraph graph, List<Notification> notifications) {
        pending.get().notifications.addAll(notifications);
        if (inTransaction.isEmpty() && afterCommit.isEmpty()) return;
        for (FlowEventVO event : events) {
            FlowNotice notice = new FlowNotice(event, instance, metaOf(graph, event.getNode()));
            for (FlowListener listener : inTransaction) {
                if (accepts(listener, notice)) listener.on(notice);
            }
            if (afterCommit.stream().anyMatch(l -> accepts(l, notice))) pending.get().notices.add(notice);
        }
    }

    private void deliverLater(Pending current) {
        if (current.notices.isEmpty() && current.notifications.isEmpty()) return;
        List<FlowNotice> ready = List.copyOf(current.notices);
        List<Notification> toSend = List.copyOf(current.notifications);
        current.clear();
        scheduler.run(() -> {
            deliver(ready);
            send(toSend);
        });
    }

    /** 通知交给每个渠道：一个出错不影响别的，只记日志 */
    private void send(List<Notification> notifications) {
        for (Notification notification : notifications) {
            for (NotificationChannel channel : channels) {
                try {
                    channel.send(notification);
                } catch (RuntimeException e) {
                    LOG.log(System.Logger.Level.ERROR, "notification channel " + channel.getClass().getName() + " failed on " + notification.getKind(), e);
                }
            }
        }
    }

    /** 提交后的监听：一个出错不影响别的，只记日志 */
    private void deliver(List<FlowNotice> notices) {
        for (FlowNotice notice : notices) {
            for (FlowListener listener : afterCommit) {
                if (!accepts(listener, notice)) continue;
                try {
                    listener.on(notice);
                } catch (RuntimeException e) {
                    LOG.log(System.Logger.Level.ERROR, "flow listener " + listener.getClass().getName() + " failed on " + notice.getEvent().getType(), e);
                }
            }
        }
    }

    private static boolean accepts(FlowListener listener, FlowNotice notice) {
        return (listener.types().isEmpty() || listener.types().contains(notice.getEvent().getType()))
            && (listener.processes().isEmpty() || listener.processes().contains(notice.getInstance().getProcessKey()));
    }

    private static Map<String, Object> metaOf(ProcessGraph graph, String nodeId) {
        if (nodeId == null) return Map.of();
        ProcessNode node = graph.node(nodeId);
        return node == null ? Map.of() : node.getInfo().getMeta();
    }
}
