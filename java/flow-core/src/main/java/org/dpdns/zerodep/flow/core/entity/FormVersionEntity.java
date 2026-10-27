package org.dpdns.zerodep.flow.core.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import lombok.Data;
import org.dpdns.zerodep.flow.core.entity.proxy.FormVersionEntityProxy;

import java.time.Instant;

/**
 * 表单发布的一个版本（{@code flow_form_version}，0004 的 1.1、1.5、5.2）：发布后不能改。字段清单是 JSON 文字；
 * 前端源码与编译结果可能很大，只在前端要渲染表单时读。
 */
@Data
@Table("flow_form_version")
@EntityProxy
public class FormVersionEntity implements ProxyEntityAvailable<FormVersionEntity, FormVersionEntityProxy>, Tenanted {
    @Column(primaryKey = true, generatedKey = true)
    private Long id;
    private Long tenantId;
    private String formKey;
    private Integer version;
    private String clientSource;
    private String clientCompiled;
    /** 编译它的编译器版本 */
    private String compilerVersion;
    private String manifest;
    /** {@code java}、{@code ts} 或 null（没有后端代码） */
    private String serverLanguage;
    private String serverSource;
    private String note;
    private Long publishedBy;
    private String publishedByName;
    private Instant publishedAt;
}
