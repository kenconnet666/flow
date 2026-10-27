package org.dpdns.zerodep.flow.core.view.form;

import lombok.Data;

import java.time.Instant;

/** 表单版本列表的一行（按 {@code select(FormVersionVO.class)} 读出）：谁、什么时候发布的、说明 */
@Data
public class FormVersionVO {
    private int version;
    private String note;
    private String publishedByName;
    private Instant publishedAt;
}
