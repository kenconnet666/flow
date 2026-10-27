package org.dpdns.zerodep.flow.core.engine.handler;

import org.dpdns.zerodep.flow.core.engine.form.FormCode;

import lombok.Data;
import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.command.SaveStartDraftCommand;
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
import org.dpdns.zerodep.flow.core.engine.store.Instances;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.entity.FormDataEntity;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.entity.ProcessEntity;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.view.instance.InstanceVO;
import org.dpdns.zerodep.flow.model.definition.ProcessDefinition;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
 * <p>
 * 发起前的草稿（{@link #saveDraft}）是一个状态为 draft 的实例：有编号、存着表单的值，不进流程。从草稿发起时用这个实例，
 * 按发起时最新的版本走，没提交的字段用草稿里的值。
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
            InstanceEntity draft = command.getDraftId() == null ? null : ownDraft(actor, command.getDraftId());
            String key = draft == null ? command.getKey() : draft.getProcessKey();
            LoadedDefinition definition = latestActive(key);
            if (!CandidateRules.isStarter(actor, definition.model().getStarters())) throw FlowErrors.forbidden("process", key, "not-a-starter");
            String businessKey = command.getBusinessKey() == null && draft != null ? draft.getBusinessKey() : command.getBusinessKey();
            Execution run = launch(definition, actor, new Launch(actor.getId(), actor.getName(), businessKey, command.getPriority(),
                command.getVariables(), command.getForm(), false, null, command.getPicks()), context.getClock().instant(), draft);
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
        return launch(definition, actor, launch, now, null);
    }

    /** 同上；draft 不为 null 时用这份草稿的实例（已锁住），没提交的字段用草稿里的值 */
    private Execution launch(LoadedDefinition definition, Actor actor, Launch launch, Instant now, InstanceEntity draft) {
        ProcessDefinition model = definition.model();
        ChildOf parent = launch.getParent();
        InstanceEntity instance = draft == null ? new InstanceEntity() : draft;
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
        if (draft == null) {
            instance.setRev(0);
            // 先写实例拿到数据库给的编号，后面的待办、表单的值、流转记录都要用它
            context.getDb().insertable(instance).executeRows(true);
        } else {
            context.getDb().updatable(instance).executeRows();
        }
        FormValues drafted = draft == null ? FormValues.empty() : Instances.form(context.getDb(), instance.getId());
        Execution run = new Execution(context, actor, now, definition, instance, FormValues.empty(), instance.getRev());
        if (parent != null) run.setCaller(parent.getCaller());
        run.setPicks(launch.getPicks());

        ProcessNode.Start start = definition.getGraph().start();
        if (definition.hasForm()) {
            // 子流程的表单值来自定义里的 input（人写的 JSON）：按清单的字段类型读成强类型
            if (launch.isFullForm()) {
                run.setForm(FormValues.of(definition.getManifest().typed(launch.getForm().asMap())));
            } else {
                run.setForm(FormSubmission.merge(definition.getManifest(), start.getForm(), true, drafted, launch.getForm(), run::read));
                // 表单的后端代码：校验、计算、写业务表（子流程按定义的 input 发起，不经它）
                run.setForm(FormCode.submit(run, definition, start.getId(), true, run.getForm()));
            }
            saveFormData(instance.getId(), definition.form().getId(), run.getForm());
        }
        Map<String, Object> origin = parent == null ? Map.of() : Map.of("parentInstanceId", parent.getInstanceId(), "parentNode", parent.getNode());
        run.addEvent(EventEntity.INSTANCE_STARTED, start, null, null, null, null, origin);
        run.leave(start.getId(), null);
        return run;
    }

    // ───────── 发起前的草稿 ─────────

    /** 存草稿：第一次存时建一个 draft 实例，之后整份替换表单的值；返回草稿（实例的样子，startedAt 是最后存的时间） */
    public InstanceVO saveDraft(Actor actor, SaveStartDraftCommand command) {
        return context.transaction(() -> {
            Instant now = context.getClock().instant();
            InstanceEntity existing = command.getDraftId() == null ? null : ownDraft(actor, command.getDraftId());
            String key = existing == null ? command.getKey() : existing.getProcessKey();
            if (key == null || key.isBlank()) throw FlowErrors.invalid("key", "required");
            LoadedDefinition definition = latestActive(key);
            if (!CandidateRules.isStarter(actor, definition.model().getStarters())) throw FlowErrors.forbidden("process", key, "not-a-starter");
            InstanceEntity draft = existing == null ? newDraft(definition, actor) : existing;
            draft.setDefinitionId(definition.getStored().getId());
            draft.setVersion(definition.getStored().getVersion());
            draft.setBusinessKey(command.getBusinessKey());
            draft.setStartedAt(now);
            if (existing == null) context.getDb().insertable(draft).executeRows(true);
            else context.getDb().updatable(draft).executeRows();
            if (definition.hasForm()) {
                Function<String, Object> read = name -> "sys.initiator".equals(name) ? actor.getId() : null;
                FormValues form = FormSubmission.draft(definition.getManifest(), definition.getGraph().start().getForm(), true,
                    FormValues.empty(), command.getForm() == null ? FormValues.empty() : command.getForm(), read);
                saveFormData(draft.getId(), definition.form().getId(), form);
            }
            return Instances.view(context.getDb(), draft.getId());
        });
    }

    /** 放弃草稿：删掉草稿的实例与表单的值；返回是否删了 */
    public boolean discardDraft(Actor actor, Long draftId) {
        return context.transaction(() -> {
            InstanceEntity draft = ownDraft(actor, draftId);
            context.getDb().deletable(FormDataEntity.class).allowDeleteStatement(true).whereById(draft.getId()).executeRows();
            return context.getDb().deletable(InstanceEntity.class).allowDeleteStatement(true)
                .where(i -> { i.id().eq(draft.getId()); i.status().eq(InstanceEntity.DRAFT); })
                .executeRows() == 1;
        });
    }

    /** 我的一份草稿（锁住）：不是草稿、不是我的都当不存在 */
    private InstanceEntity ownDraft(Actor actor, Long draftId) {
        InstanceEntity draft = Instances.lock(context.getDb(), draftId);
        if (!InstanceEntity.DRAFT.equals(draft.getStatus()) || !actor.getId().equals(draft.getStartedBy())) throw FlowErrors.notFound("draft", draftId);
        return draft;
    }

    private static InstanceEntity newDraft(LoadedDefinition definition, Actor actor) {
        InstanceEntity draft = new InstanceEntity();
        draft.setProcessKey(definition.model().getKey());
        draft.setTitle(definition.model().getName());
        draft.setPriority(0);
        draft.setSummary(Map.of());
        draft.setStatus(InstanceEntity.DRAFT);
        draft.setCurrent(List.of());
        draft.setVariables(Variables.empty());
        draft.setState(RunState.INITIAL);
        draft.setStartedBy(actor.getId());
        draft.setStartedByName(actor.getName());
        draft.setRev(0);
        return draft;
    }

    /** 实例上表单的值：有就整份替换（草稿存过的），没有就插入 */
    private void saveFormData(Long instanceId, Long formVersionId, FormValues values) {
        long updated = context.getDb().updatable(FormDataEntity.class)
            .setColumns(f -> { f.formVersionId().set(formVersionId); f.values().set(values); f.rev().increment(); })
            .whereById(instanceId)
            .executeRows();
        if (updated == 1) return;
        FormDataEntity data = new FormDataEntity();
        data.setInstanceId(instanceId);
        data.setFormVersionId(formVersionId);
        data.setValues(values);
        data.setRev(0);
        context.getDb().insertable(data).executeRows();
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
