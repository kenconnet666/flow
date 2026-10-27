package org.dpdns.zerodep.flow.core.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import lombok.Data;
import org.dpdns.zerodep.flow.core.entity.proxy.WaitEntityProxy;

import java.time.Instant;

/**
 * 等外部事件（{@code flow_wait}，0005 的 4.3）：实例停在等待节点上时一行，按事件名加实例或业务编号找到它；
 * 事件到了带条件地删掉这一行（删到 0 行就是已经处理过），再推进。
 */
@Data
@Table("flow_wait")
@EntityProxy
public class WaitEntity implements ProxyEntityAvailable<WaitEntity, WaitEntityProxy>, Tenanted {
    @Column(primaryKey = true, generatedKey = true)
    private Long id;
    private Long tenantId;
    private Long instanceId;
    private String node;
    private String event;
    /** 实例的业务单据编号；没有时为 null */
    private String businessKey;
    private Instant createdAt;
}
