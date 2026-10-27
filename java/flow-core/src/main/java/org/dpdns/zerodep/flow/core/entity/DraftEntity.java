package org.dpdns.zerodep.flow.core.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.dpdns.zerodep.flow.core.entity.proxy.DraftEntityProxy;

import java.time.Instant;

/**
 * 设计中的草稿（{@code flow_draft}，0004 的 5.2）：流程定义与表单共用，每个流程、表单一行，设计器自动保存到这里，发布成功后删掉。
 * 内容是设计器自己的状态，引擎不解释；版本列 {@code rev} 防两个人互相覆盖。读给前端的是 {@link org.dpdns.zerodep.flow.core.view.design.DraftDocument}。
 */
@Getter
@Setter
@FieldNameConstants
@Table("flow_draft")
@EntityProxy
public class DraftEntity implements ProxyEntityAvailable<DraftEntity, DraftEntityProxy>, Tenanted, Touched {
    /** 流程定义的草稿 */
    public static final String PROCESS = "process";
    /** 表单的草稿 */
    public static final String FORM = "form";

    @Column(primaryKey = true, generatedKey = true)
    private Long id;
    private Long tenantId;
    /** {@link #PROCESS} 或 {@link #FORM} */
    private String kind;
    /** 流程或表单的标识 */
    private String targetKey;
    private String name;
    /** 设计器的内容：流程是定义的 JSON，表单是源码与清单 */
    private String content;
    /** 基于哪个已发布的版本改的；新建的为 null */
    private Integer baseVersion;
    private Integer rev;
    private Long updatedBy;
    private String updatedByName;
    private Instant updatedAt;
}
