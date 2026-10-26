package org.dpdns.zerodep.flow.model.definition;

import org.dpdns.zerodep.flow.model.Condition;

import java.util.List;

/**
 * 连线（0005 第 6 节）。
 *
 * @param id        标识，流程内唯一
 * @param from      起点
 * @param to        终点
 * @param outcome   起点按结果分出边时，哪个结果走这条；null 时任何结果都可以走（再看 when）
 * @param when      条件；null 时不加条件
 * @param isDefault 排他、包容网关的默认出边
 * @param kind      {@code return}、{@code escalate} 或 null
 * @param effects   走这条线时改流程变量
 * @param label     流程图上的文字
 */
public record ProcessEdge(String id, String from, String to, String outcome, Condition when, boolean isDefault, String kind,
                          List<Effect> effects, String label) {
    public ProcessEdge { effects = effects == null ? List.of() : List.copyOf(effects); }

    /** 退回或提级：结构化的配对与"上游"都不算它们 */
    public boolean isBackward() { return "return".equals(kind) || "escalate".equals(kind); }

    /** 走这条线时改流程变量 */
    public sealed interface Effect {
        /** 改的是哪个流程变量 */
        String target();

        /** 赋值；value 是直接写的值或 {@link Ref} */
        record Set(String target, Object value) implements Effect { }

        /** 加一个数（计数） */
        record Increment(String target, java.math.BigDecimal by) implements Effect { }
    }
}
