package org.dpdns.zerodep.flow.core.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Navigate;
import com.easy.query.core.annotation.OrderByProperty;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.enums.RelationTypeEnum;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.dpdns.zerodep.flow.core.data.RunState;
import org.dpdns.zerodep.flow.core.data.Variables;
import org.dpdns.zerodep.flow.core.db.JsonColumns;
import org.dpdns.zerodep.flow.core.entity.proxy.InstanceEntityProxy;
import org.dpdns.zerodep.flow.core.view.instance.InstanceVO;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 流程实例（{@code flow_instance}，列的含义见建表脚本与 0007）。推进时引擎改它的字段，最后整行写回；推进前先把 {@code rev} 加一锁住这一行。
 * 运行状态与流程变量只在引擎里用，给前端的是 {@link org.dpdns.zerodep.flow.core.view.instance.InstanceVO}。
 */
@Getter
@Setter
@FieldNameConstants
@Table("flow_instance")
@EntityProxy
public class InstanceEntity implements ProxyEntityAvailable<InstanceEntity, InstanceEntityProxy>, Tenanted, Touched {
    /** 进行中 */
    public static final String ACTIVE = "active";
    /** 管理员暂停：待办都不能处理，直到恢复 */
    public static final String SUSPENDED = "suspended";
    /** 正常走到结束节点 */
    public static final String COMPLETED = "completed";
    /** 处理结果是终止 */
    public static final String TERMINATED = "terminated";
    /** 发起人撤回 */
    public static final String WITHDRAWN = "withdrawn";

    @Column(primaryKey = true, generatedKey = true)
    private Long id;
    private Long tenantId;
    private String processKey;
    /** 按哪个发布的版本走（flow_definition.id） */
    private Long definitionId;
    private Integer version;
    private String businessKey;
    private String title;
    private Integer priority;
    /** 待办列表的摘要列：{ 引用名: 值 } */
    @Column(conversion = JsonColumns.ObjectJson.class)
    private Map<String, Object> summary;
    private String status;
    private String result;
    /** 正在处理的节点 */
    @Column(conversion = JsonColumns.CurrentNodesJson.class)
    private List<CurrentNode> current;
    @Column(conversion = JsonColumns.VariablesJson.class)
    private Variables variables;
    /** 运行状态，引擎内部用 */
    @Column(conversion = JsonColumns.RunStateJson.class)
    private RunState state;
    private Long startedBy;
    private String startedByName;
    private Instant startedAt;
    private Instant endedAt;
    /** 调用它的父实例；不是子流程时为 null */
    private Long parentInstanceId;
    private String parentNode;
    private Integer parentRound;
    /** 最外层的实例；不是子流程时为 null */
    private Long rootInstanceId;
    private Integer rev;
    private Instant updatedAt;

    // ───────── 关系 ─────────

    /** 按的那个发布版本 */
    @Navigate(value = RelationTypeEnum.ManyToOne, selfProperty = Fields.definitionId, targetProperty = ProcessVersionEntity.Fields.id)
    private ProcessVersionEntity definition;

    /** 全部待办，按到达的先后 */
    @Navigate(value = RelationTypeEnum.OneToMany, selfProperty = Fields.id, targetProperty = WorkItemEntity.Fields.instanceId,
        orderByProps = {@OrderByProperty(property = WorkItemEntity.Fields.createdAt), @OrderByProperty(property = WorkItemEntity.Fields.id)})
    private List<WorkItemEntity> workItems;

    /** 流转记录，按先后 */
    @Navigate(value = RelationTypeEnum.OneToMany, selfProperty = Fields.id, targetProperty = EventEntity.Fields.instanceId,
        orderByProps = {@OrderByProperty(property = EventEntity.Fields.instanceRev), @OrderByProperty(property = EventEntity.Fields.id)})
    private List<EventEntity> events;

    /** 表单的值；流程没有表单时为 null */
    @Navigate(value = RelationTypeEnum.OneToOne, selfProperty = Fields.id, targetProperty = FormDataEntity.Fields.instanceId)
    private FormDataEntity formData;

    /** 调用它的父实例；不是子流程时为 null */
    @Navigate(value = RelationTypeEnum.ManyToOne, selfProperty = Fields.parentInstanceId, targetProperty = Fields.id)
    private InstanceEntity parent;

    /** 调用流程发起的子实例，按发起的先后 */
    @Navigate(value = RelationTypeEnum.OneToMany, selfProperty = Fields.id, targetProperty = Fields.parentInstanceId,
        orderByProps = {@OrderByProperty(property = Fields.id)})
    private List<InstanceEntity> children;

    /** 正在处理的一个节点 */
    @Data
    public static class CurrentNode {
        private final String node;
        private final String name;
    }

    /** 结束：状态、结果、结束时间，正在处理的节点清空 */
    public void end(String nextStatus, String nextResult, Instant at) {
        status = nextStatus;
        result = nextResult;
        endedAt = at;
        current = List.of();
    }

}
