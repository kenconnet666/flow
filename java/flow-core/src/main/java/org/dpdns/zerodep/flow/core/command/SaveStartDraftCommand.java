package org.dpdns.zerodep.flow.core.command;

import lombok.Data;
import org.dpdns.zerodep.flow.core.data.FormValues;

/**
 * 发起前存草稿（0006 的 3.1 saveStartDraft）：填了一半先存，下次接着填，再用 {@link StartCommand#getDraftId()} 发起。
 * 草稿是一个状态为 draft 的实例（有编号，附件可以先挂上去），不进流程、不记流转记录，只有发起人自己看得到。
 */
@Data
public class SaveStartDraftCommand implements FlowCommand {
    /** 改哪份草稿；第一次存时为 null，存完从结果里拿编号 */
    private final Long draftId;
    /** 流程标识；改已有的草稿时可以不写 */
    private final String key;
    /** 表单的值：只收开始节点上能改的字段，按类型、长度、范围查，不查必填；整份替换上次存的 */
    private final FormValues form;
    /** 业务单据编号 */
    private final String businessKey;
}
