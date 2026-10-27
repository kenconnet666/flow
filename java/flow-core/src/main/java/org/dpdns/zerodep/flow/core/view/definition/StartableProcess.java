package org.dpdns.zerodep.flow.core.view.definition;

import lombok.Data;

/** 我能发起的一个流程（发起列表上的一项）。 */
@Data
public class StartableProcess {
    /** 流程标识 */
    private final String key;
    /** 名称 */
    private final String name;
    /** 分类 */
    private final String category;
    /** 最新发布的版本号 */
    private final int version;
}
