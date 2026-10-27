package org.dpdns.zerodep.flow.core.view;

import lombok.Data;

import java.time.Instant;

/** 流程版本列表的一行（按 {@code select(ProcessVersionVO.class)} 读出）：谁、什么时候发布的、说明、定下的表单版本 */
@Data
public class ProcessVersionVO {
    private int version;
    private String note;
    private String publishedByName;
    private Instant publishedAt;
    private String formKey;
    private Integer formVersion;
}
