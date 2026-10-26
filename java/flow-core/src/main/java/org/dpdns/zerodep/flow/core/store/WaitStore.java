package org.dpdns.zerodep.flow.core.store;

import java.time.Instant;
import java.util.List;

/**
 * 等外部事件（0007 的 {@code flow_wait}）：实例停在等待节点（0005 的 {@code receive}）上时一行。默认实现是
 * {@link org.dpdns.zerodep.flow.core.store.query.QueryStores}。
 */
public interface WaitStore {
    /**
     * 一行等待。
     *
     * @param businessKey 实例的业务单据编号；没有时为 null
     */
    record Wait(Long id, Long instanceId, String node, String event, String businessKey, Instant createdAt) { }

    /** 要写的一行（编号、租户由存储填） */
    record NewWait(Long instanceId, String node, String event, String businessKey, Instant createdAt) { }

    /** 开始等 */
    void insert(NewWait wait);

    /** 这个实例在等这个事件的 */
    List<Wait> ofInstance(Long instanceId, String event);

    /** 业务单据编号是它、在等这个事件的（可能是几个实例） */
    List<Wait> ofBusinessKey(String businessKey, String event);

    /** 不再等（事件到了、超时了）：带条件地删掉，删到了返回 true（并发送来的同一个事件只有一个删得到） */
    boolean remove(Long waitId);

    /** 实例结束了：它的等待都删掉 */
    void removeForInstance(Long instanceId);
}
