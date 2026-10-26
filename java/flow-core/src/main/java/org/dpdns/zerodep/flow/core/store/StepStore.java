package org.dpdns.zerodep.flow.core.store;

import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.RunState;
import org.dpdns.zerodep.flow.core.data.Variables;

import java.util.Optional;

/**
 * 最近一次处理之前的快照（{@code flow_step}）：处理人撤回刚处理的一步时用它恢复。每个实例只留一份。
 * 默认实现是 {@link org.dpdns.zerodep.flow.core.store.query.QueryStores}。
 */
public interface StepStore {
    /**
     * 一份快照。
     *
     * @param workItemId 处理的是哪个待办
     * @param rev        处理那一步时实例的版本
     * @param state      处理之前的运行状态
     * @param variables  处理之前的流程变量
     * @param form       处理之前的表单的值
     */
    record StepSnapshot(Long instanceId, Long workItemId, int rev, RunState state, Variables variables, FormValues form) { }

    /** 记下（换掉这个实例原来的那份） */
    void save(StepSnapshot snapshot);

    /** 这个实例最近一次处理之前的快照 */
    Optional<StepSnapshot> find(Long instanceId);

    /** 不再能撤回：删掉 */
    void remove(Long instanceId);
}
