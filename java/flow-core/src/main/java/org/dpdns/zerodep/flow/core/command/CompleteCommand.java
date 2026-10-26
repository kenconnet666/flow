package org.dpdns.zerodep.flow.core.command;

import java.util.List;
import java.util.Map;
import org.dpdns.zerodep.flow.core.data.FormValues;

/**
 * 处理一个待办（0006 的 3.3）。待办还在收件箱里时，候选人处理就等于先认领。
 *
 * @param workItemId 待办
 * @param outcome    处理结果：节点上写的一个结果（按钮）；节点没写结果时不填
 * @param comment    意见
 * @param form       表单的值：只取这个节点上能改的字段，其余用存着的值
 * @param picks      下一步要这一步选处理人的节点（0005 的 5.6 {@code pick}）→ 选的人
 * @param returnTarget 退回到哪个节点：结果是 {@code { "kind": "return", "target": "choose" }} 时必填，是待办详情里那个结果的 targets 之一
 */
public record CompleteCommand(Long workItemId, String outcome, String comment, FormValues form, Map<String, List<Long>> picks,
                              String returnTarget) implements FlowCommand {
    /** 补上空的表单与选人 */
    public CompleteCommand {
        form = form == null ? FormValues.empty() : form;
        picks = picks == null ? Map.of() : Map.copyOf(picks);
    }

    /** 只给结果 */
    public static CompleteCommand of(Long workItemId, String outcome) {
        return new CompleteCommand(workItemId, outcome, null, FormValues.empty(), Map.of(), null);
    }
}
