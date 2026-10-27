package org.dpdns.zerodep.flow.core.view.instance;

import lombok.Data;

import java.util.List;

/**
 * 流程图（实例详情里）：实例按的那个版本的节点与连线，加上走到哪了——哪些节点走过、哪些正停着、哪些连线走过。
 * 位置取定义的 {@code layout}（0005 第 1 节"布局单独放"）；没画过布局的节点位置为 null，前端自动排。
 */
@Data
public class ProcessDiagram {
    /** 节点，按定义里的先后 */
    private final List<Node> nodes;
    /** 连线，按定义里的先后 */
    private final List<Edge> edges;

    /** 走过了 */
    public static final String DONE = "done";
    /** 正停在这里 */
    public static final String CURRENT = "current";
    /** 还没到 */
    public static final String PENDING = "pending";

    /** 一个节点。 */
    @Data
    public static class Node {
        private final String id;
        private final String name;
        /** 节点类型（start、task、exclusive……） */
        private final String type;
        /** 布局里的位置；没有时为 null */
        private final Double x;
        /** 布局里的位置；没有时为 null */
        private final Double y;
        /** {@link #DONE}、{@link #CURRENT}、{@link #PENDING} */
        private final String state;
    }

    /** 一条连线。 */
    @Data
    public static class Edge {
        private final String id;
        private final String from;
        private final String to;
        /** 连线上的字：写了 label 用它，否则用处理结果的名字 */
        private final String label;
        /** {@code return}、{@code escalate}，或 null */
        private final String kind;
        /** 走过没有 */
        private final boolean taken;
    }
}
