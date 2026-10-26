package org.dpdns.zerodep.flow.model.definition;

import org.dpdns.zerodep.flow.model.Values;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 两个版本的流程定义差在哪（0002 §1 的版本对比），与 TS 模型包的 {@code diff.ts} 同一套规则，两边跑 spec/diff 的样例。
 * 比较的是 JSON 树（{@code Json.read} 读出来的）：对象的键顺序不算差别，数字按值比，null 与没有这个键相同。
 * 布局默认不比；版本号不比。
 * <pre>{@code
 * ProcessDiff diff = ProcessDiff.of(Json.read(v1), Json.read(v2), false);
 * }</pre>
 *
 * @param process 流程级不一样的属性（节点、连线、泳道、布局、版本号以外），按名字排序
 * @param lanes   泳道的差别
 * @param nodes   节点的差别
 * @param edges   连线的差别
 * @param layout  只在要求比布局时有：位置变了的节点、拐点变了的连线
 */
public record ProcessDiff(List<String> process, ItemDiff lanes, ItemDiff nodes, ItemDiff edges, LayoutDiff layout) {
    private static final Set<String> PROCESS_SKIP = Set.of("nodes", "edges", "lanes", "layout", "version");

    /**
     * 一类东西的差别。
     *
     * @param added   新版本里才有的，按新版本里的先后
     * @param removed 旧版本里才有的，按旧版本里的先后
     * @param changed 两边都有但不一样的，按新版本里的先后
     */
    public record ItemDiff(List<String> added, List<String> removed, List<Change> changed) { }

    /**
     * 一处改动。
     *
     * @param id   标识
     * @param keys 不一样的属性，按名字排序
     */
    public record Change(String id, List<String> keys) { }

    /**
     * 布局的差别。
     *
     * @param nodes 位置变了的节点，按标识排序
     * @param edges 拐点变了的连线，按标识排序
     */
    public record LayoutDiff(List<String> nodes, List<String> edges) { }

    /** 两个版本（JSON 树）的差别；layout 为 true 时连布局一起比 */
    public static ProcessDiff of(Object before, Object after, boolean layout) {
        Map<?, ?> a = (Map<?, ?>) before;
        Map<?, ?> b = (Map<?, ?>) after;
        LayoutDiff layoutDiff = null;
        if (layout) {
            Map<?, ?> la = map(a.get("layout"));
            Map<?, ?> lb = map(b.get("layout"));
            layoutDiff = new LayoutDiff(changedKeys(map(la.get("nodes")), map(lb.get("nodes")), Set.of()),
                changedKeys(map(la.get("edges")), map(lb.get("edges")), Set.of()));
        }
        return new ProcessDiff(changedKeys(a, b, PROCESS_SKIP), items(a.get("lanes"), b.get("lanes")), items(a.get("nodes"), b.get("nodes")),
            items(a.get("edges"), b.get("edges")), layoutDiff);
    }

    /** 两个 JSON 值相等：对象不看键的顺序，数字按值比，null 与没有这个键相同 */
    public static boolean sameJson(Object a, Object b) {
        if (a == null || b == null) return a == null && b == null;
        if (a instanceof List<?> || b instanceof List<?>) {
            if (!(a instanceof List<?> l1) || !(b instanceof List<?> l2) || l1.size() != l2.size()) return false;
            for (int i = 0; i < l1.size(); i++) if (!sameJson(l1.get(i), l2.get(i))) return false;
            return true;
        }
        if (a instanceof Map<?, ?> || b instanceof Map<?, ?>) {
            if (!(a instanceof Map<?, ?> m1) || !(b instanceof Map<?, ?> m2)) return false;
            Set<Object> keys = new LinkedHashSet<>(m1.keySet());
            keys.addAll(m2.keySet());
            for (Object key : keys) if (!sameJson(m1.get(key), m2.get(key))) return false;
            return true;
        }
        if (a instanceof Number && b instanceof Number) return Values.compare(a, b) == 0;
        return a.equals(b);
    }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> m ? m : Map.of();
    }

    /** 两个对象里不一样的属性，按名字排序 */
    private static List<String> changedKeys(Map<?, ?> a, Map<?, ?> b, Set<String> skip) {
        Set<String> keys = new TreeSet<>();
        for (Object key : a.keySet()) keys.add(String.valueOf(key));
        for (Object key : b.keySet()) keys.add(String.valueOf(key));
        List<String> out = new ArrayList<>();
        for (String key : keys) if (!skip.contains(key) && !sameJson(a.get(key), b.get(key))) out.add(key);
        return out;
    }

    private static Map<String, Map<?, ?>> byId(Object list) {
        Map<String, Map<?, ?>> out = new LinkedHashMap<>();
        if (list instanceof List<?> items) {
            for (Object item : items) if (item instanceof Map<?, ?> m) out.putIfAbsent(String.valueOf(m.get("id")), m);
        }
        return out;
    }

    private static ItemDiff items(Object before, Object after) {
        Map<String, Map<?, ?>> a = byId(before);
        Map<String, Map<?, ?>> b = byId(after);
        List<String> added = b.keySet().stream().filter(id -> !a.containsKey(id)).toList();
        List<String> removed = a.keySet().stream().filter(id -> !b.containsKey(id)).toList();
        List<Change> changed = new ArrayList<>();
        for (String id : b.keySet()) {
            if (!a.containsKey(id)) continue;
            List<String> keys = changedKeys(a.get(id), b.get(id), Set.of("id"));
            if (!keys.isEmpty()) changed.add(new Change(id, keys));
        }
        return new ItemDiff(added, removed, changed);
    }
}
