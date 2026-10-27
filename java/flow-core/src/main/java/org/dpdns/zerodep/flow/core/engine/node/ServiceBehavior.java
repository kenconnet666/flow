package org.dpdns.zerodep.flow.core.engine.node;

import org.dpdns.zerodep.flow.core.engine.Execution;
import org.dpdns.zerodep.flow.core.engine.form.RefValues;
import org.dpdns.zerodep.flow.core.engine.job.BuiltinJobs;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.spi.ServiceAction;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自动节点（0005 的 4.1 {@code service}）：执行接入方注册的动作（{@link ServiceAction}），按它给的结果往下走。
 * 同步的（{@code mode: "sync"}）在流转的事务里马上做，失败整个回滚；异步的（默认）停下、排一个 action 作业，事务提交后由作业做，
 * 失败按节点的 retry 重试，用完了成了"异常"，管理员重试或跳过（{@link #skip}）。
 */
public class ServiceBehavior {
    private ServiceBehavior() { }

    public static void arrive(Execution run, ProcessNode.Service service) {
        run.updateState(s -> s.enter(service.getId()));
        if ("sync".equals(service.getMode())) {
            perform(run, service);
            return;
        }
        Instant now = run.getNow();
        run.updateState(s -> s.waitUntil(service.getId(), now));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(BuiltinJobs.NODE, service.getId());
        payload.put(BuiltinJobs.ROUND, run.getInstance().getState().round(service.getId()));
        if (service.getRetryBackoff() != null) {
            payload.put(BuiltinJobs.BACKOFF_SECONDS, run.getDeadlines().plus(service.getRetryBackoff(), now).getEpochSecond() - now.getEpochSecond());
        }
        // retry.times 是失败后再试几次：一共做 times + 1 次
        int attempts = service.getRetryTimes() == null ? BuiltinJobs.MAX_ATTEMPTS : service.getRetryTimes() + 1;
        run.schedule(BuiltinJobs.ACTION, null, now, payload, attempts);
    }

    /**
     * 做动作，改它给的流程变量，按它给的结果往下走。去重键是"实例:节点:轮次"：同一轮重试时相同。
     * 动作抛的不是业务错误码的异常包成 {@code flow.extension-failed}。
     */
    public static void perform(Execution run, ProcessNode.Service service) {
        String key = run.getInstance().getId() + ":" + service.getId() + ":" + run.getInstance().getState().round(service.getId());
        ServiceAction.Context actionContext = new ServiceAction.Context(run.loadView(), service.getId(),
            RefValues.resolve(service.getArgs(), run::read), key, run::read);
        ServiceAction.Result result;
        try {
            result = run.getContext().getExtensions().action(service.getAction()).perform(actionContext);
        } catch (FlowException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FlowException(FlowErrorCode.EXTENSION_FAILED, "action " + service.getAction() + " failed at " + service.getId(),
                Map.of("kind", "action", "name", service.getAction()), List.of(), e);
        }
        if (!result.getVariables().isEmpty()) run.getInstance().setVariables(run.getInstance().getVariables().with(result.getVariables()));
        run.updateState(s -> s.waitDone(service.getId()));
        run.leave(service.getId(), result.getOutcome());
    }

    /** 管理员跳过失败的动作（{@code skipJob}）：记一条，不再等，按指定的结果往下走 */
    public static void skip(Execution run, ProcessNode.Service service, Long jobId, String outcome, String reason) {
        run.addEvent(EventEntity.JOB_SKIPPED, service, null, outcome, null, reason, Map.of("jobId", jobId));
        run.updateState(s -> s.waitDone(service.getId()));
        run.leave(service.getId(), outcome);
    }
}
