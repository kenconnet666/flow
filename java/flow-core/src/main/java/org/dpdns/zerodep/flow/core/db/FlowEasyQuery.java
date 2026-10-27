package org.dpdns.zerodep.flow.core.db;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.basic.jdbc.conn.ConnectionManager;
import com.easy.query.core.basic.jdbc.tx.Transaction;
import com.easy.query.core.configuration.QueryConfiguration;
import com.easy.query.core.context.QueryRuntimeContext;

import java.sql.Connection;
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
public class FlowEasyQuery {
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
        config.applyValueConverter(new JsonColumns.DraftJson());
        config.applyInterceptor(new FlowInterceptors.Tenant(tenant));
        config.applyInterceptor(new FlowInterceptors.Touch(clock));
    }

    /**
     * 在事务里做：外面已有事务（Spring 的、或接入方自己开的）时加入，否则开一个，做完提交、出错回滚。
     * <p>隔离级别要是读已提交（Camunda 等引擎也这样要求）：命令先锁实例行再读会签的兄弟待办、表单的值；MySQL 默认的可重复读下，
     * 锁之前哪怕读过一次，之后读到的都是那一刻的快照，看不到刚提交的别人的处理（几个人同时会签同意，实例停住不走）。
     * 自己开的事务直接用读已提交；加入外面的事务时核对一遍，不是读已提交就报错（接入方的 {@code @Transactional} 要写
     * {@code isolation = Isolation.READ_COMMITTED}，或者库、连接池默认设成它）。
     */
    public static <T> T transaction(EasyEntityQuery db, Supplier<T> work) {
        ConnectionManager connections = db.getRuntimeContext().getConnectionManager();
        if (connections.currentThreadInTransaction()) {
            requireReadCommitted(db);
            return work.get();
        }
        try (Transaction tx = db.beginTransaction(Connection.TRANSACTION_READ_COMMITTED)) {
            T result = work.get();
            tx.commit();
            return result;
        }
    }

    /** 当前事务的隔离级别（MySQL 的会话变量，事务开始时连接设的级别也反映在这里） */
    private static void requireReadCommitted(EasyEntityQuery db) {
        String level = db.sqlQuery("SELECT @@transaction_isolation", String.class).get(0);
        if (!"READ-COMMITTED".equals(level)) {
            throw new IllegalStateException("flow commands need a READ COMMITTED transaction, but the surrounding transaction is " + level
                + "; use @Transactional(isolation = Isolation.READ_COMMITTED) or make READ COMMITTED the connection default");
        }
    }
}
