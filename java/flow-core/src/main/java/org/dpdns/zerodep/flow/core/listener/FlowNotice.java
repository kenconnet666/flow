package org.dpdns.zerodep.flow.core.listener;

import lombok.Data;
import org.dpdns.zerodep.flow.core.view.FlowEventVO;
import org.dpdns.zerodep.flow.core.view.InstanceVO;

import java.util.Map;

/** 给监听的一条通知：流转记录、它所在的实例（写记录那一刻的样子），以及节点上的业务附加信息。 */
@Data
public class FlowNotice {
    /** 流转记录（带数据库给的编号） */
    private final FlowEventVO event;
    /** 实例 */
    private final InstanceVO instance;
    /** 事件所在节点的 {@code meta}（0005 的节点共有字段，引擎原样保存不解释）；不在节点上的事件是空的 */
    private final Map<String, Object> nodeMeta;

    public FlowNotice(FlowEventVO event, InstanceVO instance, Map<String, Object> nodeMeta) {
        nodeMeta = nodeMeta == null ? Map.of() : nodeMeta;
        this.event = event;
        this.instance = instance;
        this.nodeMeta = nodeMeta;
    }
}
