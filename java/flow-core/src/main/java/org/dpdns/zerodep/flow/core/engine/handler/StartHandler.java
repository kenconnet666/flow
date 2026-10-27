package org.dpdns.zerodep.flow.core.engine.handler;

import lombok.Data;
import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.RunState;
import org.dpdns.zerodep.flow.core.data.Variables;
import org.dpdns.zerodep.flow.core.engine.DefinitionCache;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.Execution;
import org.dpdns.zerodep.flow.core.engine.FlowErrors;
import org.dpdns.zerodep.flow.core.engine.LoadedDefinition;
import org.dpdns.zerodep.flow.core.engine.assignee.CandidateRules;
import org.dpdns.zerodep.flow.core.engine.form.FormSubmission;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.entity.FormDataEntity;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.entity.ProcessEntity;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.model.definition.ProcessDefinition;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发起（0006 的 3.1）。在一个事务里：
 * <ol>
 *   <li>流程与版本：找到流程与它最新发布的版本；流程停用了、操作人不在发起人名单里时不让发起；</li>
 *   <li>实例：流程变量 = 定义里的默认值，盖上命令给的初值；先写实例，拿到数据库给的编号（后面都要用）；</li>
 *   <li>表单：只收开始节点上能改的字段（查必填与类型），存第一份；记"实例发起"；</li>
 *   <li>从开始节点出发往前走，直到停在人工节点（生成待办）或走到结束（{@link Execution}）；</li>
 *   <li>写回：按最新的值生成标题与摘要列，写回实例，写入流转记录。</li>
 * </ol>
 * 调用流程（0005 的 4.2）发起子实例也走这里的 {@link #launch}：同一个事务，不查发起人名单，记下父实例。
 */
public class StartHandler {
    private final EngineContext context;
    private final DefinitionCache definitions;

    public StartHandler(EngineContext context) {
        this.context = context;
        this.definitions = context.getDefinitions();
    }

    /** 发起；出错时抛带业务错误码的异常，整个事务回滚 */
    public CommandResult handle(Actor actor, StartCommand command) {
        return context.transaction(() -> {
            LoadedDefinition definition = latestActive(command.getKey());
            if (!CandidateRules.isStarter(actor, definition.model().getStarters())) throw FlowErrors.forbidden("process", command.getKey(), "not-a-starter");
            Execution run = launch(definition, actor, new Launch(actor.getId(), actor.getName(), command.getBusinessKey(), command.getPriority(),
                command.getVariables(), command.getForm(), false, null, command.getPicks()), context.getClock().instant());
            run.flush(command.getTitle());
            return run.toResult();
        });
    }

    /** 流程最新发布的版本；流程不存在、停用了时报错 */
    public LoadedDefinition latestActive(String key) {
        ProcessEntity process = definitions.processes().where(p -> p.processKey().eq(key))
            .singleNotNull(() -> FlowErrors.notFound("process", key));
        if (!ProcessEntity.ACTIVE.equals(process.getStatus())) throw FlowErrors.forbidden("process", key, "disabled");
        return definitions.latest(process);
    }

    /** 发起时给的东西。 */
    @Data
    public static class Launch {
        /** 发起人：发起命令是操作人，子流程是父实例的发起人 */
        private final Long starterId;
        private final String starterName;
        private final String businessKey;
        private final int priority;
        private final Variables variables;
        private final FormValues form;
        /** 表单的值原样存（子流程按 input 给的值），否则只收开始节点上能改的字段 */
        private final boolean fullForm;
        /** 父实例；不是子流程时为 null */
        private final ChildOf parent;
        /** 第一步要选处理人的节点 → 选的人 */
        private final Map<String, List<Long>> picks;
    }

    /** 子实例记下的父实例。 */
    @Data
    public static class ChildOf {
        private final Long instanceId;
        /** 父实例里调用它的节点 */
        private final String node;
        /** 那个节点是第几轮 */
        private final int round;
        /** 最外层的实例 */
        private final Long root;
        /** 这个命令里调用它的父实例的推进：子实例马上就结束时直接回到它（见 CallBehavior） */
        private final Execution caller;
    }

    /**
     * 第 2–4 步：写实例、存表单、记"实例发起"、从开始节点往前走；返回还没写回的推进（调用方 {@link Execution#flush}）。
     *
     * @param actor 记在流转记录上的操作人
     */
    public Execution launch(LoadedDefinition definition, Actor actor, Launch launch, Instant now) {
        ProcessDefinition model = definition.model();
        ChildOf parent = launch.getParent();
        InstanceEntity instance = new InstanceEntity();
        instance.setProcessKey(model.getKey());
        instance.setDefinitionId(definition.getStored().getId());
        instance.setVersion(definition.getStored().getVersion());
        instance.setBusinessKey(launch.getBusinessKey());
        instance.setTitle(model.getName());
        instance.setPriority(launch.getPriority());
        instance.setSummary(Map.of());
        instance.setStatus(InstanceEntity.ACTIVE);
        instance.setCurrent(List.of());
        instance.setVariables(initialVariables(model, launch.getVariables()));
        instance.setState(RunState.INITIAL);
        instance.setStartedBy(launch.getStarterId());
        instance.setStartedByName(launch.getStarterName());
        instance.setStartedAt(now);
        if (parent != null) {
            instance.setParentInstanceId(parent.getInstanceId());
            instance.setParentNode(parent.getNode());
            instance.setParentRound(parent.getRound());
            instance.setRootInstanceId(parent.getRoot());
        }
        instance.setRev(0);
        // 先写实例拿到数据库给的编号，后面的待办、表单的值、流转记录都要用它
        context.getDb().insertable(instance).executeRows(true);
        Execution run = new Execution(context, actor, now, definition, instance, FormValues.empty(), instance.getRev());
        if (parent != null) run.setCaller(parent.getCaller());
        run.setPicks(launch.getPicks());

        ProcessNode.Start start = definition.getGraph().start();
        if (definition.hasForm()) {
            // 子流程的表单值来自定义里的 input（人写的 JSON）：按清单的字段类型读成强类型
            run.setForm(launch.isFullForm() ? FormValues.of(definition.getManifest().typed(launch.getForm().asMap()))
                : FormSubmission.merge(definition.getManifest(), start.getForm(), true, FormValues.empty(), launch.getForm(), run::read));
            FormDataEntity data = new FormDataEntity();
            data.setInstanceId(instance.getId());
            data.setFormVersionId(definition.form().getId());
            data.setValues(run.getForm());
            data.setRev(0);
            context.getDb().insertable(data).executeRows();
        }
        Map<String, Object> origin = parent == null ? Map.of() : Map.of("parentInstanceId", parent.getInstanceId(), "parentNode", parent.getNode());
        run.addEvent(EventEntity.INSTANCE_STARTED, start, null, null, null, null, origin);
        run.leave(start.getId(), null);
        return run;
    }

    /** 定义里声明了默认值的变量，盖上命令给的初值 */
    private static Variables initialVariables(ProcessDefinition model, Variables given) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (ProcessDefinition.Variable v : model.getVariables()) {
            if (v.getDefaultValue() != null) values.put(v.getName(), v.getDefaultValue());
        }
        return Variables.of(values).with(given.asMap());
    }
}
