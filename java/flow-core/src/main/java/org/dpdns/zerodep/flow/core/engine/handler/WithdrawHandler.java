package org.dpdns.zerodep.flow.core.engine.handler;

import org.dpdns.zerodep.flow.core.command.WithdrawCommand;
import org.dpdns.zerodep.flow.core.engine.DefinitionCache;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.Execution;
import org.dpdns.zerodep.flow.core.engine.FlowErrors;
import org.dpdns.zerodep.flow.core.engine.LoadedDefinition;
import org.dpdns.zerodep.flow.core.engine.store.Instances;
import org.dpdns.zerodep.flow.core.engine.store.WorkItems;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.entity.WorkItemEntity;
import org.dpdns.zerodep.flow.core.spi.Actor;
import org.dpdns.zerodep.flow.core.spi.FormValues;
import org.dpdns.zerodep.flow.core.view.CommandResult;
import org.dpdns.zerodep.flow.core.view.workitem.WorkItemVO;

import java.util.List;
import java.util.Objects;

/**
 * 发起人撤回整个实例（0006 的 3.1）。能不能撤回看定义的 {@code withdraw}（0005 的 2.1）：
 * {@code beforeAction}（默认）——还没有人处理过；{@code beforeClaim}——还没有人认领、处理过；{@code never}——不能撤回。
 * 撤回时取消还开着的待办，实例的状态与结果都是 {@code withdrawn}。
 */
public class WithdrawHandler {
    private final EngineContext context;
    private final DefinitionCache definitions;

    public WithdrawHandler(EngineContext context) {
        this.context = context;
        this.definitions = context.getDefinitions();
    }

    /** 撤回；不是发起人、规则不允许时报 {@code flow.forbidden} */
    public CommandResult handle(Actor actor, WithdrawCommand command) {
        return context.transaction(() -> {
            InstanceEntity instance = Instances.lock(context.getDb(), command.getInstanceId());
            ClaimHandler.requireActive(instance);
            if (!actor.getId().equals(instance.getStartedBy())) throw FlowErrors.forbidden("instance", instance.getId(), "not-the-initiator");

            LoadedDefinition definition = definitions.of(instance);
            if (!allowed(definition, WorkItems.ofInstance(context.getDb(), instance.getId()))) {
                String rule = Objects.requireNonNullElse(definition.model().getSettings().getWithdraw(), "beforeAction");
                throw FlowErrors.forbidden("instance", instance.getId(), "withdraw-" + rule);
            }

            Execution run = new Execution(context, actor, context.getClock().instant(), definition, instance, FormValues.empty(), instance.getRev());
            run.finish(InstanceEntity.WITHDRAWN, InstanceEntity.WITHDRAWN, EventEntity.INSTANCE_WITHDRAWN, command.getReason());
            run.flush(instance.getTitle());
            return run.toResult();
        });
    }

    /** 发起人现在还能不能撤回（实例详情上的"撤回"按钮）：不看是不是发起人 */
    public static boolean allowed(LoadedDefinition definition, List<WorkItemVO> items) {
        return allowed(Objects.requireNonNullElse(definition.model().getSettings().getWithdraw(), "beforeAction"), items);
    }

    /** 按规则看现在还能不能撤回 */
    private static boolean allowed(String rule, List<WorkItemVO> items) {
        boolean handled = items.stream().anyMatch(w -> WorkItemEntity.DONE.equals(w.getState()));
        boolean claimed = items.stream().anyMatch(w -> WorkItemEntity.CLAIMED.equals(w.getAssignment()));
        return switch (rule) {
            case "never" -> false;
            case "beforeClaim" -> !handled && !claimed;
            default -> !handled;
        };
    }
}
