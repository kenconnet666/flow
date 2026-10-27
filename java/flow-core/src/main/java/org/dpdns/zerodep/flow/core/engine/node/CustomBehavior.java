package org.dpdns.zerodep.flow.core.engine.node;

import org.dpdns.zerodep.flow.core.engine.Execution;
import org.dpdns.zerodep.flow.core.engine.store.Instances;
import org.dpdns.zerodep.flow.core.entity.WaitEntity;
import org.dpdns.zerodep.flow.core.spi.CustomNode;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;

import java.util.Map;

/**
 * 自定义节点（{@code x-} 开头的类型，0005 第 9 节）：到达时交给接入方注册的 {@link CustomNode}；
 * 它要等外部事件时同等事件节点一样记一行 {@code flow_wait}，事件到了由 {@code SignalHandler} 调 {@link #received}。
 */
public class CustomBehavior {
    private CustomBehavior() { }

    public static void arrive(Execution run, ProcessNode.Custom node) {
        CustomNode handler = run.getContext().getExtensions().node(node.getType());
        apply(run, node, handler.arrive(context(run, node)));
    }

    /** 等的事件到了：不再等，按注册的实现说的往下走 */
    public static void received(Execution run, ProcessNode.Custom node, String outcome, Map<String, Object> data) {
        run.updateState(s -> s.waitDone(node.getId()));
        CustomNode handler = run.getContext().getExtensions().node(node.getType());
        apply(run, node, handler.received(context(run, node), outcome, data));
    }

    private static void apply(Execution run, ProcessNode.Custom node, CustomNode.Result result) {
        if (!result.getVariables().isEmpty()) run.getInstance().setVariables(run.getInstance().getVariables().with(result.getVariables()));
        if (result.getWaitFor() == null) {
            run.leave(node.getId(), result.getOutcome());
            return;
        }
        run.updateState(s -> s.enter(node.getId()).waitUntil(node.getId(), run.getNow()));
        WaitEntity wait = new WaitEntity();
        wait.setInstanceId(run.getInstance().getId());
        wait.setNode(node.getId());
        wait.setEvent(result.getWaitFor());
        wait.setBusinessKey(run.getInstance().getBusinessKey());
        wait.setCreatedAt(run.getNow());
        run.getContext().getDb().insertable(wait).executeRows();
    }

    private static CustomNode.Context context(Execution run, ProcessNode.Custom node) {
        return new CustomNode.Context(Instances.view(run.getContext().getDb(), run.getInstance().getId()), node.getId(), node.getAttributes(), run::read);
    }
}
