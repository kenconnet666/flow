package org.dpdns.zerodep.flow.core.store;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 作业（0007 的 3.5 {@code flow_job}）：定时节点、待办的超时、提醒、无人认领这些到时候要做的事。
 * 默认实现是 {@link org.dpdns.zerodep.flow.core.store.query.QueryStores}；接入方已有调度平台时也可以换成存到别处。
 * <p>领取用普通读加带条件的更新（0001 第 4 条）：{@link #due} 读出一批到期的，{@link #lock} 逐个抢，抢到的才做。
 * {@link #due} 与 {@link #lock} 跨租户（工作进程替所有租户干活），其余的只动当前租户的行。
 */
public interface JobStore {
    /**
     * 一个作业。
     *
     * @param tenantId    租户：做作业时切换到它
     * @param kind        种类：{@code timer}、{@code overdue}、{@code remind}、{@code unclaimed}，或接入方注册的种类
     * @param workItemId  相关的待办；定时节点的作业为 null
     * @param payload     作业自己的数据
     * @param state       {@link #PENDING} 或 {@link #FAILED}
     * @param attempts    失败过几次
     * @param maxAttempts 最多试几次
     */
    record Job(Long id, Long tenantId, String kind, Long instanceId, Long workItemId, Instant dueAt, Map<String, Object> payload,
               String state, int attempts, int maxAttempts, String lockedBy, Instant lockedUntil, String lastError, Instant createdAt) {
        /** 等着做 */
        public static final String PENDING = "pending";
        /** 重试用完了 */
        public static final String FAILED = "failed";

        public Job {
            payload = payload == null ? Map.of() : payload;
        }
    }

    /**
     * 要建的作业（租户、编号、状态由存储填）。
     *
     * @param maxAttempts 最多试几次
     */
    record NewJob(String kind, Long instanceId, Long workItemId, Instant dueAt, Map<String, Object> payload, int maxAttempts,
                  Instant createdAt) { }

    /** 建一个 */
    void schedule(NewJob job);

    /** 到期、没人领着（或领的人超时了）的作业，按到期时间先后，最多 limit 个；跨租户 */
    List<Job> due(Instant now, int limit);

    /** 抢一个作业：还等着、没人领着时记下是谁领的、领到什么时候；抢到返回 true。跨租户 */
    boolean lock(Long jobId, String owner, Instant now, Instant until);

    /** 做完了：删掉 */
    void done(Long jobId);

    /** 下次再做（每隔一段时间的提醒、实例暂停时往后推）：换上新的时刻与数据，放开 */
    void reschedule(Long jobId, Instant dueAt, Map<String, Object> payload);

    /** 失败了，过一会儿重试：失败次数加一，记下原因，放开 */
    void retry(Long jobId, Instant dueAt, String error);

    /** 失败了，重试用完：标成 {@link Job#FAILED}，留着给管理员看 */
    void fail(Long jobId, String error);

    /** 失败的作业重新来过（管理员 retryJob）：改回等着做、失败次数清零、马上到期；返回改到没有 */
    boolean revive(Long jobId, Instant now);

    /** 实例结束了：它还没做的作业都删掉（失败的留着） */
    void cancelForInstance(Long instanceId);
}
