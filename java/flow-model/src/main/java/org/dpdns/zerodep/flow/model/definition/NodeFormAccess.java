package org.dpdns.zerodep.flow.model.definition;

import java.util.Map;

/**
 * 节点上的表单权限（0004 第 4 节）。
 *
 * @param defaultAccess 没列出的字段按它；null 时发起节点 {@code editable}、其他节点 {@code readonly}
 * @param fields        按字段名；明细表的列写成 {@code 列表.列}；保持定义里的先后
 */
public record NodeFormAccess(FieldAccess defaultAccess, Map<String, FieldAccess> fields) {
    public NodeFormAccess { fields = fields == null ? Map.of() : fields; }
}
