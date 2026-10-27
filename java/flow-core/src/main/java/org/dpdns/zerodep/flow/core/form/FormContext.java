package org.dpdns.zerodep.flow.core.form;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.dpdns.zerodep.flow.core.identity.Actor;

/** 调表单后端代码时给它的：谁、在哪个实例的哪个节点、是不是发起 */
@Getter
@AllArgsConstructor
public class FormContext {
    /** 操作人：接入方自己的用户类型，按需要强转 */
    private final Actor actor;
    /** 流程标识 */
    private final String processKey;
    /** 实例编号；发起页打开时（load）还没有实例，为 null */
    private final Long instanceId;
    /** 当前节点：发起时是开始节点，处理时是待办所在的人工节点；调接口时为 null */
    private final String node;
    /** 是不是发起 */
    private final boolean start;
}
