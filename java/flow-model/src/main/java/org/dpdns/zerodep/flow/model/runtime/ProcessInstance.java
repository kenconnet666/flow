package org.dpdns.zerodep.flow.model.runtime;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 流程实例（docs/decisions/0006 第 4 节、0007 的 {@code flow_instance}）：命令与查询返回给前端的形状，两个后端相同。
 * 运行状态（并行令牌、汇合计数）是引擎内部的，不在这里；流程变量只给管理员看，另外查。
 *
 * @param id          编号
 * @param processKey  流程标识
 * @param version     按哪个版本走
 * @param businessKey 业务单据编号
 * @param title       实例标题
 * @param priority    紧急程度，0 是常规，越大越急
 * @param summary     待办列表的摘要列：{@code { ref }} 的名字 → 值
 * @param status      {@code active}、{@code completed}、{@code terminated}、{@code withdrawn}……
 * @param result      结束结果
 * @param current     正在处理的节点
 * @param startedBy   发起人
 * @param startedByName 发起人的名字（快照）
 * @param startedAt   发起时间
 * @param endedAt     结束时间
 * @param parentInstanceId 调用它的父实例（0005 的 4.2）；不是子流程时为 null
 */
public record ProcessInstance(Long id, String processKey, int version, String businessKey, String title, int priority,
                              Map<String, Object> summary, String status, String result, List<CurrentNode> current,
                              Long startedBy, String startedByName, Instant startedAt, Instant endedAt, Long parentInstanceId) {
    /** 实例的状态 */
    public static final String ACTIVE = "active";
    /** 管理员暂停：待办都不能处理，直到恢复 */
    public static final String SUSPENDED = "suspended";
    /** 正常走到结束节点 */
    public static final String COMPLETED = "completed";
    /** 处理结果是终止 */
    public static final String TERMINATED = "terminated";
    /** 发起人撤回 */
    public static final String WITHDRAWN = "withdrawn";

    /** 正在处理的一个节点 */
    public record CurrentNode(String node, String name) { }
}
