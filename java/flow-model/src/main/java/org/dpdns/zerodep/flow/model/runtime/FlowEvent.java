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
    /** 催办；引擎按节点的 reminders、unclaimed 自动提醒时 data 里 auto 为 true，reason 是 dueSoon、overdue、unclaimed */
    public static final String WORK_ITEM_REMINDED = "workItem.reminded";
    /** 待办超时了（0005 的 5.3）：data 里是截止时间 dueAt；超时自动处理的随后另有一条处理的记录 */
    public static final String WORK_ITEM_OVERDUE = "workItem.overdue";
    /** 收件箱负责人指定了承办人：data 同转办（toUserId、toUserName） */
    public static final String WORK_ITEM_ASSIGNED = "workItem.assigned";
    /** 进了收件箱很久没人认领，转收件箱的负责人指定（unclaimed 的 assignByManager）：data 里是负责人 designatorId、designatorName */
    public static final String WORK_ITEM_UNCLAIMED = "workItem.unclaimed";
    /** 抄送（抄送节点或手动抄送）：data 里是收到的人 users 与名字 userNames，comment 是附言；手动抄送的操作人是抄送人 */
    public static final String INSTANCE_CC = "instance.cc";
    /**
     * 作业重试用完（异步的自动节点做不成）：data 里是作业 jobId、种类 kind 与原因 error；实例停在那个节点（{@code onFailure: "error"} 时整个暂停），
     * 管理员 retryJob、skipJob 或终止
     */
    public static final String JOB_FAILED = "job.failed";
    /** 管理员跳过失败的作业：outcome 是指定的结果，data 里是作业 jobId，comment 是原因 */
    public static final String JOB_SKIPPED = "job.skipped";
    /** 外部事件到了（0005 的 4.3）：data 里是事件名 event 与送来的数据 data，outcome 是带回的结果 */
    public static final String SIGNAL_RECEIVED = "signal.received";
    /** 外部事件到了但实例不在等它：只记一笔，不报错（业务系统可以放心重发） */
    public static final String SIGNAL_IGNORED = "signal.ignored";
    /** 等待节点等超时了：data 里是 dueAt；超时按结果往下走的随后另有记录 */
    public static final String NODE_OVERDUE = "node.overdue";
    /** 处理人撤回刚处理的一步：待办回到他手里，下一步生成的待办取消 */
    public static final String WORK_ITEM_WITHDRAWN = "workItem.withdrawn";
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
