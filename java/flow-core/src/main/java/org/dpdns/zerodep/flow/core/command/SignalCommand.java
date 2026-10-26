package org.dpdns.zerodep.flow.core.command;

import java.util.Map;

/**
 * 外部事件（0005 的 4.3、0006 的 {@code signal}）：业务系统告诉引擎"某件事发生了"，停在等这个事件的等待节点上的实例往下走。
 * 按实例编号或业务单据编号找（给一个就行，都给时按实例编号）；实例不在等它时只记一笔、不报错，业务系统可以放心重发。
 *
 * @param instanceId  实例
 * @param businessKey 业务单据编号：同一个单号的几个实例都在等时都往下走
 * @param event       事件名（等待节点的 {@code event}）
 * @param outcome     带回的结果（等待节点写了 {@code outcomes} 时必须是其中一个）
 * @param data        事件带的数据：节点的 {@code output} 把其中的值写进表单或流程变量
 */
public record SignalCommand(Long instanceId, String businessKey, String event, String outcome, Map<String, Object> data) {
    public SignalCommand { data = data == null ? Map.of() : data; }
}
