package org.dpdns.zerodep.flow.core.store;

import org.dpdns.zerodep.flow.model.runtime.FlowEvent;

import java.util.List;

/**
 * 流转记录（0007 的 {@code flow_event}），只追加。默认实现是 {@link QueryStores}；量大时可以换成按月分表或存到别处。
 */
public interface EventStore {
    /** 追加几条 */
    void append(List<FlowEvent> events);

    /** 一个实例的记录，按先后 */
    List<FlowEvent> timeline(String instanceId);
}
