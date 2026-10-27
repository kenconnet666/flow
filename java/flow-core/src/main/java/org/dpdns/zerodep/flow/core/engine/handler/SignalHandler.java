package org.dpdns.zerodep.flow.core.engine.handler;

import org.dpdns.zerodep.flow.core.command.SignalCommand;
import org.dpdns.zerodep.flow.core.engine.DefinitionCache;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.Execution;
import org.dpdns.zerodep.flow.core.engine.FlowErrors;
import org.dpdns.zerodep.flow.core.engine.node.CustomBehavior;
import org.dpdns.zerodep.flow.core.engine.node.ReceiveBehavior;
import org.dpdns.zerodep.flow.core.engine.store.Instances;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.entity.WaitEntity;
import org.dpdns.zerodep.flow.core.spi.Actor;
import org.dpdns.zerodep.flow.core.spi.FormValues;
import org.dpdns.zerodep.flow.core.view.CommandResult;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部事件（0005 的 4.3）：按实例编号或业务单据编号找到在等这个事件的等待（{@code flow_wait}），逐个带条件地删掉、往下走
 * （删到 0 行就是并发送来的同一个事件已经处理过了）。都在一个事务里。
 * <ul>
 *   <li>实例在等：记一条 {@code signal.received}，{@code output} 写进表单或流程变量，按结果往下走；</li>
 *   <li>按实例编号找、实例不在等：记一条 {@code signal.ignored}，不报错；按单号找没有在等的：什么也不做；</li>
 *   <li>实例暂停了：报 {@code flow.instance-not-active}，业务系统过后重发。</li>
 * </ul>
 */
public class SignalHandler {
    private final EngineContext context;
    private final DefinitionCache definitions;

    public SignalHandler(EngineContext context) {
        this.context = context;
        this.definitions = context.getDefinitions();
    }

    /** 送来一个事件，返回每个往下走了（或记了一笔）的实例这一步的结果 */
    public List<CommandResult> signal(Actor actor, SignalCommand command) {
        if (command.getEvent() == null || command.getEvent().isBlank()) throw FlowErrors.invalid("event", "required");
        if (command.getInstanceId() == null && command.getBusinessKey() == null) throw FlowErrors.invalid("instanceId", "required");
        return context.transaction(() -> {
            List<WaitEntity> waits = context.getDb().queryable(WaitEntity.class)
                .where(w -> {
                    w.event().eq(command.getEvent());
                    if (command.getInstanceId() != null) w.instanceId().eq(command.getInstanceId());
                    else w.businessKey().eq(command.getBusinessKey());
                })
                .orderBy(w -> w.id().asc()).toList();
            List<CommandResult> results = new ArrayList<>();
            if (waits.isEmpty()) {
                if (command.getInstanceId() != null) results.add(ignored(actor, command.getInstanceId(), command));
            } else {
                for (WaitEntity wait : waits) {
                    CommandResult result = deliver(actor, wait, command);
                    if (result != null) results.add(result);
                }
            }
            return results;
        });
    }

    /** 送到一个等待：锁住实例、删掉这一行；实例还停在那个节点上时往下走，否则（跳转走了）记一笔 */
    private CommandResult deliver(Actor actor, WaitEntity wait, SignalCommand command) {
        InstanceEntity instance = Instances.lock(context.getDb(), wait.getInstanceId());
        ClaimHandler.requireActive(instance);
        long removed = context.getDb().deletable(WaitEntity.class).allowDeleteStatement(true).whereById(wait.getId()).executeRows();
        if (removed == 0) return null;   // 并发的同一个事件先到了
        FormValues form = Instances.form(context.getDb(), instance.getId());
        Execution run = new Execution(context, actor, context.getClock().instant(), definitions.of(instance), instance, form, instance.getRev());
        ProcessNode node = run.getGraph().node(wait.getNode());
        if (!instance.getState().getWaits().containsKey(wait.getNode())) {
            run.addEvent(EventEntity.SIGNAL_IGNORED, node, null, command.getOutcome(), null, null, eventData(command));
        } else if (node instanceof ProcessNode.Receive receive) {
            if (!receive.getOutcomes().isEmpty() && !receive.getOutcomes().contains(command.getOutcome())) throw FlowErrors.invalid("outcome", "unknown-outcome");
            run.addEvent(EventEntity.SIGNAL_RECEIVED, receive, null, command.getOutcome(), null, null, eventData(command));
            ReceiveBehavior.received(run, receive, command.getOutcome(), command.getData());
        } else {
            // 自定义节点停下来等的事件
            run.addEvent(EventEntity.SIGNAL_RECEIVED, node, null, command.getOutcome(), null, null, eventData(command));
            CustomBehavior.received(run, (ProcessNode.Custom) node, command.getOutcome(), command.getData());
        }
        run.flush(null);
        return run.toResult();
    }

    /** 实例不在等这个事件：只记一笔（不锁实例，同留言） */
    private CommandResult ignored(Actor actor, Long instanceId, SignalCommand command) {
        InstanceEntity instance = Instances.find(context.getDb(), instanceId);
        Execution run = new Execution(context, actor, context.getClock().instant(), definitions.of(instance), instance, FormValues.empty(),
            instance.getRev());
        run.addEvent(EventEntity.SIGNAL_IGNORED, null, null, command.getOutcome(), null, null, eventData(command));
        run.publish();
        return run.toResult();
    }

    private static Map<String, Object> eventData(SignalCommand command) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", command.getEvent());
        if (!command.getData().isEmpty()) data.put("data", command.getData());
        return data;
    }
}
