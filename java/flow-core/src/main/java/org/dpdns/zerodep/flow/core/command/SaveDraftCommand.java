package org.dpdns.zerodep.flow.core.command;

import lombok.Data;
import org.dpdns.zerodep.flow.core.data.FormValues;

/**
 * 暂存（0006 的 3.3 {@code saveDraft}）：填了一半先存，不提交、不推进。存在待办上，再打开时接着填；处理完了就不要了。
 * 只按清单查值的类型、长度、范围，不查必填；只收这个节点上能改的字段。
 */
@Data
public class SaveDraftCommand implements FlowCommand {
    /** 待办（在收件箱里的先认领给我） */
    private final Long workItemId;
    /** 表单里改过的值 */
    private final FormValues form;
    /** 写了一半的意见 */
    private final String comment;

    public SaveDraftCommand(Long workItemId, FormValues form, String comment) {
        form = form == null ? FormValues.empty() : form;
        this.workItemId = workItemId;
        this.form = form;
        this.comment = comment;
    }
}
