package org.dpdns.zerodep.flow.core.engine.query;

import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.WorkItemDraft;
import org.dpdns.zerodep.flow.core.engine.DefinitionCache;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.LoadedDefinition;
import org.dpdns.zerodep.flow.core.engine.assignee.CandidateRules;
import org.dpdns.zerodep.flow.core.engine.assignee.PickPreview;
import org.dpdns.zerodep.flow.core.engine.form.References;
import org.dpdns.zerodep.flow.core.engine.handler.StepWithdrawal;
import org.dpdns.zerodep.flow.core.engine.rule.ReturnTargets;
import org.dpdns.zerodep.flow.core.engine.store.Instances;
import org.dpdns.zerodep.flow.core.engine.store.WorkItems;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.entity.WorkItemEntity;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.view.form.FormView;
import org.dpdns.zerodep.flow.core.view.workitem.DraftVO;
import org.dpdns.zerodep.flow.core.view.workitem.PickView;
import org.dpdns.zerodep.flow.core.view.workitem.WorkItemVO;
import org.dpdns.zerodep.flow.core.view.workitem.WorkItemView.OutcomeView;
import org.dpdns.zerodep.flow.core.view.workitem.WorkItemView;
import org.dpdns.zerodep.flow.model.definition.Outcome;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** 待办详情：待办与实例、这个节点上的表单、能选的处理结果，以及我现在能认领、处理、协作什么。 */
public class WorkItemQueries {
    private final EngineContext context;
    private final DefinitionCache definitions;
    private final FormQueries forms;

    public WorkItemQueries(EngineContext context) {
        this.context = context;
        this.definitions = context.getDefinitions();
        this.forms = new FormQueries(context);
    }

    /**
     * 待办详情：待办与实例、这个节点上的表单（按节点权限去掉隐藏的字段）、能选的处理结果，以及我现在能不能认领、处理。
     * 待办已处理、实例已结束时照样能看，只是两样都不能做。
     */
    public WorkItemView workItem(Actor actor, Long workItemId) {
        WorkItemVO item = WorkItems.find(context.getDb(), workItemId);
        InstanceEntity instance = Instances.find(context.getDb(), item.getInstanceId());
        LoadedDefinition definition = definitions.of(instance);
        ProcessNode.Task task = (ProcessNode.Task) definition.getGraph().node(item.getNode());

        boolean open = WorkItemEntity.OPEN.equals(item.getState()) && InstanceEntity.ACTIVE.equals(instance.getStatus());
        boolean mine = actor.getId().equals(item.getAssigneeId());
        boolean candidate = item.getAssigneeId() == null && CandidateRules.isCandidate(actor, WorkItems.candidates(context.getDb(), item.getId()));

        FormValues stored = Instances.form(context.getDb(), instance.getId());
        Function<String, Object> read = name -> References.read(name, instance, stored);
        // 我暂存过的接着填：表单显示暂存的值
        WorkItemDraft draft = open && mine
            ? context.getDb().queryable(WorkItemEntity.class).whereById(item.getId()).selectColumn(w -> w.draft()).singleOrNull()
            : null;
        FormValues shown = draft == null ? stored : stored.with(draft.getForm());
        FormView form = definition.hasForm() ? forms.formView(definition, task.getForm(), false, shown, read) : null;
        boolean completable = open && (mine || candidate);
        PickPreview preview = new PickPreview(definition.getGraph(), context);
        List<WorkItemView.ReturnTarget> targets = completable && task.getOutcomes().stream().anyMatch(Outcome::choosesTarget)
            ? returnTargets(definition, instance, item.getNode()) : List.of();
        // 选节点的退回在没有能退回的节点时（还在第一个人工节点上）不给：点了也做不成
        List<OutcomeView> outcomes = task.getOutcomes().stream()
            .filter(o -> !(completable && o.choosesTarget() && targets.isEmpty()))
            .map(o -> outcomeView(o, completable ? preview.after(task.getId(), o.getKey(), read) : List.of(), o.choosesTarget() ? targets : List.of()))
            .toList();
        boolean assign = open && context.getDb().queryable(WorkItemEntity.class)
            .where(w -> { w.id().eq(item.getId()); w.designatorId().eq(actor.getId()); w.needsDesignation().eq(true); })
            .any();
        boolean withdrawStep = new StepWithdrawal(context).allowed(actor, item, definition.getGraph(), instance);
        WorkItemView.Actions actions = completable
            ? new WorkItemView.Actions(task.getAllow().isTransfer(), task.getAllow().isDelegate(), task.getAllow().isAddSign(),
                mine && WorkItemEntity.CLAIMED.equals(item.getAssignment()), assign, withdrawStep)
            : new WorkItemView.Actions(false, false, false, false, assign, withdrawStep);
        boolean requestReassign = open && mine && WorkItemEntity.DESIGNATED.equals(item.getAssignment()) && !Boolean.TRUE.equals(item.getNeedsDesignation());
        actions.setRequestReassign(requestReassign);
        return new WorkItemView(item, Instances.view(context.getDb(), instance.getId()), form, outcomes, open && candidate, completable, actions,
            draft == null ? null : new DraftVO(draft.getComment(), draft.getSavedAt()));
    }

    /** 处理结果给前端的样子：没写意见要求时是 {@code none}（不收意见，0005 的人工节点） */
    private static OutcomeView outcomeView(Outcome outcome, List<PickView> picks, List<WorkItemView.ReturnTarget> targets) {
        return new OutcomeView(outcome.getKey(), outcome.getLabel(), outcome.kindOrDefault(), outcome.getComment() == null ? "none" : outcome.getComment(),
            outcome.isConfirm(), picks, targets);
    }

    /** 退回时能选的节点，带上次在那里处理的人（取这个节点最近处理完的待办） */
    private List<WorkItemView.ReturnTarget> returnTargets(LoadedDefinition definition, InstanceEntity instance, String current) {
        List<WorkItemVO> items = WorkItems.ofInstance(context.getDb(), instance.getId());
        return ReturnTargets.of(definition, instance.getState(), current).stream().map(node -> {
            String handler = items.stream().filter(i -> i.getNode().equals(node.getId()) && i.getCompletedAt() != null)
                .max(Comparator.comparing(WorkItemVO::getCompletedAt)).map(WorkItemVO::getCompletedByName).orElse(null);
            return new WorkItemView.ReturnTarget(node.getId(), node.getName(), handler);
        }).toList();
    }

    /** 待办所在节点上第一个"同意"类的结果（批量处理没指定结果时用）；没有时为 null */
    public String approveOutcomeOf(Long workItemId) {
        WorkItemVO item = WorkItems.find(context.getDb(), workItemId);
        InstanceEntity instance = Instances.find(context.getDb(), item.getInstanceId());
        ProcessNode.Task task = (ProcessNode.Task) definitions.of(instance).getGraph().node(item.getNode());
        return task.getOutcomes().stream().filter(o -> "approve".equals(o.kindOrDefault())).map(Outcome::getKey).findFirst().orElse(null);
    }
}
