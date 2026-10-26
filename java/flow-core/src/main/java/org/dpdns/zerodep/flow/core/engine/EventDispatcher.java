package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.listener.AfterCommit;
import org.dpdns.zerodep.flow.core.listener.FlowListener;
import org.dpdns.zerodep.flow.core.listener.FlowNotice;
import org.dpdns.zerodep.flow.model.definition.ProcessGraph;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.runtime.FlowEvent;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance;
import org.dpdns.zerodep.query.exec.Database;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 把写下的流转记录交给监听（{@link FlowListener}，0006 第 5 节），并且管着引擎的事务边界：
 * <ul>
 *   <li>写记录时（{@link #published}）：事务内的监听马上调，抛错整个命令回滚；提交后的监听先记在这个线程上；</li>
 *   <li>引擎最外层的事务做完时（{@link #transaction}）：记下的交给 {@link AfterCommit} 去调；事务回滚了就丢掉。</li>
 * </ul>
 * 作业里调处理类会嵌套事务：只在最外层做完时发。
 */
public final class EventDispatcher {
    private static final System.Logger LOG = System.getLogger(EventDispatcher.class.getName());

    private final List<FlowListener> inTransaction;
    private final List<FlowListener> afterCommit;
    private final AfterCommit scheduler;
    /** 这个线程上正在做的引擎事务：嵌套几层，提交后要发的通知 */
    private final ThreadLocal<Pending> pending = ThreadLocal.withInitial(Pending::new);

    private static final class Pending {
        int depth;
        final List<FlowNotice> notices = new ArrayList<>();
    }

    /**
     * @param listeners 全部监听，按注册的先后调
     * @param scheduler 提交后的监听怎么排
     */
    public EventDispatcher(List<FlowListener> listeners, AfterCommit scheduler) {
        this.inTransaction = listeners.stream().filter(l -> l.phase() == FlowListener.Phase.IN_TRANSACTION).toList();
        this.afterCommit = listeners.stream().filter(l -> l.phase() == FlowListener.Phase.AFTER_COMMIT).toList();
        this.scheduler = scheduler;
    }

    /** 在引擎的事务里做；最外层做完（提交了）发提交后的通知，出错时丢掉 */
    <T> T transaction(Database db, Supplier<T> work) {
        Pending current = pending.get();
        current.depth++;
        try {
            T result = db.transaction(work);
            if (current.depth == 1) deliverLater(current);
            return result;
        } catch (RuntimeException e) {
            if (current.depth == 1) current.notices.clear();
            throw e;
        } finally {
            if (--current.depth == 0) pending.remove();
        }
    }

    /** 写下了这些流转记录（带编号）：事务内的监听马上调，提交后的记下 */
    void published(List<FlowEvent> events, ProcessInstance instance, ProcessGraph graph) {
        if (inTransaction.isEmpty() && afterCommit.isEmpty()) return;
        for (FlowEvent event : events) {
            FlowNotice notice = new FlowNotice(event, instance, metaOf(graph, event.node()));
            for (FlowListener listener : inTransaction) {
                if (accepts(listener, notice)) listener.on(notice);
            }
            if (afterCommit.stream().anyMatch(l -> accepts(l, notice))) pending.get().notices.add(notice);
        }
    }

    private void deliverLater(Pending current) {
        if (current.notices.isEmpty()) return;
        List<FlowNotice> ready = List.copyOf(current.notices);
        current.notices.clear();
        scheduler.run(() -> deliver(ready));
    }

    /** 提交后的监听：一个出错不影响别的，只记日志 */
    private void deliver(List<FlowNotice> notices) {
        for (FlowNotice notice : notices) {
            for (FlowListener listener : afterCommit) {
                if (!accepts(listener, notice)) continue;
                try {
                    listener.on(notice);
                } catch (RuntimeException e) {
                    LOG.log(System.Logger.Level.ERROR, "flow listener " + listener.getClass().getName() + " failed on " + notice.event().type(), e);
                }
            }
        }
    }

    private static boolean accepts(FlowListener listener, FlowNotice notice) {
        return (listener.types().isEmpty() || listener.types().contains(notice.event().type()))
            && (listener.processes().isEmpty() || listener.processes().contains(notice.instance().processKey()));
    }

    private static Map<String, Object> metaOf(ProcessGraph graph, String nodeId) {
        if (nodeId == null) return Map.of();
        ProcessNode node = graph.node(nodeId);
        return node == null ? Map.of() : node.info().meta();
    }
}
