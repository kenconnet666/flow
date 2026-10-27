package org.dpdns.zerodep.flow.core.db;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.basic.jdbc.conn.ConnectionManager;
import com.easy.query.core.basic.jdbc.tx.Transaction;
import com.easy.query.core.configuration.QueryConfiguration;
import com.easy.query.core.context.QueryRuntimeContext;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * 引擎用接入方建好的 {@link EasyEntityQuery}（方言、数据源、事务都由接入方定；Spring 里就是 easy-query starter 的那个），
 * 组装引擎时把引擎的表要的东西装上去：
 * <ul>
 *   <li>时间：{@link Instant} 按 UTC 的 DATETIME 读写（{@link InstantUtcHandler}；接入方已经为 Instant 注册过处理器时不换）；</li>
 *   <li>JSON 列的转换器（{@link JsonColumns}）；</li>
 *   <li>租户与修改时间的拦截器（{@link FlowInterceptors}），只作用于引擎的表。</li>
 * </ul>
 * 表与列的名字按 easy-query 默认的下划线命名（{@code processKey} ↔ {@code process_key}）。
 */
public final class FlowEasyQuery {
    private FlowEasyQuery() { }

    /**
     * 装上引擎的表要的东西。
     *
     * @param tenant 当前租户，不用多租户时是 {@code () -> 0L}
     */
    public static void install(EasyEntityQuery db, Supplier<Long> tenant, Clock clock) {
        QueryRuntimeContext runtime = db.getRuntimeContext();
        runtime.getJdbcTypeHandlerManager().appendHandler(Instant.class, new InstantUtcHandler(), false);
        QueryConfiguration config = runtime.getQueryConfiguration();
        config.applyValueConverter(new JsonColumns.FormValuesJson());
        config.applyValueConverter(new JsonColumns.VariablesJson());
        config.applyValueConverter(new JsonColumns.RunStateJson());
        config.applyValueConverter(new JsonColumns.CurrentNodesJson());
        config.applyValueConverter(new JsonColumns.ObjectJson());
        config.applyInterceptor(new FlowInterceptors.Tenant(tenant));
        config.applyInterceptor(new FlowInterceptors.Touch(clock));
    }

    /** 在事务里做：外面已有事务（Spring 的、或接入方自己开的）时加入，否则开一个，做完提交、出错回滚 */
    public static <T> T transaction(EasyEntityQuery db, Supplier<T> work) {
        ConnectionManager connections = db.getRuntimeContext().getConnectionManager();
        if (connections.currentThreadInTransaction()) return work.get();
        try (Transaction tx = db.beginTransaction()) {
            T result = work.get();
            tx.commit();
            return result;
        }
    }
}
