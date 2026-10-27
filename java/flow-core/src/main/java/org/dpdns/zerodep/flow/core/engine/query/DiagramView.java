package org.dpdns.zerodep.flow.core.engine.query;

import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.extension.Choice;
import org.dpdns.zerodep.flow.core.extension.ExtensionInfo;
import org.dpdns.zerodep.flow.core.extension.Extensions;
import org.dpdns.zerodep.flow.core.view.instance.FlowEventVO;
import org.dpdns.zerodep.flow.core.view.instance.InstanceVO;
import org.dpdns.zerodep.flow.core.view.instance.ProcessDiagram;
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
 * 离开时记下的连线是走过的。位置取定义的 {@code layout.nodes}。自定义节点的显示名、自动节点与自定义节点的结果的名字取接入方注册时给的
 * （{@code info()}），没给时照写类型、结果的键。
 */
public class DiagramView {
    private DiagramView() { }

    static ProcessDiagram of(ProcessDefinition definition, InstanceVO instance, List<FlowEventVO> timeline, Extensions extensions) {
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
            ExtensionInfo info = node.getType().startsWith("x-") && extensions.hasNode(node.getType()) ? extensions.node(node.getType()).info() : null;
            return new ProcessDiagram.Node(node.getId(), node.getName(), node.getType(), info == null ? null : info.getLabel(), number(at.get("x")), number(at.get("y")), state);
        }).toList();
        List<ProcessDiagram.Edge> edges = definition.getEdges().stream()
            .map(edge -> new ProcessDiagram.Edge(edge.getId(), edge.getFrom(), edge.getTo(), labelOf(definition, edge, extensions), edge.getKind(), taken.contains(edge.getId())))
            .toList();
        return new ProcessDiagram(nodes, edges);
    }

    /** 有走过的连线通到这个节点 */
    private static boolean reached(ProcessDefinition definition, String node, Set<String> taken) {
        return definition.getEdges().stream().anyMatch(e -> e.getTo().equals(node) && taken.contains(e.getId()));
    }

    /** 连线上的字：写了 label 用它；按处理结果分的线用结果的名字（人工节点写在定义里，动作、自定义节点的在注册时给） */
    private static String labelOf(ProcessDefinition definition, ProcessEdge edge, Extensions extensions) {
        if (edge.getLabel() != null) return edge.getLabel();
        if (edge.getOutcome() == null) return null;
        ProcessNode from = definition.getNodes().stream().filter(n -> n.getId().equals(edge.getFrom())).findFirst().orElse(null);
        if (from instanceof ProcessNode.Task task) {
            return task.getOutcomes().stream().filter(o -> o.getKey().equals(edge.getOutcome())).map(Outcome::getLabel).findFirst().orElse(edge.getOutcome());
        }
        ExtensionInfo info = from == null ? null : declaring(from, extensions);
        if (info == null) return edge.getOutcome();
        return info.getOutcomes().stream().filter(c -> c.getValue().equals(edge.getOutcome())).map(Choice::getLabel).findFirst().orElse(edge.getOutcome());
    }

    /** 给出结果的扩展：自动节点的动作、自定义节点；没注册时为 null */
    private static ExtensionInfo declaring(ProcessNode node, Extensions extensions) {
        if (node instanceof ProcessNode.Service service) return extensions.hasAction(service.getAction()) ? extensions.action(service.getAction()).info() : null;
        return node.getType().startsWith("x-") && extensions.hasNode(node.getType()) ? extensions.node(node.getType()).info() : null;
    }

    private static Double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }
}
