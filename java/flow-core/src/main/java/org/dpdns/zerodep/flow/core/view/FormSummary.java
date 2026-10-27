package org.dpdns.zerodep.flow.core.view;

import lombok.Data;

/** 管理端的表单列表的一行。 */
@Data
public class FormSummary {
    /** 表单标识 */
    private final String key;
    /** 名称 */
    private final String name;
    /** 分类 */
    private final String category;
    /** {@code active} 能被流程选用，{@code disabled} 停用（已经引用它的流程照常用） */
    private final String status;
    /** 最新发布的版本号 */
    private final int latestVersion;
}
