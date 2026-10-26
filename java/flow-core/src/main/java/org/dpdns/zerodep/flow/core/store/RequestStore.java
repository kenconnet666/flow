package org.dpdns.zerodep.flow.core.store;

import java.time.Instant;
import java.util.Optional;

/**
 * 命令的幂等记录（0007 的 {@code flow_request}）：带请求编号的命令做成后记下响应，同一个编号再来时直接返回它。
 * 默认实现是 {@link org.dpdns.zerodep.flow.core.store.query.QueryStores}。
 */
public interface RequestStore {
    /**
     * 记下的一次请求。
     *
     * @param command  哪个命令
     * @param actorId  谁做的
     * @param response 第一次的响应
     */
    record StoredRequest(Long id, String requestId, String command, Long actorId, String response, Instant createdAt) { }

    /** 要写的一条（编号、租户由存储填） */
    record NewRequest(String requestId, String command, Long actorId, String response, Instant createdAt) { }

    /** 这个请求编号做过没有 */
    Optional<StoredRequest> find(String requestId);

    /** 记下；同一个编号并发写第二次时违反唯一约束，那个命令整个回滚 */
    void save(NewRequest request);
}
