package org.dpdns.zerodep.flow.core.view.instance;

import lombok.Data;

/** 实例上的一个流程变量（实例详情里只给流程管理员）：按定义里声明的先后。 */
@Data
public class VariableVO {
    /** 名字 */
    private final String name;
    /** 显示名；定义里没写时为 null */
    private final String label;
    /** 类型：同定义里声明的 */
    private final String type;
    /** 现在的值（强类型：decimal 是 BigDecimal、datetime 是 Instant……）；没有值时为 null */
    private final Object value;
}
