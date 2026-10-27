package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

import java.util.Map;

/**
 * 外部事件（0005 的 4.3、0006 的 {@code signal}）：业务系统告诉引擎"某件事发生了"，停在等这个事件的等待节点上的实例往下走。
 * 按实例编号或业务单据编号找（给一个就行，都给时按实例编号）；实例不在等它时只记一笔、不报错，业务系统可以放心重发。
 */
@Data
public class SignalCommand implements FlowCommand {
    /** 实例 */
    private final Long instanceId;
    /** 业务单据编号：同一个单号的几个实例都在等时都往下走 */
    private final String businessKey;
    /** 事件名（等待节点的 {@code event}） */
    private final String event;
    /** 带回的结果（等待节点写了 {@code outcomes} 时必须是其中一个） */
    private final String outcome;
    /** 事件带的数据：节点的 {@code output} 把其中的值写进表单或流程变量 */
    private final Map<String, Object> data;

    public SignalCommand(Long instanceId, String businessKey, String event, String outcome, Map<String, Object> data) { data = data == null ? Map.of() : data;
        this.instanceId = instanceId;
        this.businessKey = businessKey;
        this.event = event;
        this.outcome = outcome;
        this.data = data;
    }
}
