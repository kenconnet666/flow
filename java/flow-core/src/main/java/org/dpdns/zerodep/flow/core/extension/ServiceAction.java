package org.dpdns.zerodep.flow.core.extension;

import org.dpdns.zerodep.flow.core.view.InstanceVO;

import java.util.Map;
import java.util.function.Function;

/**
 * 自动节点的动作（0005 的 4.1 {@code service}）：接入方按名字注册，到达自动节点时做——同步的在流转的事务里做，
 * 异步的（默认）在事务提交后由作业做，失败按节点的 {@code retry} 重试，所以<b>要能重复做</b>（用 {@link Context#idempotencyKey} 去重）。
 */
public interface ServiceAction {
    /** 定义里写的名字 */
    String name();

    /** 做；抛异常就是失败（同步的整个流转回滚，异步的过一会儿重试） */
    Result perform(Context context);

    /**
     * 做的时候能拿到的。
     *
     * @param instance       实例
     * @param node           自动节点
     * @param args           定义里写的参数，其中的 {@code { ref }} 已经换成值
     * @param idempotencyKey 去重用：同一个节点的同一轮相同（重试时也相同）
     * @param read           按引用的名字取值
     */
    record Context(InstanceVO instance, String node, Map<String, Object> args, String idempotencyKey, Function<String, Object> read) { }

    /**
     * 做完的结果。
     *
     * @param outcome   走哪个结果的出边；null 时走没写结果的那条
     * @param variables 要改的流程变量；不改时是空的
     */
    record Result(String outcome, Map<String, Object> variables) {
        /** 做完了，照常往下走 */
        public static final Result DONE = new Result(null, Map.of());

        public Result {
            variables = variables == null ? Map.of() : Map.copyOf(variables);
        }

        /** 走这个结果 */
        public static Result outcome(String outcome) { return new Result(outcome, Map.of()); }
    }
}
