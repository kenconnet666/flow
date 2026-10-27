package org.dpdns.zerodep.flow.core.engine;

import java.util.function.Supplier;

/**
 * 当前租户的临时切换：工作进程替所有租户做作业（{@link JobRunner}），做每个作业时切到它的租户，
 * easy-query 的租户拦截器（{@code db.FlowInterceptors}）在查询里加的条件、插入时填的租户就都是它的。不在切换里时用接入方给的当前租户。
 */
public final class TenantScope {
    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantScope() { }

    /** 给数据库实例用的当前租户：在 {@link #run} 里是切换到的租户，否则问 fallback（接入方给的） */
    public static Supplier<Long> over(Supplier<Long> fallback) {
        return () -> {
            Long scoped = CURRENT.get();
            return scoped != null ? scoped : fallback.get();
        };
    }

    /** 在 tenant 下做一件事，做完切回原来的 */
    static void run(Long tenant, Runnable action) {
        Long previous = CURRENT.get();
        CURRENT.set(tenant);
        try {
            action.run();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
