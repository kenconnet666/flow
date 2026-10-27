package org.dpdns.zerodep.flow.core.view.instance;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 外部参与方看的进度（0002 §9"外部参与方视图"）：现在在哪个环节、谁在处理、预计什么时候答复。不含表单、流转记录与内部的处理人编号。
 */
@Data
public class ProgressView {
    private String title;
    private String processKey;
    /** 实例的状态 */
    private String status;
    private Instant startedAt;
    /** 结束的时间；没结束时为 null */
    private Instant endedAt;
    /** 正在处理的环节 */
    private List<Step> steps;

    /** 一个正在处理的环节 */
    @Data
    public static class Step {
        private String nodeName;
        /** 谁在处理；还在收件箱里没人认领时为 null */
        private String handlerName;
        private Instant arrivedAt;
        /** 预计答复的时间（截止时间）；没有时限时为 null */
        private Instant dueAt;
    }
}
