package org.dpdns.zerodep.flow.core.view;

import com.easy.query.core.annotation.Column;
import lombok.Data;
import org.dpdns.zerodep.flow.core.db.JsonColumns;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity.CurrentNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 流程实例给前端的形状（按 {@code select(InstanceVO.class)} 读出）：运行状态与流程变量是引擎内部的，不在这里。
 * JSON 列在这里也要写上转换器（easy-query 按 VO 自己的列读）。状态的取值见 {@link org.dpdns.zerodep.flow.core.entity.InstanceEntity} 上的常量。
 */
@Data
public class InstanceVO {
    private Long id;
    private String processKey;
    private int version;
    private String businessKey;
    private String title;
    private int priority;
    /** 待办列表的摘要列：{ 引用名: 值 } */
    @Column(conversion = JsonColumns.ObjectJson.class)
    private Map<String, Object> summary;
    private String status;
    private String result;
    /** 正在处理的节点 */
    @Column(conversion = JsonColumns.CurrentNodesJson.class)
    private List<CurrentNode> current;
    private Long startedBy;
    private String startedByName;
    private Instant startedAt;
    private Instant endedAt;
    /** 调用它的父实例；不是子流程时为 null */
    private Long parentInstanceId;
}
