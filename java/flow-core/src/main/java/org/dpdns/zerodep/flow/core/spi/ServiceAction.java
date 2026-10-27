package org.dpdns.zerodep.flow.core.spi;

import lombok.Data;
import org.dpdns.zerodep.flow.core.view.instance.InstanceVO;

import java.util.Map;
import java.util.function.Function;

/**
 * 自动节点的动作（0005 的 4.1 {@code service}）：接入方按名字注册，到达自动节点时做——同步的在流转的事务里做，
 * 异步的（默认）在事务提交后由作业做，失败按节点的 {@code retry} 重试，所以<b>要能重复做</b>（用 {@link Context#idempotencyKey} 去重）。
 */
public interface ServiceAction {
    /** 定义里写的名字 */
    String name();

    /** 设计器里的显示名、说明、参数与可能的结果；不给时只显示名字，参数写 JSON */
    default ExtensionInfo info() { return null; }

    /** 做；抛异常就是失败（同步的整个流转回滚，异步的过一会儿重试） */
    Result perform(Context context);

    /** 做的时候能拿到的。 */
    @Data
    static class Context {
        /** 实例 */
        private final InstanceVO instance;
        /** 自动节点 */
        private final String node;
        /** 定义里写的参数，其中的 {@code { ref }} 已经换成值 */
        private final Map<String, Object> args;
        /** 去重用：同一个节点的同一轮相同（重试时也相同） */
        private final String idempotencyKey;
        /** 按引用的名字取值 */
        private final Function<String, Object> read;
    }

    /** 做完的结果。 */
    @Data
    static class Result {
        /** 走哪个结果的出边；null 时走没写结果的那条 */
        private final String outcome;
        /** 要改的流程变量；不改时是空的 */
        private final Map<String, Object> variables;

        /** 做完了，照常往下走 */
        public static final Result DONE = new Result(null, Map.of());

        public Result(String outcome, Map<String, Object> variables) {
            variables = variables == null ? Map.of() : Map.copyOf(variables);
            this.outcome = outcome;
            this.variables = variables;
        }

        /** 走这个结果 */
        public static Result outcome(String outcome) { return new Result(outcome, Map.of()); }
    }
}
