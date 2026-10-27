package org.dpdns.zerodep.flow.core.engine.node;

import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.engine.Execution;
import org.dpdns.zerodep.flow.core.engine.job.BuiltinJobs;
import org.dpdns.zerodep.flow.core.engine.store.Instances;
import org.dpdns.zerodep.flow.core.entity.WaitEntity;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 等待外部事件（0005 的 4.3 {@code receive}）：到达时记下在等什么（{@code flow_wait} 一行，按事件名加实例或业务编号找到），停在这里；
 * 写了 {@code due} 时排一个超时作业。事件到了由 {@code SignalHandler} 调 {@link #received}。
 */
public class ReceiveBehavior {
    private ReceiveBehavior() { }

    public static void arrive(Execution run, ProcessNode.Receive receive) {
        run.updateState(s -> s.enter(receive.getId()).waitUntil(receive.getId(), run.getNow()));
        WaitEntity wait = new WaitEntity();
        wait.setInstanceId(run.getInstance().getId());
        wait.setNode(receive.getId());
        wait.setEvent(receive.getEvent());
        wait.setBusinessKey(run.getInstance().getBusinessKey());
        wait.setCreatedAt(run.getNow());
        run.getContext().getDb().insertable(wait).executeRows();
        Instant due = receive.getDue() == null ? null : run.getDeadlines().of(receive.getDue(), run.getNow(), run::read);
        if (due == null) return;
        Map<String, Object> payload = Map.of(BuiltinJobs.NODE, receive.getId(), BuiltinJobs.ROUND, run.getInstance().getState().round(receive.getId()));
        run.schedule(BuiltinJobs.RECEIVE_TIMEOUT, null, due, payload, BuiltinJobs.MAX_ATTEMPTS);
    }

    /**
     * 事件到了（或等超时、按 overdue 的结果）：不再等；{@code output} 把事件数据里的值写进表单（{@code form.} 开头）
     * 或流程变量，按结果往下走。
     */
    public static void received(Execution run, ProcessNode.Receive receive, String outcome, Map<String, Object> data) {
        Map<String, Object> formChanges = new LinkedHashMap<>();
        Map<String, Object> variableChanges = new LinkedHashMap<>();
        receive.getOutput().forEach((target, key) -> {
            if (target.startsWith("form.")) formChanges.put(target.substring("form.".length()), data.get(key));
            else variableChanges.put(target, data.get(key));
        });
        if (!formChanges.isEmpty()) {
            // 业务系统送来的数据未必标了类型：按清单的字段类型读成强类型
            FormValues changes = FormValues.of(run.getManifest() == null ? formChanges : run.getManifest().typed(formChanges));
            run.setForm(run.getForm().with(changes));
            Instances.saveForm(run.getContext().getDb(), run.getInstance().getId(), run.getForm());
        }
        if (!variableChanges.isEmpty()) run.getInstance().setVariables(run.getInstance().getVariables().with(variableChanges));
        run.updateState(s -> s.waitDone(receive.getId()));
        run.leave(receive.getId(), outcome);
    }
}
