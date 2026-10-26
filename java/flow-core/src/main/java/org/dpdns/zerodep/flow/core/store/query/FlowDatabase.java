package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.store.query.FlowTables.Tenanted;
import org.dpdns.zerodep.flow.core.store.query.FlowTables.Touched;
import org.dpdns.zerodep.query.Sql;
import org.dpdns.zerodep.query.exec.ConnectionProvider;
import org.dpdns.zerodep.query.exec.Database;

import java.time.Clock;
import java.util.function.Supplier;

/**
 * 引擎自己的 query 数据库实例：连接由接入方给（DataSource，或 Spring 里带事务管理器的连接），配置由引擎定。
 * 把每张表都要做的事交给 query 统一做，存储的代码里就不再写：
 * <ul>
 *   <li>租户：带租户列的表（{@link Tenanted}）在查询、更新、删除里自动加 {@code tenant_id = 当前租户}，插入时自动填；</li>
 *   <li>修改时间：带修改时间的表（{@link Touched}）在插入、更新时自动填当前时刻。</li>
 * </ul>
 */
public final class FlowDatabase {
    private FlowDatabase() { }

    /** 在这些连接上建引擎用的数据库实例；tenant 给出当前租户，不用多租户时是 {@code () -> ""} */
    public static Database create(ConnectionProvider connections, Supplier<String> tenant, Clock clock) {
        return Sql.database(connections, config -> config
            .filter("flow-tenant", Tenanted.class, t -> t.tenantId().eq(tenant.get()))
            .onInsert(Tenanted.class, Tenanted::tenantId, tenant)
            .onInsert(Touched.class, Touched::updatedAt, clock::instant)
            .onUpdate(Touched.class, Touched::updatedAt, clock::instant));
    }
}
