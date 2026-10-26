package org.dpdns.zerodep.flow.core.store;

import org.dpdns.zerodep.flow.core.data.RunState;
import org.dpdns.zerodep.flow.core.data.Variables;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 流程实例（0007 的 {@code flow_instance}）。推进一个实例前先 {@link #lock} 把版本加一：各库都会锁住这一行，并发的推进在这里排队
 * （0001 第 3 条）。默认实现在 {@code store.query} 包里，可以单独替换。
 */
public interface InstanceStore {
    /** 新建，返回带上数据库给的编号的记录 */
    InstanceRecord insert(InstanceRecord instance);

    /** 版本加一并锁住这一行，返回新的版本；实例不存在时为空 */
    Optional<Integer> lock(Long instanceId);

    /** 读一行 */
    Optional<InstanceRecord> find(Long instanceId);

    /** 按编号读几行（收件箱带上实例的标题与摘要用） */
    List<InstanceRecord> findAll(Collection<Long> instanceIds);

    /** 整行写回：标题、摘要、状态、结果、当前节点、流程变量、运行状态、结束时间（调用前已 {@link #lock}） */
    void update(InstanceRecord instance);

    /**
     * 实例的一行。不可变：推进时用 {@code with...} 换出新的一份。
     *
     * @param id            编号
     * @param processKey    流程标识
     * @param definitionId  按哪个版本走
     * @param version       版本号
     * @param businessKey   业务单据编号
     * @param title         实例标题
     * @param priority      紧急程度
     * @param summary       待办列表的摘要列：{@code { ref }} 的名字 → 值
     * @param status        状态（{@link ProcessInstance#ACTIVE} 等）
     * @param result        结束结果
     * @param current       正在处理的节点
     * @param variables     流程变量
     * @param state         运行状态，引擎内部用
     * @param startedBy     发起人
     * @param startedByName 发起人的名字（快照）
     * @param startedAt     发起时间
     * @param endedAt       结束时间
     * @param rev           版本
     */
    record InstanceRecord(Long id, String processKey, Long definitionId, int version, String businessKey, String title,
                          int priority, Map<String, Object> summary, String status, String result,
                          List<ProcessInstance.CurrentNode> current, Variables variables, RunState state,
                          Long startedBy, String startedByName, Instant startedAt, Instant endedAt, int rev) {
        /** 换流程变量 */
        public InstanceRecord withVariables(Variables next) {
            return new InstanceRecord(id, processKey, definitionId, version, businessKey, title, priority, summary, status, result, current,
                next, state, startedBy, startedByName, startedAt, endedAt, rev);
        }

        /** 换运行状态 */
        public InstanceRecord withState(RunState next) {
            return new InstanceRecord(id, processKey, definitionId, version, businessKey, title, priority, summary, status, result, current,
                variables, next, startedBy, startedByName, startedAt, endedAt, rev);
        }

        /** 结束：状态、结果、结束时间，正在处理的节点清空 */
        public InstanceRecord withEnd(String nextStatus, String nextResult, Instant at) {
            return new InstanceRecord(id, processKey, definitionId, version, businessKey, title, priority, summary, nextStatus, nextResult,
                List.of(), variables, state, startedBy, startedByName, startedAt, at, rev);
        }

        /** 换列表上显示的东西：标题、摘要列、正在处理的节点 */
        public InstanceRecord withDisplay(String nextTitle, Map<String, Object> nextSummary, List<ProcessInstance.CurrentNode> nextCurrent) {
            return new InstanceRecord(id, processKey, definitionId, version, businessKey, nextTitle, priority, nextSummary, status, result,
                nextCurrent, variables, state, startedBy, startedByName, startedAt, endedAt, rev);
        }

        /** 给前端的形状 */
        public ProcessInstance view() {
            return new ProcessInstance(id, processKey, version, businessKey, title, priority, summary, status, result, current,
                startedBy, startedByName, startedAt, endedAt);
        }
    }
}
