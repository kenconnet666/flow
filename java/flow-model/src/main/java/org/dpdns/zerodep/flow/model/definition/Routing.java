package org.dpdns.zerodep.flow.model.definition;

import org.dpdns.zerodep.flow.model.Conditions;
import org.dpdns.zerodep.flow.model.Values;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 选出边与连线的副作用（docs/decisions/0005 第 6 节），与 TS 模型包的 {@code routing.ts} 同一套规则，两边跑 spec/routing 的样例。
 * <pre>{@code
 * List<ProcessEdge> next = Routing.selectEdges(graph, "review", "pass", scope::read);
 * Map<String, Object> variables = Routing.applyEffects(edge.effects(), variables, scope::read);
 * }</pre>
 */
public class Routing {
    private static final BigDecimal LONG_MIN = BigDecimal.valueOf(Long.MIN_VALUE);
    private static final BigDecimal LONG_MAX = BigDecimal.valueOf(Long.MAX_VALUE);

    private Routing() { }

    /**
     * 从一个节点走哪几条线。
     * <ul>
     *   <li>并行网关：全部出边；</li>
     *   <li>包容网关：条件成立的都走，都不成立时走默认边；</li>
     *   <li>其余：条件成立的边里，先取写明了这个结果的，再取没写结果的，都没有时走默认边——最多一条。</li>
     * </ul>
     * 一条也选不出时返回空列表。
     *
     * @param outcome 节点给出的结果；网关传 null
     * @param read    条件里按字段名取值
     */
    public static List<ProcessEdge> selectEdges(ProcessGraph graph, String nodeId, String outcome, Function<String, Object> read) {
        List<ProcessEdge> edges = graph.outgoing(nodeId);
        ProcessNode node = graph.node(nodeId);
        String type = node == null ? null : node.getType();
        if ("parallel".equals(type)) return List.copyOf(edges);
        List<ProcessEdge> candidates = new ArrayList<>();
        List<ProcessEdge> fallback = new ArrayList<>();
        for (ProcessEdge edge : edges) {
            boolean fits = edge.getOutcome() == null || edge.getOutcome().equals(outcome);
            if (!fits) continue;
            if (edge.isDefault()) fallback.add(edge);
            else if (edge.getWhen() == null || Conditions.evaluate(edge.getWhen(), read)) candidates.add(edge);
        }
        if ("inclusive".equals(type)) return !candidates.isEmpty() ? candidates : fallback.stream().limit(1).toList();
        ProcessEdge chosen = candidates.stream().filter(e -> e.getOutcome() != null && e.getOutcome().equals(outcome)).findFirst()
            .orElse(candidates.isEmpty() ? (fallback.isEmpty() ? null : fallback.get(0)) : candidates.get(0));
        return chosen == null ? List.of() : List.of(chosen);
    }

    /**
     * 按顺序应用连线的副作用，返回新的流程变量（不改原来的）。{@code set} 的值是引用时用 read 取；{@code increment} 把没有、
     * 或不是数字的当作 0，按精确的小数加，结果是放得进 long 的整数时是 Long，否则是 BigDecimal。
     */
    public static Map<String, Object> applyEffects(List<ProcessEdge.Effect> effects, Map<String, Object> variables, Function<String, Object> read) {
        Map<String, Object> next = new LinkedHashMap<>(variables);
        for (ProcessEdge.Effect effect : effects) {
            if (effect instanceof ProcessEdge.Effect.Set set) {
                next.put(set.getTarget(), set.getValue() instanceof Ref ref ? read.apply(ref.getRef()) : set.getValue());
            } else if (effect instanceof ProcessEdge.Effect.Increment increment) {
                next.put(increment.getTarget(), add(next.get(increment.getTarget()), increment.getBy()));
            }
        }
        return next;
    }

    private static Object add(Object current, BigDecimal by) {
        BigDecimal base = Objects.requireNonNullElse(Values.decimalOf(current), BigDecimal.ZERO);
        BigDecimal sum = base.add(by).stripTrailingZeros();
        if (sum.scale() <= 0 && sum.compareTo(LONG_MIN) >= 0 && sum.compareTo(LONG_MAX) <= 0) return sum.longValueExact();
        return sum.scale() < 0 ? new BigDecimal(sum.toBigInteger()) : sum;
    }
}
