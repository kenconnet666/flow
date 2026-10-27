package org.dpdns.zerodep.flow.core.engine.query;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/** 我的待办、我已办、我发起的、抄送我的的筛选条件（0002 §9）：不写的不限 */
@Getter
@AllArgsConstructor
public class ListFilter {
    /** 不筛 */
    public static final ListFilter NONE = new ListFilter(null, null, null, Map.of());

    /** 流程 */
    private final String processKey;
    /** 从这一刻起（含）：已办按处理时间、我发起的按发起时间、抄送按抄送时间 */
    private final Instant from;
    /** 到这一刻之前（不含） */
    private final Instant to;
    /** 接入方自己的筛选参数，交给 {@code TodoQueryCustomizer}（只对我的待办、我已办）；没有时是空的 */
    private final Map<String, String> business;

    /** 不带接入方参数 */
    public ListFilter(String processKey, Instant from, Instant to) { this(processKey, from, to, Map.of()); }

    /** 写了流程 */
    public boolean hasProcess() { return processKey != null && !processKey.isBlank(); }
}
