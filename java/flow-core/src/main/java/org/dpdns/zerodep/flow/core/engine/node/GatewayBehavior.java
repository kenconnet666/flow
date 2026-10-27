package org.dpdns.zerodep.flow.core.engine.node;

import org.dpdns.zerodep.flow.core.engine.Execution;

/**
 * 网关（0005 的 4.4）：排他网关与并行、包容的分叉直接往下走（选线在 {@link Execution#leave}）；
 * 汇合（配对的分叉记下了要等几条）到了一条就少等一条，等齐了才往下走，没等齐时这条分支就停在这里。
 */
public class GatewayBehavior {
    private GatewayBehavior() { }

    public static void arrive(Execution run, String nodeId) {
        if (run.getPairs().forkOf(nodeId) != null) {
            boolean last = run.getInstance().getState().waitingAt(nodeId) <= 1;
            run.updateState(s -> s.arrive(nodeId));
            if (!last) return;
        }
        run.leave(nodeId, null);
    }
}
