package org.dpdns.zerodep.flow.core.engine.query;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * 我的待办、我已办、我发起的、抄送我的的筛选条件（0002 §9）与排序：不写的不限、按列表的默认顺序。
 *
 * <pre>
 *   ListFilter.builder().processKey("purchase").from(start).sort(SortOrder.parse("-startedAt")).build()
 * </pre>
 */
@Getter
@Builder
public class ListFilter {
    /** 不筛 */
    public static final ListFilter NONE = builder().build();

    /** 流程 */
    private final String processKey;
    /** 从这一刻起（含）：已办按处理时间、我发起的按发起时间、抄送按抄送时间 */
    private final Instant from;
    /** 到这一刻之前（不含） */
    private final Instant to;
    /** 接入方自己的筛选参数，交给 {@code TodoQueryCustomizer}（只对我的待办、我已办）；没有时是空的 */
    @Builder.Default
    private final Map<String, String> business = Map.of();
    /** 排序（我的待办两段合并，不支持；其余列表各认自己的几列） */
    @Builder.Default
    private final SortOrder sort = SortOrder.NONE;
    /** 只看已归档的（我已办、我发起的；0007 第 6 节）：归档表里的行，没有归档时是空的 */
    private final boolean archived;

    /** 写了流程 */
    public boolean hasProcess() { return processKey != null && !processKey.isBlank(); }
}
