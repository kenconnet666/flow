package org.dpdns.zerodep.flow.model.definition;

import lombok.Data;
import org.dpdns.zerodep.flow.model.Condition;

import java.util.List;

/** 连线（0005 第 6 节）。 */
@Data
public class ProcessEdge {
    /** 标识，流程内唯一 */
    private final String id;
    /** 起点 */
    private final String from;
    /** 终点 */
    private final String to;
    /** 起点按结果分出边时，哪个结果走这条；null 时任何结果都可以走（再看 when） */
    private final String outcome;
    /** 条件；null 时不加条件 */
    private final Condition when;
    /** 排他、包容网关的默认出边 */
    private final boolean isDefault;
    /** {@code return}、{@code escalate} 或 null */
    private final String kind;
    /** 走这条线时改流程变量 */
    private final List<Effect> effects;
    /** 流程图上的文字 */
    private final String label;

    public ProcessEdge(String id, String from, String to, String outcome, Condition when, boolean isDefault, String kind, List<Effect> effects, String label) { effects = effects == null ? List.of() : List.copyOf(effects);
        this.id = id;
        this.from = from;
        this.to = to;
        this.outcome = outcome;
        this.when = when;
        this.isDefault = isDefault;
        this.kind = kind;
        this.effects = effects;
        this.label = label;
    }

    /** 退回或提级：结构化的配对与"上游"都不算它们 */
    public boolean isBackward() { return "return".equals(kind) || "escalate".equals(kind); }

    /** 走这条线时改流程变量 */
    public interface Effect {
        /** 改的是哪个流程变量 */
        String getTarget();

        /** 赋值；value 是直接写的值或 {@link Ref} */
        @Data
        static class Set implements Effect {
            private final String target;
            private final Object value;
        }

        /** 加一个数（计数） */
        @Data
        static class Increment implements Effect {
            private final String target;
            private final java.math.BigDecimal by;
        }
    }
}
