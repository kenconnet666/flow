package org.dpdns.zerodep.flow.core.view;

import com.easy.query.core.annotation.Column;
import lombok.Data;
import org.dpdns.zerodep.flow.core.db.JsonColumns;

import java.time.Instant;
import java.util.Map;

/**
 * 一条流转记录（按 {@code select(FlowEventVO.class)} 读出）：给前端的流转记录与监听收到的事件。
 * 事件的种类见 {@link org.dpdns.zerodep.flow.core.entity.EventEntity} 上的常量。
 */
@Data
public class FlowEventVO {
    private Long id;
    private Long instanceId;
    private Long workItemId;
    private String type;
    private String node;
    /** 节点名称（快照） */
    private String nodeName;
    private String edge;
    private String outcome;
    /** 写这条记录时实例的版本：同一实例的记录按它、再按编号排出先后 */
    private int instanceRev;
    /** 操作人；引擎自己推进时为 null */
    private Long actorId;
    private String actorName;
    private Instant at;
    private String comment;
    @Column(conversion = JsonColumns.ObjectJson.class)
    private Map<String, Object> data;
}
