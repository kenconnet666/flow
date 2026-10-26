package org.dpdns.zerodep.flow.model.definition;

/**
 * 取别处的值（0005 的 1.1）：{@code form.字段}、{@code sys.名字}、流程变量名、{@code fn.名字}。
 * 定义里"可以直接写、也可以取别处的值"的地方，值的类型是 {@code Object}：直接写的值，或一个 {@code Ref}。
 *
 * @param ref 名字
 */
public record Ref(String ref) {
    /** 是不是引用 */
    public static boolean isRef(Object value) {
        return value instanceof Ref;
    }
}
