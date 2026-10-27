package org.dpdns.zerodep.flow.core.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import lombok.Data;
import org.dpdns.zerodep.flow.core.entity.proxy.ProcessVersionEntityProxy;

import java.time.Instant;

/**
 * 流程发布的一个版本（{@code flow_definition}）：发布后不再改，进行中的实例一直按自己的版本走。
 * {@code model} 是 0005 的整份定义的 JSON 文字；formKey、formVersion 是发布时定下的表单版本。
 */
@Data
@Table("flow_definition")
@EntityProxy
public class ProcessVersionEntity implements ProxyEntityAvailable<ProcessVersionEntity, ProcessVersionEntityProxy>, Tenanted {
    @Column(primaryKey = true, generatedKey = true)
    private Long id;
    private Long tenantId;
    private String processKey;
    private Integer version;
    private String model;
    private String formKey;
    private Integer formVersion;
    private String note;
    private Long publishedBy;
    private String publishedByName;
    private Instant publishedAt;
}
