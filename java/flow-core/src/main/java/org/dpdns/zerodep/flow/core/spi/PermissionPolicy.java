package org.dpdns.zerodep.flow.core.spi;

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
     * @param operation 命令或查询的名字：
     *                  发起与处理：{@code start}、{@code saveStartDraft}、{@code discardStartDraft}、{@code startDrafts}、{@code startable}、{@code startView}、{@code claim}、{@code complete}、{@code saveDraft}、{@code withdraw}、{@code withdrawStep}、{@code forms.action}；
     *                  查询：{@code todo}、{@code done}、{@code started}、{@code cc.list}、{@code workItem}、{@code instance}、{@code progress}、{@code designations}、{@code instances.search}、{@code processes.list}、{@code incidents}、{@code stats}；
     *                  协作：{@code transfer}、{@code delegate}、{@code addSigners}、{@code removeSigners}、{@code release}、{@code remind}、{@code comment}、{@code cc}、{@code readCc}、{@code readWorkItem}、{@code signal}、{@code assign}、{@code requestReassign}；
     *                  设计：{@code definitions.publish}、{@code definitions.validate}、{@code definitions.status}、{@code definitions.versions}、{@code definitions.read}、{@code forms.publish}、{@code forms.list}、{@code forms.versions}、{@code forms.read}、{@code forms.status}、{@code forms.compile}、{@code drafts.save}、{@code drafts.discard}、{@code drafts.read}、{@code extensions.read}、{@code calendars.read}、{@code calendars.save}、{@code calendars.delete}；
     *                  管理员：{@code terminate}、{@code suspend}、{@code resume}、{@code jump}、{@code reassign}、{@code retryJob}、{@code skipJob}、{@code migrate}、{@code changeData}
     * @param target    操作的对象：流程标识、待办编号或实例编号
     */
    boolean allows(U actor, String operation, String target);

    /** 全部放行 */
    @SuppressWarnings("unchecked")
    static <U extends Actor> PermissionPolicy<U> allowAll() { return (PermissionPolicy<U>) ALLOW_ALL; }
}
