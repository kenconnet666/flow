package org.dpdns.zerodep.flow.model.runtime;

import java.time.Instant;
import java.util.Map;

/**
 * 待办（0007 的 {@code flow_work_item}）：命令与查询返回给前端的形状，两个后端相同。收件箱里的待办另带实例的标题与摘要列。
 *
 * @param id           编号
 * @param instanceId   实例
 * @param processKey   流程标识
 * @param priority     紧急程度（从实例抄来）
 * @param node         节点
 * @param nodeName     节点名称（快照）
 * @param round        这个节点第几轮
 * @param state        {@code open}、{@code done}、{@code canceled}
 * @param assignment   {@code pool}（收件箱里等认领）、{@code claimed}、{@code direct}
 * @param arrival      怎样来的：{@code start}、{@code forward}……
 * @param inboxKind    收件箱是哪种：{@code role}、{@code department}、接入方扩展的种类；不在收件箱里时为 null
 * @param inboxId      收件箱是哪个角色、部门
 * @param assigneeId   当前处理人；在收件箱里等认领时为 null
 * @param assigneeName 处理人的名字（快照）
 * @param createdAt    到达时间
 * @param claimedAt    认领时间
 * @param dueAt        截止时间
 * @param outcome      处理结果
 * @param comment      意见
 * @param completedBy  谁处理的
 * @param completedByName 处理人的名字（快照）
 * @param completedAt  处理时间
 * @param title        实例标题：只在收件箱、已办这类列表里带上
 * @param summary      实例的摘要列：同上
 */
public record WorkItem(String id, String instanceId, String processKey, int priority, String node, String nodeName, int round,
                       String state, String assignment, String arrival, String inboxKind, String inboxId, String assigneeId,
                       String assigneeName, Instant createdAt, Instant claimedAt, Instant dueAt, String outcome, String comment,
                       String completedBy, String completedByName, Instant completedAt, String title, Map<String, Object> summary) {
    /** 等着处理 */
    public static final String OPEN = "open";
    /** 处理完 */
    public static final String DONE = "done";
    /** 取消（撤回、实例结束） */
    public static final String CANCELED = "canceled";

    /** 收件箱里等认领 */
    public static final String POOL = "pool";
    /** 认领了 */
    public static final String CLAIMED = "claimed";
    /** 直接落到人 */
    public static final String DIRECT = "direct";

    /** 带上实例的标题与摘要列（列表用） */
    public WorkItem withInstance(String title, Map<String, Object> summary) {
        return new WorkItem(id, instanceId, processKey, priority, node, nodeName, round, state, assignment, arrival, inboxKind, inboxId,
            assigneeId, assigneeName, createdAt, claimedAt, dueAt, outcome, comment, completedBy, completedByName, completedAt, title, summary);
    }
}
