package org.dpdns.zerodep.flow.core.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.OrderByProperty;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Navigate;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.enums.RelationTypeEnum;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.dpdns.zerodep.flow.core.entity.proxy.WorkItemEntityProxy;

import java.time.Instant;
import java.util.List;

/**
 * 待办（{@code flow_work_item}）：一张表加状态，已办也查这张表。状态的变化都写成带条件的更新（改到 0 行就是别人先改了），
 * 所以认领先到先得、重复提交挡得住。读出来给引擎与前端的是 {@link org.dpdns.zerodep.flow.core.view.WorkItemVO}。
 */
@Getter
@Setter
@FieldNameConstants
@Table("flow_work_item")
@EntityProxy
public class WorkItemEntity implements ProxyEntityAvailable<WorkItemEntity, WorkItemEntityProxy>, Tenanted {
    /** 等着处理 */
    public static final String OPEN = "open";
    /** 等委派、加签的人办完（办完后回到原来的处理人） */
    public static final String WAITING = "waiting";
    /** 处理完 */
    public static final String DONE = "done";
    /** 取消（撤回、实例结束） */
    public static final String CANCELED = "canceled";
    /** 还没办完的：打开着的，和等委派、加签的人办完的 */
    public static final List<String> PENDING = List.of(OPEN, WAITING);

    /** 收件箱里等认领 */
    public static final String POOL = "pool";
    /** 认领了 */
    public static final String CLAIMED = "claimed";
    /** 直接落到人 */
    public static final String DIRECT = "direct";
    /** 收件箱负责人指定的承办人（无人认领超时后） */
    public static final String DESIGNATED = "designated";

    @Column(primaryKey = true, generatedKey = true)
    private Long id;
    private Long tenantId;
    private Long instanceId;
    private String processKey;
    private Integer priority;
    private String node;
    private String nodeName;
    /** 这个节点第几轮 */
    private Integer round;
    /** 会签时是第几个参与人 */
    private Integer signSeq;
    /** 票签的权重 */
    private Integer weight;
    private String state;
    private String assignment;
    private String arrival;
    private String inboxKind;
    private Long inboxId;
    private Long assigneeId;
    private String assigneeName;
    private Long fromUserId;
    private String fromUserName;
    /** 委派、加签生成的待办指向原来的待办 */
    private Long parentItemId;
    private String addMode;
    private Instant createdAt;
    private Instant claimedAt;
    private Instant dueAt;
    private Integer remindCount;
    private Instant lastRemindedAt;
    private Boolean needsDesignation;
    private Long designatorId;
    private String outcome;
    private String comment;
    private Long completedBy;
    private String completedByName;
    private Instant completedAt;
    private Integer rev;

    // ───────── 关系 ─────────

    /** 所在的实例 */
    @Navigate(value = RelationTypeEnum.ManyToOne, selfProperty = Fields.instanceId, targetProperty = InstanceEntity.Fields.id)
    private InstanceEntity instance;

    /** 打开着时谁能处理、谁被排除 */
    @Navigate(value = RelationTypeEnum.OneToMany, selfProperty = Fields.id, targetProperty = CandidateEntity.Fields.workItemId)
    private List<CandidateEntity> candidates;

    /** 委派、加签前原来的待办；不是这样生成的为 null */
    @Navigate(value = RelationTypeEnum.ManyToOne, selfProperty = Fields.parentItemId, targetProperty = Fields.id)
    private WorkItemEntity parent;

    /** 委派、加签从它生成的待办，按生成的先后 */
    @Navigate(value = RelationTypeEnum.OneToMany, selfProperty = Fields.id, targetProperty = Fields.parentItemId,
        orderByProps = {@OrderByProperty(property = Fields.id)})
    private List<WorkItemEntity> children;
}
