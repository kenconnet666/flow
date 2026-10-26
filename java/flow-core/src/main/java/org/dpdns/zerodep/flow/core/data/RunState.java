package org.dpdns.zerodep.flow.core.data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实例的运行状态（0007 的 1 节第 5 条，存在实例的 {@code state} 列）：引擎内部用，不给前端。不可变，改了返回新的。
 * 现在只有各人工节点到过几轮（{@code sys.round.节点}）；并行的令牌、汇合计数、依次会签的名单随并行网关与会签补上。
 *
 * @param rounds 节点 → 第几轮
 */
public record RunState(Map<String, Integer> rounds) {
    /** 刚发起 */
    public static final RunState INITIAL = new RunState(Map.of());

    /** 复制并冻结 */
    public RunState {
        rounds = rounds == null ? Map.of() : Map.copyOf(rounds);
    }

    /** 节点到过几轮；没到过是 0 */
    public int round(String node) { return rounds.getOrDefault(node, 0); }

    /** 再到一次这个节点 */
    public RunState enter(String node) {
        Map<String, Integer> next = new LinkedHashMap<>(rounds);
        next.merge(node, 1, Integer::sum);
        return new RunState(next);
    }
}
