package org.dpdns.zerodep.flow.core.view;

import lombok.Data;

import java.time.Instant;

/**
 * 待办（{@code flow_work_item} 的一行，按 {@code select(WorkItemVO.class)} 读出）：引擎读到的样子，也是命令与查询返回给前端的形状。
 * 状态与落到谁的取值见 {@link org.dpdns.zerodep.flow.core.entity.WorkItemEntity} 上的常量。
 */
@Data
public class WorkItemVO {
    private Long id;
    private Long instanceId;
    private String processKey;
    /** 紧急程度（从实例抄来） */
    private int priority;
    private String node;
    /** 节点名称（快照） */
    private String nodeName;
    /** 这个节点第几轮 */
    private int round;
    /** 会签时是第几个参与人（依次会签按它的先后）；不是会签时为 null */
    private Integer signSeq;
    /** 票签的权重；不是票签时为 null */
    private Integer weight;
    /** {@code open}、{@code waiting}（等委派、加签的人办完）、{@code done}、{@code canceled} */
    private String state;
    /** {@code pool}（收件箱里等认领）、{@code claimed}、{@code direct}、{@code designated} */
    private String assignment;
    /** 怎样来的：{@code start}、{@code forward}、{@code return}…… */
    private String arrival;
    /** 收件箱是哪种：{@code role}、{@code department}、接入方扩展的种类；不在收件箱里时为 null */
    private String inboxKind;
    private Long inboxId;
    /** 当前处理人；在收件箱里等认领时为 null */
    private Long assigneeId;
    private String assigneeName;
    /** 从谁那里来：转办、委派、加签的人 */
    private Long fromUserId;
    private String fromUserName;
    /** 委派、加签生成的待办指向原来的待办 */
    private Long parentItemId;
    /** {@code delegate} 委派、{@code before} 前加签、{@code after} 后加签、{@code parallel} 并加 */
    private String addMode;
    private Instant createdAt;
    private Instant claimedAt;
    private Instant dueAt;
    private String outcome;
    private String comment;
    private Long completedBy;
    private String completedByName;
    private Instant completedAt;
}
