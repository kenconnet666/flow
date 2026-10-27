package org.dpdns.zerodep.flow.model.definition;

import lombok.Data;

import java.util.Map;

/** 节点上的表单权限（0004 第 4 节）。 */
@Data
public class NodeFormAccess {
    /** 没列出的字段按它；null 时发起节点 {@code editable}、其他节点 {@code readonly} */
    private final FieldAccess defaultAccess;
    /** 按字段名；明细表的列写成 {@code 列表.列}；保持定义里的先后 */
    private final Map<String, FieldAccess> fields;

    public NodeFormAccess(FieldAccess defaultAccess, Map<String, FieldAccess> fields) { fields = fields == null ? Map.of() : fields;
        this.defaultAccess = defaultAccess;
        this.fields = fields;
    }
}
