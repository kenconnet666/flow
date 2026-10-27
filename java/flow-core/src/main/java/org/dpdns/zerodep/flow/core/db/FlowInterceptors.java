package org.dpdns.zerodep.flow.core.db;

import com.easy.query.core.basic.extension.interceptor.EntityInterceptor;
import com.easy.query.core.basic.extension.interceptor.PredicateFilterInterceptor;
import com.easy.query.core.basic.extension.interceptor.UpdateSetInterceptor;
import com.easy.query.core.expression.parser.core.base.ColumnSetter;
import com.easy.query.core.expression.parser.core.base.WherePredicate;
import com.easy.query.core.expression.sql.builder.EntityInsertExpressionBuilder;
import com.easy.query.core.expression.sql.builder.EntityUpdateExpressionBuilder;
import com.easy.query.core.expression.sql.builder.LambdaEntityExpressionBuilder;
import org.dpdns.zerodep.flow.core.entity.Tenanted;
import org.dpdns.zerodep.flow.core.entity.Touched;

import java.time.Clock;
import java.util.function.Supplier;

/**
 * 每张表都要做的事交给 easy-query 的拦截器，引擎里的查询就不再写：
 * <ul>
 *   <li>{@link Tenant}：带租户列的表（{@link Tenanted}）在查询、更新、删除里加 {@code tenant_id = 当前租户}，插入时填上；
 *       跨租户的读写（作业的领取、过期记录的清理）用 {@code noInterceptor(TENANT)} 关掉它；</li>
 *   <li>{@link Touch}：带修改时间的表（{@link Touched}）在插入、更新时填当前时刻。</li>
 * </ul>
 */
public final class FlowInterceptors {
    /** 租户拦截器的名字 */
    public static final String TENANT = "flow-tenant";
    /** 修改时间拦截器的名字 */
    public static final String TOUCH = "flow-touch";

    private FlowInterceptors() { }

    /** 租户：tenant 给出当前租户，不用多租户时是 {@code () -> 0L} */
    record Tenant(Supplier<Long> tenant) implements PredicateFilterInterceptor, EntityInterceptor {
        @Override public String name() { return TENANT; }

        @Override public boolean apply(Class<?> entityClass) { return Tenanted.class.isAssignableFrom(entityClass); }

        @Override
        public void configure(Class<?> entityClass, LambdaEntityExpressionBuilder builder, WherePredicate<Object> where) {
            where.eq("tenantId", tenant.get());
        }

        @Override
        public void configureInsert(Class<?> entityClass, EntityInsertExpressionBuilder builder, Object entity) {
            ((Tenanted) entity).setTenantId(tenant.get());
        }

        @Override
        public void configureUpdate(Class<?> entityClass, EntityUpdateExpressionBuilder builder, Object entity) { }
    }

    /** 修改时间 */
    record Touch(Clock clock) implements EntityInterceptor, UpdateSetInterceptor {
        @Override public String name() { return TOUCH; }

        @Override public boolean apply(Class<?> entityClass) { return Touched.class.isAssignableFrom(entityClass); }

        @Override
        public void configureInsert(Class<?> entityClass, EntityInsertExpressionBuilder builder, Object entity) {
            ((Touched) entity).setUpdatedAt(clock.instant());
        }

        @Override
        public void configureUpdate(Class<?> entityClass, EntityUpdateExpressionBuilder builder, Object entity) {
            ((Touched) entity).setUpdatedAt(clock.instant());
        }

        @Override
        public void configure(Class<?> entityClass, EntityUpdateExpressionBuilder builder, ColumnSetter<Object> setter) {
            setter.set("updatedAt", clock.instant());
        }
    }
}
