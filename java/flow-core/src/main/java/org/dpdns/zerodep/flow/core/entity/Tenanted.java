package org.dpdns.zerodep.flow.core.entity;

/**
 * 带租户列的表（每张 flow_* 表都是）：查询、更新、删除自动加 {@code tenant_id = 当前租户}，插入时自动填
 * （{@link org.dpdns.zerodep.flow.core.db.FlowInterceptors}）。
 */
public interface Tenanted {
    Long getTenantId();

    void setTenantId(Long tenantId);
}
