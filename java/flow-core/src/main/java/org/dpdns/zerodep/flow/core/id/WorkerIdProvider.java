package org.dpdns.zerodep.flow.core.id;

/**
 * 雪花的机器号从哪来（docs/decisions/0001 的"雪花的机器号"）：默认在 {@code flow_worker} 表里租用（{@code store.LeasedWorkerIds}），
 * 也可以写死，或接入方自己实现（例如从 Kubernetes 的序号取）。
 */
@FunctionalInterface
public interface WorkerIdProvider {
    /** 这个进程的机器号，在 [0, limit) 里 */
    long workerId(long limit);

    /** 写死的机器号：单机部署，或部署时给每个实例配好 */
    static WorkerIdProvider fixed(long id) {
        return limit -> {
            if (id < 0 || id >= limit) throw new IllegalArgumentException("worker id " + id + " is outside [0, " + limit + ")");
            return id;
        };
    }
}
