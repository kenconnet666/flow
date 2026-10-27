package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.data.RunState;
import org.dpdns.zerodep.flow.model.definition.GatewayPairs;
import org.dpdns.zerodep.flow.model.definition.ProcessEdge;
import org.dpdns.zerodep.flow.model.definition.ProcessGraph;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 退回时能选的节点（处理结果 {@code { "kind": "return", "target": "choose" }}，0005 的 5.4）：
 * 当前节点的上游（沿前进的连线能走到当前节点）、这个实例里走到过的人工节点，按定义里的先后。
 * 不在当前节点之外的并行区域里——旁边的分支、已经汇合过的区域进去了汇合等不齐。
 */
class ReturnTargets {
    private ReturnTargets() { }

    /** 当前节点 current 能退回到的人工节点 */
    static List<ProcessNode.Task> of(LoadedDefinition definition, RunState state, String current) {
        ProcessGraph graph = definition.getGraph();
        GatewayPairs pairs = definition.getPairs();
        Set<String> upstream = upstreamOf(graph, current);
        List<String> around = pairs.forksAround(current);
        return graph.definition().getNodes().stream()
            .filter(node -> node instanceof ProcessNode.Task && !node.getId().equals(current))
            .filter(node -> upstream.contains(node.getId()) && state.round(node.getId()) > 0)
            .filter(node -> around.containsAll(pairs.forksAround(node.getId())))
            .map(node -> (ProcessNode.Task) node).toList();
    }

    /** 处理命令给的目标要是能选的一个：没给报 {@code returnTarget: required}，不能选报 {@code returnTarget: not-upstream} */
    static String check(LoadedDefinition definition, RunState state, String current, String target) {
        if (target == null || target.isBlank()) throw FlowErrors.invalid("returnTarget", "required");
        boolean allowed = of(definition, state, current).stream().anyMatch(node -> node.getId().equals(target));
        if (!allowed) throw FlowErrors.invalid("returnTarget", "not-upstream");
        return target;
    }

    /** 沿前进的连线（不算退回、提级）往回走能到的节点 */
    private static Set<String> upstreamOf(ProcessGraph graph, String node) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(List.of(node));
        while (!queue.isEmpty()) {
            for (ProcessEdge edge : graph.incoming(queue.poll())) {
                if (!edge.isBackward() && seen.add(edge.getFrom())) queue.add(edge.getFrom());
            }
        }
        return seen;
    }
}
