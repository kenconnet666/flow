package org.dpdns.zerodep.flow.core.view.definition;

import lombok.Data;

import java.time.Instant;

/** 设计中的草稿（{@code flow_draft}）：按 {@code select(DesignDraftVO.class)} 读出 */
@Data
public class DesignDraftVO {
    /** {@code process} 或 {@code form} */
    private String kind;
    /** 流程或表单的标识 */
    private String targetKey;
    private String name;
    /** 设计器的内容；列表里不读（为 null） */
    private String content;
    /** 基于哪个已发布的版本改的；新建的为 null */
    private Integer baseVersion;
    /** 版本：下次保存时带上 */
    private Integer rev;
    private Long updatedBy;
    private String updatedByName;
    private Instant updatedAt;
}
