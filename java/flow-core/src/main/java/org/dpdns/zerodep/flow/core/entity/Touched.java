package org.dpdns.zerodep.flow.core.entity;

import org.dpdns.zerodep.flow.core.db.FlowInterceptors;

import java.time.Instant;

/** 带修改时间的表：插入、更新时自动填当前时刻（{@link org.dpdns.zerodep.flow.core.db.FlowInterceptors}） */
public interface Touched {
    void setUpdatedAt(Instant updatedAt);
}
