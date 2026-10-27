package org.dpdns.zerodep.flow.model.validate;

import lombok.Data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 校验发现的一个问题：只给代号和参数，文案在前端的语言包里（0005 第 7 节）。 */
@Data
public class Problem {
    /** 代号（{@code outcome-unrouted}） */
    private final String code;
    /** 严重程度 */
    private final Severity severity;
    /** 出问题的节点；没有时为 null */
    private final String node;
    /** 出问题的连线 */
    private final String edge;
    /** 出问题的泳道 */
    private final String lane;
    /** 字段清单的校验：出问题的字段路径（明细表的列写成 {@code 列表.列}） */
    private final String field;
    /** 参数，保持写入的先后 */
    private final Map<String, String> params;

    public Problem(String code, Severity severity, String node, String edge, String lane, String field, Map<String, String> params) { params = params == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(params));
        this.code = code;
        this.severity = severity;
        this.node = node;
        this.edge = edge;
        this.lane = lane;
        this.field = field;
        this.params = params;
    }

    /** 严重程度 */
    public enum Severity {
        /** 引擎会走不下去，不能发布 */
        ERROR,
        /** 能走，但多半是画漏了 */
        WARNING
    }
}
