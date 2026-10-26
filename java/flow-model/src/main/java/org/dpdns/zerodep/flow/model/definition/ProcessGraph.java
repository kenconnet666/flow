package org.dpdns.zerodep.flow.model.definition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程的结构索引：按标识找节点、泳道，按节点找出边、入边（同 TS 模型包的 {@code graph.ts}）。定义不可变，建一次反复用。
 * <pre>{@code
 * ProcessGraph graph = ProcessGraph.of(definition);
 * graph.outgoing("check");   // 从"资料审核"出去的连线，按定义的先后
 * }</pre>
 */
public final class ProcessGraph {
    private final ProcessDefinition definition;
    private final Map<String, ProcessNode> nodes = new HashMap<>();
    private final Map<String, Lane> lanes = new HashMap<>();
    private final Map<String, List<ProcessEdge>> outgoing = new HashMap<>();
    private final Map<String, List<ProcessEdge>> incoming = new HashMap<>();
    private final ProcessNode.Start start;

    private ProcessGraph(ProcessDefinition definition) {
        this.definition = definition;
        for (ProcessNode node : definition.nodes()) nodes.putIfAbsent(node.id(), node);
        for (Lane lane : definition.lanes()) lanes.putIfAbsent(lane.id(), lane);
        for (ProcessEdge edge : definition.edges()) {
            outgoing.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge);
            incoming.computeIfAbsent(edge.to(), k -> new ArrayList<>()).add(edge);
        }
        start = (ProcessNode.Start) definition.nodes().stream().filter(n -> n instanceof ProcessNode.Start).findFirst().orElse(null);
    }

    /** 建索引 */
    public static ProcessGraph of(ProcessDefinition definition) {
        return new ProcessGraph(definition);
    }

    /** 流程定义 */
    public ProcessDefinition definition() { return definition; }

    /** 按标识找节点；标识重复时取第一个；没有时为 null */
    public ProcessNode node(String id) { return id == null ? null : nodes.get(id); }

    /** 按标识找泳道；没有时为 null */
    public Lane lane(String id) { return id == null ? null : lanes.get(id); }

    /** 从这个节点出去的连线，按定义的先后 */
    public List<ProcessEdge> outgoing(String id) { return outgoing.getOrDefault(id, List.of()); }

    /** 进这个节点的连线 */
    public List<ProcessEdge> incoming(String id) { return incoming.getOrDefault(id, List.of()); }

    /** 开始节点；没有时为 null */
    public ProcessNode.Start start() { return start; }
}
