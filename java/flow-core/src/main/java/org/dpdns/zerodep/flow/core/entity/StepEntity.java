package org.dpdns.zerodep.flow.core.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.dpdns.zerodep.flow.core.db.JsonColumns;
import org.dpdns.zerodep.flow.core.engine.state.RunState;
import org.dpdns.zerodep.flow.core.engine.state.Variables;
import org.dpdns.zerodep.flow.core.entity.proxy.StepEntityProxy;
import org.dpdns.zerodep.flow.core.spi.FormValues;

/**
 * 最近一次处理之前的快照（{@code flow_step}，每个实例只留一行）：处理人撤回刚处理的一步时用它恢复。
 */
@Getter
@Setter
@FieldNameConstants
@Table("flow_step")
@EntityProxy
public class StepEntity implements ProxyEntityAvailable<StepEntity, StepEntityProxy>, Tenanted {
    @Column(primaryKey = true, generatedKey = true)
    private Long id;
    private Long tenantId;
    private Long instanceId;
    /** 处理的是哪个待办 */
    private Long workItemId;
    /** 处理那一步时实例的版本：之后实例再被动过就不能撤回 */
    private Integer rev;
    @Column(conversion = JsonColumns.RunStateJson.class)
    private RunState state;
    @Column(conversion = JsonColumns.VariablesJson.class)
    private Variables variables;
    /** 处理之前的表单的值；流程没有表单时为 null */
    @Column(conversion = JsonColumns.FormValuesJson.class)
    private FormValues form;
}
