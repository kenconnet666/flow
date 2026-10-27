package org.dpdns.zerodep.flow.core.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Navigate;
import com.easy.query.core.annotation.OrderByProperty;
import com.easy.query.core.enums.RelationTypeEnum;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.dpdns.zerodep.flow.core.entity.proxy.FormEntityProxy;

import java.time.Instant;
import java.util.List;

/** 表单本身（{@code flow_form}）：每个表单标识一行，记启用停用与最新版本 */
@Getter
@Setter
@FieldNameConstants
@Table("flow_form")
@EntityProxy
public class FormEntity implements ProxyEntityAvailable<FormEntity, FormEntityProxy>, Tenanted, Touched {
    /** 能被流程选用 */
    public static final String ACTIVE = "active";
    /** 停用：新发布的流程不能再选它，已经引用它的照常用 */
    public static final String DISABLED = "disabled";

    @Column(primaryKey = true, generatedKey = true)
    private Long id;
    private Long tenantId;
    private String formKey;
    private String name;
    private String category;
    private String status;
    private Integer latestVersion;
    private Integer rev;
    private Instant createdAt;
    private Instant updatedAt;
    private Long updatedBy;

    // ───────── 关系 ─────────

    /** 发布过的全部版本，新的在前 */
    @Navigate(value = RelationTypeEnum.OneToMany, selfProperty = Fields.formKey, targetProperty = FormVersionEntity.Fields.formKey,
        orderByProps = {@OrderByProperty(property = FormVersionEntity.Fields.version, asc = false)})
    private List<FormVersionEntity> versions;

    /** 最新发布的版本 */
    @Navigate(value = RelationTypeEnum.ManyToOne, selfProperty = {Fields.formKey, Fields.latestVersion}, targetProperty = {FormVersionEntity.Fields.formKey, FormVersionEntity.Fields.version})
    private FormVersionEntity latest;
}
