package org.dpdns.zerodep.flow.core.engine.handler;

import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.command.CompleteCommand;
import org.dpdns.zerodep.flow.core.command.SaveDraftCommand;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.RunState;
import org.dpdns.zerodep.flow.core.data.Variables;
import org.dpdns.zerodep.flow.core.data.WorkItemDraft;
import org.dpdns.zerodep.flow.core.engine.DefinitionCache;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.Execution;
import org.dpdns.zerodep.flow.core.engine.FlowErrors;
import org.dpdns.zerodep.flow.core.engine.LoadedDefinition;
import org.dpdns.zerodep.flow.core.engine.form.FormSubmission;
import org.dpdns.zerodep.flow.core.engine.job.BuiltinJobs;
import org.dpdns.zerodep.flow.core.engine.rule.Countersign;
import org.dpdns.zerodep.flow.core.engine.rule.ReturnTargets;
import org.dpdns.zerodep.flow.core.engine.rule.SubItems;
import org.dpdns.zerodep.flow.core.engine.store.Instances;
import org.dpdns.zerodep.flow.core.engine.store.WorkItems;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.entity.WorkItemEntity;
import org.dpdns.zerodep.flow.core.extension.OutcomeGuard;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.view.workitem.WorkItemVO;
import org.dpdns.zerodep.flow.model.definition.Outcome;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.error.ErrorDetail;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 处理一个待办（0006 的 3.3）。在一个事务里，按 0007 第 5 节"推进"的顺序：
 * <ol>
 *   <li>锁住实例行（版本加一）：并发的处理在这里排队，后到的看到的是前一个处理完的样子；</li>
 *   <li>重读待办：已处理、已取消的报 {@code flow.work-item-not-open}（重复提交）；还在收件箱里的，候选人处理就是先认领；</li>
 *   <li>核对处理结果与意见：结果要是节点上写的一个，意见必填的要填；收好表单后问结果上的守卫（{@link OutcomeGuard}）；</li>
 *   <li>收表单：只取这个节点上能改的字段，其余用存着的值；</li>
 *   <li>待办改成"已处理"（带条件的更新，改到 0 行就是别人先处理了；这一步与下一步在 {@link #finishItem}，超时自动处理也走它）；</li>
 *   <li>往下走：委派、加签生成的待办办完了按 {@link SubItems} 回到原来的待办；原来的待办还在等加签的人时先不走；
 *       会签节点先看结束了没有（{@link Countersign}：还要等别人、轮到下一个人、或按某个结果结束）；
 *       节点结束了按结果的种类往下：同意、否决、退回、提级按出边流转，终止直接结束实例；</li>
 *   <li>重算标题与摘要列，写回。</li>
 * </ol>
 */
public class CompleteHandler {
    private final EngineContext context;
    private final DefinitionCache definitions;
    private final ClaimHandler claims;

    public CompleteHandler(EngineContext context, ClaimHandler claims) {
        this.context = context;
        this.definitions = context.getDefinitions();
        this.claims = claims;
    }

    /** 处理；出错时抛带业务错误码的异常，整个事务回滚 */
    public CommandResult handle(Actor actor, CompleteCommand command) {
        return context.transaction(() -> {
            // 1. 锁实例
            WorkItemVO found = WorkItems.find(context.getDb(), command.getWorkItemId());
            InstanceEntity instance = Instances.lock(context.getDb(), found.getInstanceId());
            ClaimHandler.requireActive(instance);
            // 处理之前的样子（实例会在推进时就地改）：记成快照，下一步还没人动时处理人能撤回
            RunState stateBefore = instance.getState();
            Variables variablesBefore = instance.getVariables();

            // 2. 锁住之后重读待办：并发的另一次处理可能刚刚改了它
            WorkItemVO item = WorkItems.find(context.getDb(), command.getWorkItemId());
            if (!WorkItemEntity.OPEN.equals(item.getState())) throw FlowErrors.notOpen(item.getId());
            if (item.getAssigneeId() == null) claims.claim(actor, item);
            else if (!item.getAssigneeId().equals(actor.getId())) throw FlowErrors.notCandidate(item.getId());

            // 3. 结果与意见
            LoadedDefinition definition = definitions.of(instance);
            ProcessNode.Task task = (ProcessNode.Task) definition.getGraph().node(item.getNode());
            Outcome outcome = chosenOutcome(task, command.getOutcome());
            checkComment(outcome, command.getComment());

            // 4. 表单
            Instant now = context.getClock().instant();
            FormValues stored = Instances.form(context.getDb(), instance.getId());
            Execution run = new Execution(context, actor, now, definition, instance, stored, instance.getRev());
            if (definition.hasForm()) {
                run.form(FormSubmission.merge(definition.getManifest(), task.getForm(), false, stored, command.getForm(), run::read));
                Instances.saveForm(context.getDb(), instance.getId(), run.form());
            }

            run.picks(command.getPicks());
            if (outcome != null && outcome.choosesTarget()) {
                run.returnTarget(ReturnTargets.check(definition, instance.getState(), item.getNode(), command.getReturnTarget()));
            }

            // 守卫：表单合并好之后、改成已处理之前问一遍，任一不通过整个处理不做
            checkGuards(actor, run, item, outcome);

            // 5、6. 待办改成已处理，往下走
            finishItem(run, task, item, outcome == null ? command.getOutcome() : outcome.getKey(), outcome, command.getComment());

            // 记下处理之前的样子：下一步还没人动时处理人能撤回（StepWithdrawal）；结束了就不再能撤回
            StepWithdrawal.remember(context.getDb(), instance, item.getId(), stateBefore, variablesBefore, stored);

            // 7. 写回
            run.flush(null);
            return run.result();
        });
    }

    /**
     * 暂存：在收件箱里的先认领给我，表单只收这个节点上能改的字段、不查必填，连同意见存在待办上；不推进、不记流转记录。
     */
    public CommandResult saveDraft(Actor actor, SaveDraftCommand command) {
        return context.transaction(() -> {
            WorkItemVO item = WorkItems.find(context.getDb(), command.getWorkItemId());
            InstanceEntity instance = Instances.find(context.getDb(), item.getInstanceId());
            ClaimHandler.requireActive(instance);
            if (item.getAssigneeId() == null) claims.claim(actor, item);
            else if (!item.getAssigneeId().equals(actor.getId())) throw FlowErrors.notCandidate(item.getId());

            LoadedDefinition definition = definitions.of(instance);
            ProcessNode.Task task = (ProcessNode.Task) definition.getGraph().node(item.getNode());
            FormValues stored = Instances.form(context.getDb(), instance.getId());
            Execution run = new Execution(context, actor, context.getClock().instant(), definition, instance, stored, instance.getRev());
            FormValues form = definition.hasForm()
                ? FormSubmission.draft(definition.getManifest(), task.getForm(), stored, command.getForm(), run::read)
                : FormValues.empty();
            String comment = command.getComment() == null || command.getComment().isBlank() ? null : command.getComment();
            WorkItemDraft draft = new WorkItemDraft(form, comment, run.now());
            long saved = context.getDb().updatable(WorkItemEntity.class)
                .setColumns(w -> w.draft().set(draft))
                .where(w -> { w.id().eq(item.getId()); w.state().eq(WorkItemEntity.OPEN); })
                .executeRows();
            if (saved != 1) throw FlowErrors.notOpen(item.getId());
            return run.result();
        });
    }

    /**
     * 待办按这个结果办完（第 5、6 步）：改成已处理、记流转记录、往下走。处理人是这一步的操作人（超时自动处理时是系统）。
     * 处理命令与超时自动处理（{@link BuiltinJobs}）共用。
     *
     * @param outcome 选的结果；节点没写处理结果时为 null（按 outcomeKey 当作一个"同意"）
     */
    public void finishItem(Execution run, ProcessNode.Task task, WorkItemVO item, String outcomeKey, Outcome outcome, String comment) {
        if (!WorkItems.complete(context.getDb(), item.getId(), outcomeKey, comment, run.actor().getId(), run.actor().getName(), run.now())) {
            throw FlowErrors.notOpen(item.getId());
        }
        run.closed.add(item);
        // 结果的名字存快照（同处理人的名字）：流转记录上显示"通过"而不是 pass，以后改了定义也不变
        Map<String, Object> data = outcome == null ? Map.of() : Map.of(EventEntity.OUTCOME_LABEL, outcome.getLabel());
        run.addEvent(EventEntity.WORK_ITEM_COMPLETED, task, null, outcomeKey, item.getId(), comment, data);

        Outcome chosen = outcome != null ? outcome : implicitApprove(outcomeKey);
        if (item.getParentItemId() != null) {
            // 委派、加签的人办完：回到原来的待办（SubItems）；后加签、并加的都办完了才按原来的人的结果往下
            SubItems.childDone(context, run, task, item, (parent, parentOutcome) -> settle(run, task, parent, parentOutcome, comment));
        } else if (!SubItems.holdsParent(context, run, task, item, chosen)) {
            settle(run, task, item, chosen, comment);
        }
    }

    /**
     * 一个待办（会签时是其中一份）按这个结果办完了，节点怎样往下：不是会签时直接往下；会签时先问 {@link Countersign}——
     * 还要等别人、轮到下一个人、或按某个结果结束（结束时取消其余的待办）。
     */
    private void settle(Execution run, ProcessNode.Task task, WorkItemVO item, Outcome chosen, String comment) {
        if (task.getMulti() == null) {
            goOn(run, task, chosen, comment);
            return;
        }
        // 这一轮的会签待办：不含转办取消掉的、委派加签挂在下面的
        List<WorkItemVO> round = context.getDb().queryable(WorkItemEntity.class)
            .where(w -> {
                w.instanceId().eq(item.getInstanceId());
                w.node().eq(task.getId());
                w.round().eq(item.getRound());
                w.parentItemId().isNull();
                w.state().ne(WorkItemEntity.CANCELED);
            })
            .orderBy(w -> { w.createdAt().asc(); w.id().asc(); })
            .select(WorkItemVO.class).toList();
        Countersign.Decision decision = Countersign.decide(task.getMulti(), chosen, round, run.instance().getState().queue(task.getId()).size());
        if (decision instanceof Countersign.Decision.Next) {
            run.nextSigner(task, round.size() + 1);
        } else if (decision instanceof Countersign.Decision.Finish finish) {
            run.closeCountersign(task);
            goOn(run, task, chosenOutcome(task, finish.getOutcome()), comment);
        }
        // Wait：还要等别人，这一步只记下这个人的处理
    }

    /**
     * 节点按这个结果往下走：同意时被退回过、退回它的节点要求直接回去就直接回去，否则照常往下（Execution.proceed）；
     * 否决、退回、提级按结果选线（退回、提级的线带 kind，Execution.leave 按它分配处理人）；处理时选退回节点的退回到选的节点
     * （会签时是让节点结束的那个人选的）；终止直接结束实例。
     */
    private static void goOn(Execution run, ProcessNode.Task task, Outcome outcome, String comment) {
        switch (outcome.kindOrDefault()) {
            case "approve" -> run.proceed(task.getId(), outcome.getKey());
            case "return" -> {
                if (outcome.choosesTarget()) run.returnTo(task.getId(), outcome.getKey());
                else run.leave(task.getId(), outcome.getKey());
            }
            case "reject", "escalate" -> run.leave(task.getId(), outcome.getKey());
            case "terminate" -> run.finish(InstanceEntity.TERMINATED, outcome.getResult(), EventEntity.INSTANCE_TERMINATED, comment);
            default -> throw new UnsupportedOperationException("outcome kind '" + outcome.kindOrDefault() + "' is not supported");
        }
    }

    /** 节点没写处理结果时，提交的结果当作一个"同意"（按没写结果的出边走） */
    private static Outcome implicitApprove(String key) {
        return new Outcome(key, key, null, null, null, false, List.of(), false, null, null);
    }

    /** 选的结果：节点写了结果时必须是其中一个；节点没写结果时为 null（按没写结果的出边走） */
    public static Outcome chosenOutcome(ProcessNode.Task task, String key) {
        if (task.getOutcomes().isEmpty()) return null;
        Outcome outcome = task.getOutcomes().stream().filter(o -> o.getKey().equals(key)).findFirst()
            .orElseThrow(() -> FlowErrors.invalid("outcome", "unknown-outcome"));
        return outcome;
    }

    /** 结果上写的守卫（接入方注册的 OutcomeGuard）都要通过；不通过的报 {@code flow.guard-rejected}，明细里是守卫的名字与原因 */
    private void checkGuards(Actor actor, Execution run, WorkItemVO item, Outcome outcome) {
        if (outcome == null || outcome.getGuards().isEmpty()) return;
        List<ErrorDetail> refused = new ArrayList<>();
        OutcomeGuard.Context guardContext = new OutcomeGuard.Context(actor, run.view(), item, outcome.getKey(), run.form().asMap());
        for (String name : outcome.getGuards()) {
            String reason = context.getExtensions().guard(name).check(guardContext);
            if (reason != null) refused.add(new ErrorDetail(name, null, item.getNode(), null, null, Map.of("reason", reason)));
        }
        if (!refused.isEmpty()) throw new FlowException(FlowErrorCode.GUARD_REJECTED, "guards refused outcome " + outcome.getKey(), refused);
    }

    /** 结果要求写意见时（{@code comment: "required"}）不能空 */
    private static void checkComment(Outcome outcome, String comment) {
        if (outcome != null && "required".equals(outcome.getComment()) && (comment == null || comment.isBlank())) {
            throw FlowErrors.invalid("comment", "required");
        }
    }
}
