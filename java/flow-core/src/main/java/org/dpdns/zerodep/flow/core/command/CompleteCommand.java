package org.dpdns.zerodep.flow.core.command;

import org.dpdns.zerodep.flow.core.data.FormValues;

/**
 * 处理一个待办（0006 的 3.3）。待办还在收件箱里时，候选人处理就等于先认领。
 *
 * @param workItemId 待办
 * @param outcome    处理结果：节点上写的一个结果（按钮）；节点没写结果时不填
 * @param comment    意见
 * @param form       表单的值：只取这个节点上能改的字段，其余用存着的值
 */
public record CompleteCommand(Long workItemId, String outcome, String comment, FormValues form) {
    /** 补上空的表单 */
    public CompleteCommand {
        form = form == null ? FormValues.empty() : form;
    }

    /** 只给结果 */
    public static CompleteCommand of(Long workItemId, String outcome) {
        return new CompleteCommand(workItemId, outcome, null, FormValues.empty());
    }
}
