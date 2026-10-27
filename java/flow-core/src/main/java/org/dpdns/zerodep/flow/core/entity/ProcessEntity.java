package org.dpdns.zerodep.flow.core.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import lombok.Data;
import org.dpdns.zerodep.flow.core.entity.proxy.ProcessEntityProxy;

import java.time.Instant;

/** 流程本身（{@code flow_process}）：每个流程标识一行，记启用停用与最新版本。两个人同时发布时后到的在唯一键上失败 */
@Data
@Table("flow_process")
@EntityProxy
public class ProcessEntity implements ProxyEntityAvailable<ProcessEntity, ProcessEntityProxy>, Tenanted, Touched {
    /** 能发起 */
    public static final String ACTIVE = "active";
    /** 停用：不能再发起，进行中的照常走完 */
    public static final String DISABLED = "disabled";

    @Column(primaryKey = true, generatedKey = true)
    private Long id;
    private Long tenantId;
    private String processKey;
    /** 最新版本的名称 */
    private String name;
    private String category;
    private String status;
    private Integer latestVersion;
    private Integer rev;
    private Instant createdAt;
    private Instant updatedAt;
    private Long updatedBy;
}
