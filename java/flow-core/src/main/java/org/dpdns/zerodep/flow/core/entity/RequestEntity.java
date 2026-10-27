package org.dpdns.zerodep.flow.core.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.dpdns.zerodep.flow.core.entity.proxy.RequestEntityProxy;

import java.time.Instant;

/**
 * 命令的幂等记录（{@code flow_request}）：带请求编号的命令做成后记下响应，同一个编号再来时直接返回它。
 * 与命令同一个事务写；同一个编号并发写第二次时违反唯一约束，那个命令整个回滚。
 */
@Getter
@Setter
@FieldNameConstants
@Table("flow_request")
@EntityProxy
public class RequestEntity implements ProxyEntityAvailable<RequestEntity, RequestEntityProxy>, Tenanted {
    @Column(primaryKey = true, generatedKey = true)
    private Long id;
    private Long tenantId;
    private String requestId;
    /** 哪个命令 */
    private String command;
    /** 谁做的 */
    private Long actorId;
    /** 第一次的响应 */
    private String response;
    private Instant createdAt;
}
