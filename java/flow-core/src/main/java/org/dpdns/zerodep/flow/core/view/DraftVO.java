package org.dpdns.zerodep.flow.core.view;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/** 待办详情里我暂存过的：写了一半的意见与暂存的时间（暂存的表单值已经并进待办详情的表单） */
@Getter
@AllArgsConstructor
public class DraftVO {
    /** 写了一半的意见；没写时为 null */
    private String comment;
    private Instant savedAt;
}
