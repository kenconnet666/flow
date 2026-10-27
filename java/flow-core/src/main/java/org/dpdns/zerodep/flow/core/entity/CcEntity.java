package org.dpdns.zerodep.flow.core.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Navigate;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.enums.RelationTypeEnum;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import lombok.Data;
import org.dpdns.zerodep.flow.core.entity.proxy.CcEntityProxy;

import java.time.Instant;

/** 抄送（{@code flow_cc}）：抄送节点、处理人手动抄送，每个收到的人一行，带已读。读出来是 {@link org.dpdns.zerodep.flow.core.view.CcVO} */
@Data
@Table("flow_cc")
@EntityProxy
public class CcEntity implements ProxyEntityAvailable<CcEntity, CcEntityProxy>, Tenanted {
    @Column(primaryKey = true, generatedKey = true)
    private Long id;
    private Long tenantId;
    private Long instanceId;
    /** 抄送节点；手动抄送时为 null */
    private String node;
    private String nodeName;
    private Long recipientId;
    private String recipientName;
    /** 谁抄送的；抄送节点时为 null */
    private Long fromUserId;
    private String fromUserName;
    private String message;
    private Instant createdAt;
    /** 读的时间；没读时为 null */
    private Instant readAt;

    /** 所在的实例 */
    @Navigate(value = RelationTypeEnum.ManyToOne, selfProperty = "instanceId", targetProperty = "id")
    private InstanceEntity instance;
}
