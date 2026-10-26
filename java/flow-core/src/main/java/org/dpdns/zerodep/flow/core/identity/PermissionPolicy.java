package org.dpdns.zerodep.flow.core.identity;

/**
 * 每个命令、查询之前判断能不能做（0006 第 6 节）。默认全部放行：页面与接口的保护由接入方自己的框架负责（0004 第 6 节的宽松原则），
 * 要收紧时替换它。引擎自己的规则（只有候选人能认领、只有发起人能撤回）不经过这里，照样生效。
 */
@FunctionalInterface
public interface PermissionPolicy<U extends Actor> {
    /** 全部放行 */
    @SuppressWarnings("rawtypes")
    PermissionPolicy ALLOW_ALL = (actor, operation, target) -> true;

    /**
     * 能不能做。
     *
     * @param operation 命令或查询的名字：{@code start}、{@code claim}、{@code complete}、{@code withdraw}、{@code startable}、{@code startView}、{@code todo}、{@code workItem}、{@code instance}、
     *                  {@code definitions.publish}、{@code forms.publish}；
     *                  协作：{@code transfer}、{@code delegate}、{@code addSigners}、{@code removeSigners}、{@code release}、{@code remind}、{@code comment}；
     *                  管理员：{@code terminate}、{@code suspend}、{@code resume}、{@code jump}、{@code reassign}
     * @param target    操作的对象：流程标识、待办编号或实例编号
     */
    boolean allows(U actor, String operation, String target);

    /** 全部放行 */
    @SuppressWarnings("unchecked")
    static <U extends Actor> PermissionPolicy<U> allowAll() { return (PermissionPolicy<U>) ALLOW_ALL; }
}
