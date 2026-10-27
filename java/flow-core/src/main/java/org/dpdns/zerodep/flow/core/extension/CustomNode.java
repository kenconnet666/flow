package org.dpdns.zerodep.flow.core.extension;

import lombok.Data;
import org.dpdns.zerodep.flow.core.view.instance.InstanceVO;

import java.util.Map;
import java.util.function.Function;

/**
 * 自定义节点类型（0005 第 9 节，定义里 {@code "type": "x-名字"}）：接入方注册，到达这个节点时做什么由它定。
 * 两种做法：
 * <ul>
 *   <li>当场做完：{@link Result#proceed} 按某个结果往下走（可以顺带改流程变量），在流转的事务里做，抛异常整个回滚；</li>
 *   <li>停下来等外部事件：{@link Result#waitFor} 记下等哪个事件，事件由 {@code signal} 命令送来（同等事件节点），
 *       送到时调 {@link #received} 决定怎样往下。</li>
 * </ul>
 * 节点上除共同字段以外的全部字段在 {@link Context#getAttributes()} 里，引擎不解释。
 */
public interface CustomNode {
    /** 节点类型：以 {@code x-} 开头，同定义里写的 */
    String type();

    /** 设计器里的显示名、说明、属性与可能的结果：节点面板里按它画出这种节点和它的属性；不给时只显示类型名，属性写 JSON */
    default ExtensionInfo info() { return null; }

    /** 到达时 */
    Result arrive(Context context);

    /** 等的事件到了（{@link #arrive} 返回 {@link Result#waitFor} 时）；默认按事件带的结果往下走 */
    default Result received(Context context, String outcome, Map<String, Object> data) { return Result.proceed(outcome); }

    /** 做的时候能拿到的。 */
    @Data
    static class Context {
        /** 实例 */
        private final InstanceVO instance;
        /** 节点 */
        private final String node;
        /** 节点上的其余字段（定义里写的原样） */
        private final Map<String, Object> attributes;
        /** 按引用的名字取值（{@code form.金额}、流程变量、{@code sys.initiator}） */
        private final Function<String, Object> read;
    }

    /** 怎样往下。 */
    @Data
    static class Result {
        /** 走哪个结果的出边；null 时走没写结果的那条。等事件时为 null */
        private final String outcome;
        /** 要改的流程变量；不改时是空的 */
        private final Map<String, Object> variables;
        /** 停下来等的事件名；不等时为 null */
        private final String waitFor;

        /** 按这个结果往下走 */
        public static Result proceed(String outcome) { return new Result(outcome, Map.of(), null); }

        /** 改了流程变量，再按这个结果往下走 */
        public static Result proceed(String outcome, Map<String, Object> variables) { return new Result(outcome, Map.copyOf(variables), null); }

        /** 停下来等这个事件（{@code signal} 命令按事件名加实例或业务编号送来） */
        public static Result waitFor(String event) { return new Result(null, Map.of(), event); }
    }
}
