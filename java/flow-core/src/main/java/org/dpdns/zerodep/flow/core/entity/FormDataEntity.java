package org.dpdns.zerodep.flow.core.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import lombok.Data;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.db.JsonColumns;
import org.dpdns.zerodep.flow.core.entity.proxy.FormDataEntityProxy;

import java.time.Instant;

/** 实例上表单的值（{@code flow_form_data}，每个实例一行）：发起时写第一份，每次处理按节点上的字段权限合并后整份写回 */
@Data
@Table("flow_form_data")
@EntityProxy
public class FormDataEntity implements ProxyEntityAvailable<FormDataEntity, FormDataEntityProxy>, Tenanted, Touched {
    @Column(primaryKey = true)
    private Long instanceId;
    private Long tenantId;
    /** 填的是哪个表单版本 */
    private Long formVersionId;
    @Column(value = "data", conversion = JsonColumns.FormValuesJson.class)
    private FormValues values;
    private Integer rev;
    private Instant updatedAt;
}
