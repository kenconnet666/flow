package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.command.SignalCommand;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.store.InstanceStore.InstanceRecord;
import org.dpdns.zerodep.flow.core.store.WaitStore.Wait;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.runtime.FlowEvent;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance;

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
public final class SignalHandler {
    private final EngineContext context;
    private final DefinitionCache definitions;

    public SignalHandler(EngineContext context) {
        this.context = context;
        this.definitions = context.definitions();
    }

    /** 送来一个事件，返回每个往下走了（或记了一笔）的实例这一步的结果 */
    public List<CommandResult> signal(Actor actor, SignalCommand command) {
        if (command.event() == null || command.event().isBlank()) throw FlowErrors.invalid("event", "required");
        if (command.instanceId() == null && command.businessKey() == null) throw FlowErrors.invalid("instanceId", "required");
        return context.transaction(() -> {
            List<Wait> waits = command.instanceId() != null
                ? context.stores().waits().ofInstance(command.instanceId(), command.event())
                : context.stores().waits().ofBusinessKey(command.businessKey(), command.event());
            List<CommandResult> results = new ArrayList<>();
            if (waits.isEmpty()) {
                if (command.instanceId() != null) results.add(ignored(actor, command.instanceId(), command));
            } else {
                for (Wait wait : waits) {
                    CommandResult result = deliver(actor, wait, command);
                    if (result != null) results.add(result);
                }
            }
            return results;
        });
    }

    /** 送到一个等待：锁住实例、删掉这一行；实例还停在那个节点上时往下走，否则（跳转走了）记一笔 */
    private CommandResult deliver(Actor actor, Wait wait, SignalCommand command) {
        int rev = context.stores().instances().lock(wait.instanceId()).orElseThrow(() -> FlowErrors.notFound("instance", wait.instanceId()));
        InstanceRecord instance = context.stores().instances().find(wait.instanceId()).orElseThrow();
        ClaimHandler.requireActive(instance);
        if (!context.stores().waits().remove(wait.id())) return null;   // 并发的同一个事件先到了
        FormValues form = context.stores().formData().read(instance.id()).map(f -> f.values()).orElse(FormValues.empty());
        Execution run = new Execution(context, actor, context.clock().instant(), definitions.of(instance), instance, form, rev);
        ProcessNode.Receive receive = (ProcessNode.Receive) run.graph().node(wait.node());
        if (!instance.state().waits().containsKey(wait.node())) {
            run.addEvent(FlowEvent.SIGNAL_IGNORED, receive, null, command.outcome(), null, null, eventData(command));
        } else {
            if (!receive.outcomes().isEmpty() && !receive.outcomes().contains(command.outcome())) throw FlowErrors.invalid("outcome", "unknown-outcome");
            run.addEvent(FlowEvent.SIGNAL_RECEIVED, receive, null, command.outcome(), null, null, eventData(command));
            run.received(receive, command.outcome(), command.data());
        }
        run.flush(null);
        return run.result();
    }

    /** 实例不在等这个事件：只记一笔（不锁实例，同留言） */
    private CommandResult ignored(Actor actor, Long instanceId, SignalCommand command) {
        InstanceRecord instance = context.stores().instances().find(instanceId).orElseThrow(() -> FlowErrors.notFound("instance", instanceId));
        Execution run = new Execution(context, actor, context.clock().instant(), definitions.of(instance), instance, FormValues.empty(),
            instance.rev());
        run.addEvent(FlowEvent.SIGNAL_IGNORED, null, null, command.outcome(), null, null, eventData(command));
        run.publish();
        return run.result();
    }

    private static Map<String, Object> eventData(SignalCommand command) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", command.event());
        if (!command.data().isEmpty()) data.put("data", command.data());
        return data;
    }
}
