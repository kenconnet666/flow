package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.view.FlowEventVO;
import org.dpdns.zerodep.flow.core.view.InstanceVO;
import org.dpdns.zerodep.flow.core.view.ProcessDiagram;
import org.dpdns.zerodep.flow.model.definition.Outcome;
import org.dpdns.zerodep.flow.model.definition.ProcessDefinition;
import org.dpdns.zerodep.flow.model.definition.ProcessEdge;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 实例的流程图（{@link ProcessDiagram}）：按流转记录算走到哪了——离开过的节点是走过的，实例的"正在处理的节点"是正停着的，
 * 离开时记下的连线是走过的。位置取定义的 {@code layout.nodes}。
 */
class DiagramView {
    private DiagramView() { }

    static ProcessDiagram of(ProcessDefinition definition, InstanceVO instance, List<FlowEventVO> timeline) {
        Set<String> left = new HashSet<>();
        Set<String> taken = new HashSet<>();
        for (FlowEventVO event : timeline) {
            if (!EventEntity.NODE_LEFT.equals(event.getType())) continue;
            if (event.getNode() != null) left.add(event.getNode());
            if (event.getEdge() != null) taken.add(event.getEdge());
        }
        Set<String> current = new HashSet<>();
        instance.getCurrent().forEach(c -> current.add(c.getNode()));
        // 结束了：走到的结束节点也算走过（结束节点不会"离开"）
        boolean ended = instance.getEndedAt() != null;
        Map<?, ?> positions = definition.getLayout().get("nodes") instanceof Map<?, ?> map ? map : Map.of();

        List<ProcessDiagram.Node> nodes = definition.getNodes().stream().map(node -> {
            String state = current.contains(node.getId()) ? ProcessDiagram.CURRENT
                : left.contains(node.getId()) || (ended && node instanceof ProcessNode.End && reached(definition, node.getId(), taken))
                ? ProcessDiagram.DONE : ProcessDiagram.PENDING;
            Map<?, ?> at = positions.get(node.getId()) instanceof Map<?, ?> p ? p : Map.of();
            return new ProcessDiagram.Node(node.getId(), node.getName(), node.getType(), number(at.get("x")), number(at.get("y")), state);
        }).toList();
        List<ProcessDiagram.Edge> edges = definition.getEdges().stream()
            .map(edge -> new ProcessDiagram.Edge(edge.getId(), edge.getFrom(), edge.getTo(), labelOf(definition, edge), edge.getKind(), taken.contains(edge.getId())))
            .toList();
        return new ProcessDiagram(nodes, edges);
    }

    /** 有走过的连线通到这个节点 */
    private static boolean reached(ProcessDefinition definition, String node, Set<String> taken) {
        return definition.getEdges().stream().anyMatch(e -> e.getTo().equals(node) && taken.contains(e.getId()));
    }

    /** 连线上的字：写了 label 用它；按处理结果分的线用结果的名字 */
    private static String labelOf(ProcessDefinition definition, ProcessEdge edge) {
        if (edge.getLabel() != null) return edge.getLabel();
        if (edge.getOutcome() == null) return null;
        return definition.getNodes().stream()
            .filter(n -> n.getId().equals(edge.getFrom()) && n instanceof ProcessNode.Task)
            .flatMap(n -> ((ProcessNode.Task) n).getOutcomes().stream())
            .filter(o -> o.getKey().equals(edge.getOutcome())).map(Outcome::getLabel).findFirst().orElse(edge.getOutcome());
    }

    private static Double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }
}
