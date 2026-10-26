package org.dpdns.zerodep.flow.model;

import java.util.List;

/**
 * 条件树：比较一个字段，或用 all / any / not 组合。与组件库、TS 模型包的 {@code Condition} 同形（{@code { field, op, value }}），
 * 连线条件、表单的显示与必填条件都用它（0005 的 6.2、0004 的 1.3）。读、求值见 {@link Conditions}。
 */
public sealed interface Condition {
    /**
     * 比较一个字段。
     *
     * @param field 字段名：{@code form.字段}、{@code sys.名字}、流程变量名、{@code fn.名字}（0005 的 1.1）
     * @param op    比较的方式
     * @param value 比较的值；{@code between} 是两个值的 List，{@code in} / {@code notIn} 是 List，{@code exists} / {@code empty} 是 null
     */
    record Rule(String field, ConditionOp op, Object value) implements Condition { }

    /** 全部满足 */
    record All(List<Condition> items) implements Condition {
        public All { items = List.copyOf(items); }
    }

    /** 任一满足 */
    record Any(List<Condition> items) implements Condition {
        public Any { items = List.copyOf(items); }
    }

    /** 不满足 */
    record Not(Condition item) implements Condition { }
}
