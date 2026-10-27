package org.dpdns.zerodep.flow.core.command;

import lombok.Data;
import org.dpdns.zerodep.flow.core.engine.state.Variables;
import org.dpdns.zerodep.flow.core.spi.FormValues;

/**
 * 管理员改数据（0002 §6"改处理人、改变量"）：改流程变量、表单的值，修正录错的数据。改前改后的值都记进流转记录。
 * 只改写了的名字；值写 null 是清空。
 */
@Data
public class ChangeDataCommand implements FlowCommand {
    private final Long instanceId;
    /** 要改的流程变量 */
    private final Variables variables;
    /** 要改的表单字段（按清单的字段类型读） */
    private final FormValues form;
    /** 原因（必填） */
    private final String reason;

    public ChangeDataCommand(Long instanceId, Variables variables, FormValues form, String reason) {
        this.instanceId = instanceId;
        this.variables = variables == null ? Variables.empty() : variables;
        this.form = form == null ? FormValues.empty() : form;
        this.reason = reason;
    }
}
