package org.dpdns.zerodep.flow.model.validate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 校验发现的一个问题：只给代号和参数，文案在前端的语言包里（0005 第 7 节）。
 *
 * @param code     代号（{@code outcome-unrouted}）
 * @param severity 严重程度
 * @param node     出问题的节点；没有时为 null
 * @param edge     出问题的连线
 * @param lane     出问题的泳道
 * @param field    字段清单的校验：出问题的字段路径（明细表的列写成 {@code 列表.列}）
 * @param params   参数，保持写入的先后
 */
public record Problem(String code, Severity severity, String node, String edge, String lane, String field, Map<String, String> params) {
    public Problem { params = params == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(params)); }

    /** 严重程度 */
    public enum Severity {
        /** 引擎会走不下去，不能发布 */
        ERROR,
        /** 能走，但多半是画漏了 */
        WARNING
    }
}
