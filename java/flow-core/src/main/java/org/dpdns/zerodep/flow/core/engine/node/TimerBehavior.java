package org.dpdns.zerodep.flow.core.engine.node;

import org.dpdns.zerodep.flow.core.engine.Execution;
import org.dpdns.zerodep.flow.core.engine.job.BuiltinJobs;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;

import java.time.Instant;
import java.util.Map;

/**
 * 定时节点（0005 第 4 节 {@code timer}）：到达时算出到什么时候、排一个作业，停在这里；到时候作业调 {@link #fired}。
 */
public class TimerBehavior {
    private TimerBehavior() { }

    /** 轮次加一，算出到什么时候（时长从现在起算，或到某个时刻）；时刻已经过了、引用的字段没有值时直接往下走 */
    public static void arrive(Execution run, ProcessNode.Timer timer) {
        run.updateState(s -> s.enter(timer.getId()));
        Instant until = run.getDeadlines().of(timer.getWaitFor(), run.getNow(), run::read);
        if (until == null || !until.isAfter(run.getNow())) {
            run.leave(timer.getId(), null);
            return;
        }
        run.updateState(s -> s.waitUntil(timer.getId(), until));
        Map<String, Object> payload = Map.of(BuiltinJobs.NODE, timer.getId(), BuiltinJobs.ROUND, run.getInstance().getState().round(timer.getId()));
        run.schedule(BuiltinJobs.TIMER, null, until, payload, BuiltinJobs.MAX_ATTEMPTS);
    }

    /** 到时候了：不再等，往下走 */
    public static void fired(Execution run, String nodeId) {
        run.updateState(s -> s.waitDone(nodeId));
        run.leave(nodeId, null);
    }
}
