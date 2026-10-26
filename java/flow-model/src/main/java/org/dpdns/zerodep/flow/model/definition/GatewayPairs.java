package org.dpdns.zerodep.flow.model.definition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 并行、包容网关的分叉与汇合配对（0005 的 4.4，结构化）：每个分叉有一个汇合，中间的分支不跳出去。
 * <p>按前进图（去掉退回、提级连线）算：汇合是分叉的直接后支配节点，分叉是汇合的直接支配节点，两者同类型。
 * 校验用它报 {@code gateway-unpaired}，引擎用它在分叉时记下"汇合要等几条分支"、在退回跳出区域时找出要取消的分支。
 * 与 TS 的 {@code gatewayPairs} 同一套算法。
 */
public final class GatewayPairs {
    /** 结束节点都连到这个虚拟的出口，后支配树才有唯一的根 */
    private static final String EXIT = "\u0000exit";

    private final Map<String, String> joinOf;
    private final Map<String, String> forkOf;
    private final Set<String> unpaired;
    private final Map<String, Set<String>> regions;

    private GatewayPairs(Map<String, String> joinOf, Map<String, String> forkOf, Set<String> unpaired, Map<String, Set<String>> regions) {
        this.joinOf = joinOf;
        this.forkOf = forkOf;
        this.unpaired = unpaired;
        this.regions = regions;
    }

    /** 分叉配对的汇合；不是分叉、或没配上对时为 null */
    public String joinOf(String fork) { return joinOf.get(fork); }

    /** 汇合配对的分叉；不是汇合、或没配上对时为 null */
    public String forkOf(String join) { return forkOf.get(join); }

    /** 没配上对的并行、包容网关（按定义里的先后） */
    public Set<String> unpaired() { return unpaired; }

    /** 分叉与汇合之间的节点（不含两头）：退回跳出这个区域时，这些节点上还开着的待办一起取消 */
    public Set<String> region(String fork) { return regions.getOrDefault(fork, Set.of()); }

    /** 分叉出去的节点在哪些区域里，由里到外（嵌套的并行） */
    public List<String> forksAround(String node) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : regions.entrySet()) if (e.getValue().contains(node)) out.add(e.getKey());
        out.sort((a, b) -> Integer.compare(regions.get(a).size(), regions.get(b).size()));
        return out;
    }

    /** 算出一份定义的配对 */
    public static GatewayPairs of(ProcessDefinition definition) {
        ProcessGraph graph = ProcessGraph.of(definition);
        Map<String, List<String>> succ = new HashMap<>();
        Map<String, List<String>> pred = new HashMap<>();
        for (ProcessEdge e : definition.edges()) {
            if (e.isBackward() || graph.node(e.from()) == null || graph.node(e.to()) == null) continue;
            succ.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e.to());
            pred.computeIfAbsent(e.to(), k -> new ArrayList<>()).add(e.from());
        }
        for (ProcessNode node : definition.nodes()) {
            if (!node.type().equals("end")) continue;
            succ.computeIfAbsent(node.id(), k -> new ArrayList<>()).add(EXIT);
            pred.computeIfAbsent(EXIT, k -> new ArrayList<>()).add(node.id());
        }
        Map<String, String> joinOf = new LinkedHashMap<>();
        Map<String, String> forkOf = new LinkedHashMap<>();
        Set<String> unpaired = new LinkedHashSet<>();
        Map<String, Set<String>> regions = new LinkedHashMap<>();
        if (graph.start() == null) return new GatewayPairs(joinOf, forkOf, unpaired, regions);

        Function<String, List<String>> successors = id -> succ.getOrDefault(id, List.of());
        Function<String, List<String>> predecessors = id -> pred.getOrDefault(id, List.of());
        Map<String, String> idom = immediateDominators(graph.start().id(), successors, predecessors);
        Map<String, String> ipdom = immediateDominators(EXIT, predecessors, successors);
        for (ProcessNode node : definition.nodes()) {
            if (!node.type().equals("parallel") && !node.type().equals("inclusive")) continue;
            if (!idom.containsKey(node.id()) || !ipdom.containsKey(node.id())) continue;
            boolean isFork = successors.apply(node.id()).size() > 1;
            boolean isJoin = predecessors.apply(node.id()).size() > 1;
            if (isFork) {
                String join = ipdom.get(node.id());
                if (paired(graph, node.id(), join, idom, pred)) {
                    joinOf.put(node.id(), join);
                    forkOf.put(join, node.id());
                    regions.put(node.id(), between(node.id(), join, successors));
                } else {
                    unpaired.add(node.id());
                }
            }
            if (isJoin) {
                String fork = idom.get(node.id());
                if (fork == null || !paired(graph, fork, node.id(), idom, pred) || !node.id().equals(ipdom.get(fork))) unpaired.add(node.id());
            }
        }
        return new GatewayPairs(Collections.unmodifiableMap(joinOf), Collections.unmodifiableMap(forkOf),
            Collections.unmodifiableSet(unpaired), Collections.unmodifiableMap(regions));
    }

    /** fork 与 join 配对：同类型、join 有不止一条入边、join 的直接支配节点就是 fork */
    private static boolean paired(ProcessGraph graph, String fork, String join, Map<String, String> idom, Map<String, List<String>> pred) {
        if (join == null || join.equals(EXIT)) return false;
        ProcessNode a = graph.node(fork);
        ProcessNode b = graph.node(join);
        return a != null && b != null && a.type().equals(b.type())
            && pred.getOrDefault(join, List.of()).size() > 1 && fork.equals(idom.get(join));
    }

    /** 从分叉往前走、不过汇合能到的节点 */
    private static Set<String> between(String fork, String join, Function<String, List<String>> successors) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(successors.apply(fork));
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (id.equals(join) || id.equals(EXIT) || !seen.add(id)) continue;
            queue.addAll(successors.apply(id));
        }
        return Collections.unmodifiableSet(seen);
    }

    /** 直接支配节点（Cooper–Harvey–Kennedy 的迭代算法）；root 映射到自己，走不到的节点不在结果里 */
    private static Map<String, String> immediateDominators(String root, Function<String, List<String>> successors,
                                                           Function<String, List<String>> predecessors) {
        List<String> order = new ArrayList<>();
        postOrder(root, successors, new HashSet<>(), order);
        List<String> rpo = new ArrayList<>(order);
        Collections.reverse(rpo);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < rpo.size(); i++) index.put(rpo.get(i), i);
        Map<String, String> idom = new HashMap<>();
        idom.put(root, root);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String id : rpo) {
                if (id.equals(root)) continue;
                List<String> preds = predecessors.apply(id).stream().filter(idom::containsKey).toList();
                if (preds.isEmpty()) continue;
                String next = preds.get(0);
                for (String p : preds.subList(1, preds.size())) next = intersect(next, p, idom, index);
                if (!next.equals(idom.get(id))) {
                    idom.put(id, next);
                    changed = true;
                }
            }
        }
        return idom;
    }

    private static void postOrder(String id, Function<String, List<String>> successors, Set<String> visited, List<String> order) {
        visited.add(id);
        for (String next : successors.apply(id)) if (!visited.contains(next)) postOrder(next, successors, visited, order);
        order.add(id);
    }

    private static String intersect(String a, String b, Map<String, String> idom, Map<String, Integer> index) {
        String x = a;
        String y = b;
        while (!x.equals(y)) {
            while (index.get(x) > index.get(y)) x = idom.get(x);
            while (index.get(y) > index.get(x)) y = idom.get(y);
        }
        return x;
    }
}
