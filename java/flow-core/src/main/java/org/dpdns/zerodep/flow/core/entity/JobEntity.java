package org.dpdns.zerodep.flow.core.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Navigate;
import com.easy.query.core.annotation.OrderByProperty;
import com.easy.query.core.enums.RelationTypeEnum;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.dpdns.zerodep.flow.core.db.JsonColumns;
import org.dpdns.zerodep.flow.core.entity.proxy.JobEntityProxy;

import java.time.Instant;
import java.util.Map;

/**
 * 作业（{@code flow_job}，0007 的 3.5）：定时节点、待办的超时、提醒、无人认领、异步动作这些到时候要做的事。
 * 领取用普通读加带条件的更新（0001 第 4 条）；成功的直接删掉，表里只留等着的和失败的。
 */
@Getter
@Setter
@FieldNameConstants
@Table("flow_job")
@EntityProxy
public class JobEntity implements ProxyEntityAvailable<JobEntity, JobEntityProxy>, Tenanted {
    /** 等着做 */
    public static final String PENDING = "pending";
    /** 重试用完了 */
    public static final String FAILED = "failed";

    @Column(primaryKey = true, generatedKey = true)
    private Long id;
    /** 做作业时切换到它 */
    private Long tenantId;
    /** {@code timer}、{@code overdue}、{@code remind}、{@code unclaimed}、{@code action}……或接入方注册的种类 */
    private String kind;
    private Long instanceId;
    /** 相关的待办；定时节点的作业为 null */
    private Long workItemId;
    private Instant dueAt;
    /** 作业自己的数据 */
    @Column(conversion = JsonColumns.ObjectJson.class)
    private Map<String, Object> payload;
    private String state;
    /** 失败过几次 */
    private Integer attempts;
    /** 最多试几次 */
    private Integer maxAttempts;
    private String lockedBy;
    private Instant lockedUntil;
    private String lastError;
    private Instant createdAt;

    // ───────── 关系 ─────────

    /** 所在的实例 */
    @Navigate(value = RelationTypeEnum.ManyToOne, selfProperty = Fields.instanceId, targetProperty = InstanceEntity.Fields.id)
    private InstanceEntity instance;

    /** 相关的待办；定时节点、自动节点的作业为 null */
    @Navigate(value = RelationTypeEnum.ManyToOne, selfProperty = Fields.workItemId, targetProperty = WorkItemEntity.Fields.id)
    private WorkItemEntity workItem;
}
