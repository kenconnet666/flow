package org.dpdns.zerodep.flow.model.runtime;

import java.time.Instant;

/**
 * 待办（0007 的 {@code flow_work_item} 的一行）：命令与查询返回给前端的形状，两个后端相同。收件箱的一行是 {@link TodoItem}（另带实例的标题与摘要列）。
 *
 * @param id           编号（数据库自增）
 * @param instanceId   实例
 * @param processKey   流程标识
 * @param priority     紧急程度（从实例抄来）
 * @param node         节点
 * @param nodeName     节点名称（快照）
 * @param round        这个节点第几轮
 * @param signSeq      会签时是第几个参与人（依次会签按它的先后）；不是会签时为 null
 * @param weight       票签的权重；不是票签时为 null
 * @param state        {@code open}、{@code waiting}（等委派、加签的人办完）、{@code done}、{@code canceled}
 * @param assignment   {@code pool}（收件箱里等认领）、{@code claimed}、{@code direct}
 * @param arrival      怎样来的：{@code start}、{@code forward}……
 * @param inboxKind    收件箱是哪种：{@code role}、{@code department}、接入方扩展的种类；不在收件箱里时为 null
 * @param inboxId      收件箱是哪个角色、部门
 * @param assigneeId   当前处理人；在收件箱里等认领时为 null
 * @param assigneeName 处理人的名字（快照）
 * @param fromUserId   从谁那里来：转办、委派、加签的人
 * @param fromUserName 那个人的名字（快照）
 * @param parentItemId 委派、加签生成的待办指向原来的待办
 * @param addMode      怎样加进来的：{@code delegate} 委派、{@code before} 前加签、{@code after} 后加签、{@code parallel} 并加
 * @param createdAt    到达时间
 * @param claimedAt    认领时间
 * @param dueAt        截止时间
 * @param outcome      处理结果
 * @param comment      意见
 * @param completedBy  谁处理的
 * @param completedByName 处理人的名字（快照）
 * @param completedAt  处理时间
 */
public record WorkItem(Long id, Long instanceId, String processKey, int priority, String node, String nodeName, int round,
                       Integer signSeq, Integer weight,
                       String state, String assignment, String arrival, String inboxKind, Long inboxId,
                       Long assigneeId, String assigneeName,
                       Long fromUserId, String fromUserName, Long parentItemId, String addMode,
                       Instant createdAt, Instant claimedAt, Instant dueAt,
                       String outcome, String comment, Long completedBy, String completedByName, Instant completedAt) {
    /** 等着处理 */
    public static final String OPEN = "open";
    /** 等委派、加签的人办完（办完后回到原来的处理人） */
    public static final String WAITING = "waiting";
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
    /** 收件箱负责人指定的承办人（无人认领超时后） */
    public static final String DESIGNATED = "designated";
}
