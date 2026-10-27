package org.dpdns.zerodep.flow.core.extension;

import lombok.Data;

import java.util.function.Function;

/**
 * 计算值（0005 的 6.1，条件里写 {@code fn.名字}）：接入方按名字注册，把引擎不知道的规则算成一个值，连线条件拿它比较。
 * 例如"金额落在哪一档"（档位的阈值放在接入方的配置里，调整时不用重新发布流程）、"供应商是不是战略供应商"。
 * 每次用到都调，不缓存；要快，要没有副作用。
 */
public interface ComputedValue {
    /** 定义里写的名字（{@code fn.} 后面那段） */
    String name();

    /** 算出的值；类型同 {@link ExtensionInfo#getValueType()} 说的（文字、数字、BigDecimal、布尔、LocalDate） */
    Object compute(Context context);

    /** 设计器里的显示名、说明与值的类型；不给时只显示名字、按文字比较 */
    default ExtensionInfo info() { return null; }

    /** 算的时候能拿到的。 */
    @Data
    class Context {
        /** 实例编号 */
        private final Long instanceId;
        /** 流程标识 */
        private final String processKey;
        /** 按引用的名字取值（{@code form.amount}、流程变量、{@code sys.initiator}……） */
        private final Function<String, Object> read;
    }
}
