package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.command.WithdrawCommand;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.store.InstanceStore.InstanceRecord;
import org.dpdns.zerodep.flow.model.runtime.FlowEvent;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance;
import org.dpdns.zerodep.flow.model.runtime.WorkItem;

import java.util.List;
import java.util.Objects;

/**
 * 发起人撤回整个实例（0006 的 3.1）。能不能撤回看定义的 {@code withdraw}（0005 的 2.1）：
 * {@code beforeAction}（默认）——还没有人处理过；{@code beforeClaim}——还没有人认领、处理过；{@code never}——不能撤回。
 * 撤回时取消还开着的待办，实例的状态与结果都是 {@code withdrawn}。
 */
public final class WithdrawHandler {
    private final EngineContext context;
    private final DefinitionCache definitions;

    public WithdrawHandler(EngineContext context, DefinitionCache definitions) {
        this.context = context;
        this.definitions = definitions;
    }

    /** 撤回；不是发起人、规则不允许时报 {@code flow.forbidden} */
    public CommandResult handle(Actor actor, WithdrawCommand command) {
        return context.db().transaction(() -> {
            int rev = context.stores().instances().lock(command.instanceId())
                .orElseThrow(() -> FlowErrors.notFound("instance", command.instanceId()));
            InstanceRecord instance = context.stores().instances().find(command.instanceId()).orElseThrow();
            ClaimHandler.requireActive(instance);
            if (!actor.id().equals(instance.startedBy())) throw FlowErrors.forbidden("instance", instance.id(), "not-the-initiator");

            LoadedDefinition definition = definitions.of(instance);
            String rule = Objects.requireNonNullElse(definition.model().settings().withdraw(), "beforeAction");
            if (!allowed(rule, context.stores().workItems().ofInstance(instance.id()))) {
                throw FlowErrors.forbidden("instance", instance.id(), "withdraw-" + rule);
            }

            Execution run = new Execution(context, actor, context.clock().instant(), definition, instance, FormValues.empty(), rev);
            run.finish(ProcessInstance.WITHDRAWN, ProcessInstance.WITHDRAWN, FlowEvent.INSTANCE_WITHDRAWN, command.reason());
            run.flush(instance.title());
            return new CommandResult(run.instance().view(), run.created, run.closed, run.events);
        });
    }

    /** 按规则看现在还能不能撤回 */
    private static boolean allowed(String rule, List<WorkItem> items) {
        boolean handled = items.stream().anyMatch(w -> WorkItem.DONE.equals(w.state()));
        boolean claimed = items.stream().anyMatch(w -> WorkItem.CLAIMED.equals(w.assignment()));
        return switch (rule) {
            case "never" -> false;
            case "beforeClaim" -> !handled && !claimed;
            default -> !handled;
        };
    }
}
