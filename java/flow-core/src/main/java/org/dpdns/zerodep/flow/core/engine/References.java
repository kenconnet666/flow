package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.store.InstanceStore.InstanceRecord;

/**
 * 按引用的名字取值（0005 的 {@code { ref }}）。连线条件、处理人、标题模板、字段的显示条件都经过这里取值：
 * <pre>
 *   form.amount        表单字段
 *   sys.initiator      引擎给的：发起人、发起时间、实例编号、业务编号
 *   sys.round.review   某个节点到过几轮
 *   negotiationRound   其余是流程变量
 * </pre>
 * 推进时由 {@link Execution} 用（表单与实例随推进在变），查询时直接用（算字段在某个待办上的权限）。
 */
final class References {
    private References() { }

    /** 在这个实例、这份表单的值上取 name 的值；没有时为 null */
    static Object read(String name, InstanceRecord instance, FormValues form) {
        if (name.startsWith("form.")) return form.get(name.substring("form.".length()));
        if (name.startsWith("sys.round.")) return instance.state().round(name.substring("sys.round.".length()));
        if (name.startsWith("sys.")) return system(name.substring("sys.".length()), instance);
        if (name.startsWith("fn.")) throw new UnsupportedOperationException("computed values (" + name + ") are not supported yet");
        return instance.variables().get(name);
    }

    /** {@code sys.} 下引擎给的只读值 */
    private static Object system(String name, InstanceRecord instance) {
        return switch (name) {
            case "initiator" -> instance.startedBy();
            case "startedAt" -> instance.startedAt() == null ? null : instance.startedAt().toString();
            case "instanceId" -> instance.id();
            case "businessKey" -> instance.businessKey();
            default -> null;
        };
    }
}
