package org.dpdns.zerodep.flow.core.view;

import java.util.List;

/**
 * 流程图（实例详情里）：实例按的那个版本的节点与连线，加上走到哪了——哪些节点走过、哪些正停着、哪些连线走过。
 * 位置取定义的 {@code layout}（0005 第 1 节"布局单独放"）；没画过布局的节点位置为 null，前端自动排。
 *
 * @param nodes 节点，按定义里的先后
 * @param edges 连线，按定义里的先后
 */
public record ProcessDiagram(List<Node> nodes, List<Edge> edges) {
    /** 走过了 */
    public static final String DONE = "done";
    /** 正停在这里 */
    public static final String CURRENT = "current";
    /** 还没到 */
    public static final String PENDING = "pending";

    /**
     * 一个节点。
     *
     * @param type  节点类型（start、task、exclusive……）
     * @param x     布局里的位置；没有时为 null
     * @param y     布局里的位置；没有时为 null
     * @param state {@link #DONE}、{@link #CURRENT}、{@link #PENDING}
     */
    public record Node(String id, String name, String type, Double x, Double y, String state) { }

    /**
     * 一条连线。
     *
     * @param label 连线上的字：写了 label 用它，否则用处理结果的名字
     * @param kind  {@code return}、{@code escalate}，或 null
     * @param taken 走过没有
     */
    public record Edge(String id, String from, String to, String label, String kind, boolean taken) { }
}
