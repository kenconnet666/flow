package org.dpdns.zerodep.flow.core.listener;

import org.dpdns.zerodep.flow.model.runtime.FlowEvent;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance;

import java.util.Map;

/**
 * 给监听的一条通知：流转记录、它所在的实例（写记录那一刻的样子），以及节点上的业务附加信息。
 *
 * @param event    流转记录（带数据库给的编号）
 * @param instance 实例
 * @param nodeMeta 事件所在节点的 {@code meta}（0005 的节点共有字段，引擎原样保存不解释）；不在节点上的事件是空的
 */
public record FlowNotice(FlowEvent event, ProcessInstance instance, Map<String, Object> nodeMeta) {
    public FlowNotice {
        nodeMeta = nodeMeta == null ? Map.of() : nodeMeta;
    }
}
