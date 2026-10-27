package org.dpdns.zerodep.flow.core.view.definition;

import lombok.Data;

/** 管理端的流程列表的一行。 */
@Data
public class ProcessSummary {
    /** 流程标识 */
    private final String key;
    /** 名称 */
    private final String name;
    /** 分类 */
    private final String category;
    /** {@code active} 能发起，其余停用 */
    private final String status;
    /** 最新发布的版本号 */
    private final int latestVersion;
}
