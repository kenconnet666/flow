package org.dpdns.zerodep.flow.core.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 待办上的暂存（{@code flow_work_item.draft}）：处理人填了一半先存下的表单与意见，再打开时接着填，处理完就清掉。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkItemDraft {
    /** 改过的表单字段（只含这个节点上能改的） */
    private FormValues form;
    /** 写了一半的意见；没写时为 null */
    private String comment;
    /** 暂存的时间 */
    private Instant savedAt;
}
