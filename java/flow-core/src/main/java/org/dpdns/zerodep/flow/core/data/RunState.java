package org.dpdns.zerodep.flow.core.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实例的运行状态（0007 的 {@code flow_instance.state}，引擎内部用，不给前端）。不可变：推进时换出新的一份。
 *
 * @param rounds  节点 → 到过几轮（{@code sys.round.节点}、待办的轮次）
 * @param waiting 并行、包容的汇合 → 还有几条分支没到（分叉时记下走了几条，每到一条减一，减到 0 汇合才往下走）
 * @param returns 被退回的节点 → 退回它的节点：退回它的节点写了 {@code returnTo: "resubmit"}，重新提交后直接回去（0005 的 5.4）
 * @param queues  依次会签的节点 → 还没轮到的人（0005 的 5.2 {@code sequential}）
 */
public record RunState(Map<String, Integer> rounds, Map<String, Integer> waiting, Map<String, String> returns,
                       Map<String, List<Long>> queues) {
    /** 刚发起 */
    public static final RunState INITIAL = new RunState(Map.of(), Map.of(), Map.of(), Map.of());

    /** 复制并冻结 */
    public RunState {
        rounds = rounds == null ? Map.of() : Map.copyOf(rounds);
        waiting = waiting == null ? Map.of() : Map.copyOf(waiting);
        returns = returns == null ? Map.of() : Map.copyOf(returns);
        queues = queues == null ? Map.of() : Map.copyOf(queues);
    }

    // ───────── 轮次 ─────────

    /** 节点到过几轮；没到过是 0 */
    public int round(String node) { return rounds.getOrDefault(node, 0); }

    /** 再到一次这个节点 */
    public RunState enter(String node) {
        Map<String, Integer> next = new LinkedHashMap<>(rounds);
        next.merge(node, 1, Integer::sum);
        return new RunState(next, waiting, returns, queues);
    }

    // ───────── 并行、包容的汇合 ─────────

    /** 分叉走了 branches 条：汇合 join 要等这么多条 */
    public RunState fork(String join, int branches) {
        Map<String, Integer> next = new LinkedHashMap<>(waiting);
        next.put(join, branches);
        return new RunState(rounds, next, returns, queues);
    }

    /** 汇合 join 还要等几条；没有在等时是 0 */
    public int waitingAt(String join) { return waiting.getOrDefault(join, 0); }

    /** 一条分支到了汇合 join：少等一条，等齐了就不再记它 */
    public RunState arrive(String join) {
        Map<String, Integer> next = new LinkedHashMap<>(waiting);
        int remaining = next.getOrDefault(join, 1) - 1;
        if (remaining > 0) next.put(join, remaining);
        else next.remove(join);
        return new RunState(rounds, next, returns, queues);
    }

    /** 不再等汇合 join（区域里的分支都取消了：退回跳出了区域） */
    public RunState forget(String join) {
        if (!waiting.containsKey(join)) return this;
        Map<String, Integer> next = new LinkedHashMap<>(waiting);
        next.remove(join);
        return new RunState(rounds, next, returns, queues);
    }

    // ───────── 退回后直接回去 ─────────

    /** target 被 from 退回了，重新提交后直接回到 from */
    public RunState returned(String target, String from) {
        Map<String, String> next = new LinkedHashMap<>(returns);
        next.put(target, from);
        return new RunState(rounds, waiting, next, queues);
    }

    /** node 重新提交后要直接回去的节点；没有时为 null */
    public String resubmitTo(String node) { return returns.get(node); }

    /** node 重新提交了，不再记它 */
    public RunState resubmitted(String node) {
        if (!returns.containsKey(node)) return this;
        Map<String, String> next = new LinkedHashMap<>(returns);
        next.remove(node);
        return new RunState(rounds, waiting, next, queues);
    }

    // ───────── 跳转 ─────────

    /** 管理员跳转：不再等汇合、不再记退回后直接回去与依次会签的队列（原来的待办都取消了）；轮次照旧 */
    public RunState withoutPending() { return new RunState(rounds, Map.of(), Map.of(), Map.of()); }

    // ───────── 依次会签 ─────────

    /** 依次会签的节点还没轮到的人；没有时是空列表 */
    public List<Long> queue(String node) { return queues.getOrDefault(node, List.of()); }

    /** 换掉依次会签的节点还没轮到的人；空列表就是不再记它 */
    public RunState queue(String node, List<Long> remaining) {
        Map<String, List<Long>> next = new LinkedHashMap<>(queues);
        if (remaining.isEmpty()) next.remove(node);
        else next.put(node, List.copyOf(remaining));
        return new RunState(rounds, waiting, returns, next);
    }
}
