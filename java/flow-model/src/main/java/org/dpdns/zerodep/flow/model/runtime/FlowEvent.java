package org.dpdns.zerodep.flow.model.runtime;

import java.time.Instant;
import java.util.Map;

/**
 * 一条流转记录（0006 第 5 节的事件、0007 的 {@code flow_event}），只追加。同一个实例的记录按 {@code instanceRev}、再按编号排出先后。
 *
 * @param id          编号
 * @param instanceId  实例
 * @param workItemId  相关的待办
 * @param type        {@code instance.started}、{@code workItem.completed}、{@code node.entered}……
 * @param node        节点
 * @param nodeName    节点名称（快照）
 * @param edge        走的连线
 * @param outcome     处理结果
 * @param instanceRev 写这条记录时实例的版本：同一步写的几条相同
 * @param actorId     操作人；引擎自己推进时为 null
 * @param actorName   操作人的名字（快照）
 * @param at          时间
 * @param comment     意见、原因
 * @param data        附加的数据；处理的记录带 {@link #OUTCOME_LABEL}（结果的名字，快照）
 */
public record FlowEvent(Long id, Long instanceId, Long workItemId, String type, String node, String nodeName, String edge,
                        String outcome, int instanceRev, Long actorId, String actorName, Instant at, String comment,
                        Map<String, Object> data) {
    /** data 里处理结果的名字（快照） */
    public static final String OUTCOME_LABEL = "outcomeLabel";

    /** 实例发起 */
    public static final String INSTANCE_STARTED = "instance.started";
    /** 发起人撤回实例 */
    public static final String INSTANCE_WITHDRAWN = "instance.withdrawn";
    /** 处理结果是终止 */
    public static final String INSTANCE_TERMINATED = "instance.terminated";
    /** 实例走到结束节点 */
    public static final String INSTANCE_FINISHED = "instance.finished";
    /** 生成了待办 */
    public static final String WORK_ITEM_CREATED = "workItem.created";
    /** 认领 */
    public static final String WORK_ITEM_CLAIMED = "workItem.claimed";
    /** 处理 */
    public static final String WORK_ITEM_COMPLETED = "workItem.completed";
    /** 取消 */
    public static final String WORK_ITEM_CANCELED = "workItem.canceled";
    /** 转办：data 里是交给谁（toUserId、toUserName） */
    public static final String WORK_ITEM_TRANSFERRED = "workItem.transferred";
    /** 委派：data 同上 */
    public static final String WORK_ITEM_DELEGATED = "workItem.delegated";
    /** 加签：data 里是加的人（users）与方式（mode） */
    public static final String SIGNERS_ADDED = "workItem.signersAdded";
    /** 减签 */
    public static final String SIGNERS_REMOVED = "workItem.signersRemoved";
    /** 委派、前加签的人都办完了，待办回到原来的处理人 */
    public static final String WORK_ITEM_RESUMED = "workItem.resumed";
    /** 退回收件箱 */
    public static final String WORK_ITEM_RELEASED = "workItem.released";
    /** 改派：data 同转办 */
    public static final String WORK_ITEM_REASSIGNED = "workItem.reassigned";
    /** 催办 */
    public static final String WORK_ITEM_REMINDED = "workItem.reminded";
    /** 留言 */
    public static final String INSTANCE_COMMENTED = "instance.commented";
    /** 暂停 */
    public static final String INSTANCE_SUSPENDED = "instance.suspended";
    /** 恢复 */
    public static final String INSTANCE_RESUMED = "instance.resumed";
    /** 管理员跳转：节点是跳到哪 */
    public static final String INSTANCE_JUMPED = "instance.jumped";
    /** 进入节点（含网关） */
    public static final String NODE_ENTERED = "node.entered";
    /** 离开节点 */
    public static final String NODE_LEFT = "node.left";
}
