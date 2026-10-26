package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.RunState;
import org.dpdns.zerodep.flow.core.data.Variables;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.store.DefinitionStore.StoredProcess;
import org.dpdns.zerodep.flow.core.store.InstanceStore.InstanceRecord;
import org.dpdns.zerodep.flow.model.definition.ProcessDefinition;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.runtime.FlowEvent;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发起（0006 的 3.1）。在一个事务里：
 * <ol>
 *   <li>找到流程与它最新发布的版本；流程停用了、操作人不在发起人名单里时不让发起；</li>
 *   <li>流程变量 = 定义里的默认值，盖上命令给的初值；</li>
 *   <li>收表单：只取开始节点上能改的字段，查必填与类型；</li>
 *   <li>写实例与表单的值，记"实例发起"；</li>
 *   <li>从开始节点出发往前走，直到停在人工节点（生成待办）或走到结束；</li>
 *   <li>按最新的值生成标题与摘要列，写回。</li>
 * </ol>
 */
public final class StartHandler {
    private final EngineContext context;
    private final DefinitionCache definitions;

    public StartHandler(EngineContext context, DefinitionCache definitions) {
        this.context = context;
        this.definitions = definitions;
    }

    /** 发起；出错时抛带业务错误码的异常，整个事务回滚 */
    public CommandResult handle(Actor actor, StartCommand command) {
        return context.db().transaction(() -> {
            // 1. 流程与版本
            StoredProcess process = context.stores().definitions().process(command.key())
                .orElseThrow(() -> FlowErrors.notFound("process", command.key()));
            if (!StoredProcess.ACTIVE.equals(process.status())) throw FlowErrors.forbidden("process", command.key(), "disabled");
            LoadedDefinition definition = definitions.of(context.stores().definitions().latest(command.key())
                .orElseThrow(() -> FlowErrors.notFound("process", command.key())));
            ProcessDefinition model = definition.model();
            if (!CandidateRules.isStarter(actor, model.starters())) throw FlowErrors.forbidden("process", command.key(), "not-a-starter");

            // 2. 实例与流程变量
            Instant now = context.clock().instant();
            InstanceRecord instance = new InstanceRecord(context.ids().next("flow_instance"), command.key(), definition.stored().id(),
                definition.stored().version(), command.businessKey(), model.name(), command.priority(), Map.of(), ProcessInstance.ACTIVE, null,
                List.of(), initialVariables(model, command.variables()), RunState.INITIAL, actor.id(), actor.name(), now, null, 0);
            Execution run = new Execution(context, actor, now, definition.graph(), instance, FormValues.empty(), instance.rev());

            // 3. 表单
            ProcessNode.Start start = definition.graph().start();
            if (definition.hasForm()) {
                run.form(FormSubmission.merge(definition.manifest(), start.form(), true, FormValues.empty(), command.form(), run::read));
            }

            // 4. 写实例与表单的值
            context.stores().instances().insert(instance);
            if (definition.hasForm()) context.stores().formData().insert(instance.id(), definition.form().id(), run.form(), now);
            run.record(FlowEvent.INSTANCE_STARTED, start, null, null, null, null);

            // 5. 往前走；6. 写回
            run.leave(start.id(), null);
            run.flush(command.title());
            return new CommandResult(run.instance().view(), run.created, run.closed, run.events);
        });
    }

    /** 定义里声明了默认值的变量，盖上命令给的初值 */
    private static Variables initialVariables(ProcessDefinition model, Variables given) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (ProcessDefinition.Variable v : model.variables()) {
            if (v.defaultValue() != null) values.put(v.name(), v.defaultValue());
        }
        return Variables.of(values).with(given.asMap());
    }
}
