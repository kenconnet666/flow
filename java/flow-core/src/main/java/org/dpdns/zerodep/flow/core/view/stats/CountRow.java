package org.dpdns.zerodep.flow.core.view.stats;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

/** 分组计数的一行（统计内部用：按两个键分组后的个数） */
@Data
@FieldNameConstants
public class CountRow {
    private String key1;
    private String key2;
    private Long id;
    private String name;
    private Long count;
}
