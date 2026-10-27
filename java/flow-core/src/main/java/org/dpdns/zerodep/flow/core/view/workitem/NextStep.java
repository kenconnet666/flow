package org.dpdns.zerodep.flow.core.view.workitem;

import lombok.Data;

import java.util.List;

/**
 * 预测的下一步（0006 的 predict）：选了某个处理结果（或发起）后，按现在的值穿过网关与抄送，流程会停在哪里。
 * 只用定义与现在的值算，不跑自动节点：停在自动处理、调用流程这类节点上时，它后面怎么走要等它的结果。
 */
@Data
public class NextStep {
    /** 节点 */
    private final String node;
    /** 节点名称 */
    private final String nodeName;
    /** 节点类型：task、service、call、receive、timer、end…… */
    private final String type;
    /** 看得出来的处理人名字（指定的人、发起人、同某节点的上一位处理人）；要选人、要解析器算、进收件箱的不在这里 */
    private final List<String> handlers;
    /** 会进收件箱（角色、部门、扩展的种类）等人认领 */
    private final boolean inbox;
    /** 要在提交时选处理人（{@code pick}）；不用选时为 null */
    private final PickView pick;
}
