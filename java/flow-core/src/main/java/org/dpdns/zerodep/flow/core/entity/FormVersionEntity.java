package org.dpdns.zerodep.flow.core.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.dpdns.zerodep.flow.core.entity.proxy.FormVersionEntityProxy;

import java.time.Instant;

/**
 * 表单发布的一个版本（{@code flow_form_version}，0004 的 1.1、1.5、5.2）：发布后不能改。字段清单是 JSON 文字；
 * 前端源码、编译结果、后端源码可能很大，默认不读（{@code autoSelect = false}），只在要渲染、编辑表单时显式选出来。
 */
@Getter
@Setter
@FieldNameConstants
@Table("flow_form_version")
@EntityProxy
public class FormVersionEntity implements ProxyEntityAvailable<FormVersionEntity, FormVersionEntityProxy>, Tenanted {
    @Column(primaryKey = true, generatedKey = true)
    private Long id;
    private Long tenantId;
    private String formKey;
    private Integer version;
    /** 前端源码；同下面两个大字段一样默认不读，要时显式选（{@code FETCHER.clientSource()}） */
    @Column(autoSelect = false)
    private String clientSource;
    @Column(autoSelect = false)
    private String clientCompiled;
    /** 编译它的编译器版本 */
    private String compilerVersion;
    private String manifest;
    /** {@code java}、{@code ts} 或 null（没有后端代码） */
    private String serverLanguage;
    @Column(autoSelect = false)
    private String serverSource;
    private String note;
    private Long publishedBy;
    private String publishedByName;
    private Instant publishedAt;
}
