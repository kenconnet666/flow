package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.command.CompleteCommand;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.store.FormDataStore.StoredFormData;
import org.dpdns.zerodep.flow.core.store.InstanceStore.InstanceRecord;
import org.dpdns.zerodep.flow.core.store.WorkItemStore.Completion;
import org.dpdns.zerodep.flow.model.definition.Outcome;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.runtime.FlowEvent;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance;
import org.dpdns.zerodep.flow.model.runtime.WorkItem;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 处理一个待办（0006 的 3.3）。在一个事务里，按 0007 第 5 节"推进"的顺序：
 * <ol>
 *   <li>锁住实例行（版本加一）：并发的处理在这里排队，后到的看到的是前一个处理完的样子；</li>
 *   <li>重读待办：已处理、已取消的报 {@code flow.work-item-not-open}（重复提交）；还在收件箱里的，候选人处理就是先认领；</li>
 *   <li>核对处理结果与意见：结果要是节点上写的一个，意见必填的要填；</li>
 *   <li>收表单：只取这个节点上能改的字段，其余用存着的值；</li>
 *   <li>待办改成"已处理"（带条件的更新，改到 0 行就是别人先处理了）；</li>
 *   <li>往下走：委派、加签生成的待办办完了按 {@link SubItems} 回到原来的待办；原来的待办还在等加签的人时先不走；
 *       会签节点先看结束了没有（{@link Countersign}：还要等别人、轮到下一个人、或按某个结果结束）；
 *       节点结束了按结果的种类往下：同意、否决、退回、提级按出边流转，终止直接结束实例；</li>
 *   <li>重算标题与摘要列，写回。</li>
 * </ol>
 */
public final class CompleteHandler {
    private final EngineContext context;
    private final DefinitionCache definitions;
    private final ClaimHandler claims;

    public CompleteHandler(EngineContext context, DefinitionCache definitions, ClaimHandler claims) {
        this.context = context;
        this.definitions = definitions;
        this.claims = claims;
    }

    /** 处理；出错时抛带业务错误码的异常，整个事务回滚 */
    public CommandResult handle(Actor actor, CompleteCommand command) {
        return context.db().transaction(() -> {
            // 1. 锁实例
            WorkItem found = context.stores().workItems().find(command.workItemId())
                .orElseThrow(() -> FlowErrors.notFound("workItem", command.workItemId()));
            int rev = context.stores().instances().lock(found.instanceId())
                .orElseThrow(() -> FlowErrors.notFound("instance", found.instanceId()));
            InstanceRecord instance = context.stores().instances().find(found.instanceId()).orElseThrow();
            ClaimHandler.requireActive(instance);

            // 2. 锁住之后重读待办：并发的另一次处理可能刚刚改了它
            WorkItem item = context.stores().workItems().find(command.workItemId()).orElseThrow();
            if (!WorkItem.OPEN.equals(item.state())) throw FlowErrors.notOpen(item.id());
            if (item.assigneeId() == null) claims.claim(actor, item);
            else if (!item.assigneeId().equals(actor.id())) throw FlowErrors.notCandidate(item.id());

            // 3. 结果与意见
            LoadedDefinition definition = definitions.of(instance);
            ProcessNode.Task task = (ProcessNode.Task) definition.graph().node(item.node());
            Outcome outcome = chosenOutcome(task, command.outcome());
            checkComment(outcome, command.comment());

            // 4. 表单
            Instant now = context.clock().instant();
            FormValues stored = context.stores().formData().read(instance.id()).map(StoredFormData::values).orElse(FormValues.empty());
            Execution run = new Execution(context, actor, now, definition, instance, stored, rev);
            if (definition.hasForm()) {
                run.form(FormSubmission.merge(definition.manifest(), task.form(), false, stored, command.form(), run::read));
                context.stores().formData().update(instance.id(), run.form());
            }

            // 5. 待办改成已处理
            String outcomeKey = outcome == null ? command.outcome() : outcome.key();
            Completion completion = new Completion(outcomeKey, command.comment(), actor.id(), actor.name(), now);
            if (!context.stores().workItems().complete(item.id(), completion)) throw FlowErrors.notOpen(item.id());
            run.closed.add(item);
            // 结果的名字存快照（同处理人的名字）：流转记录上显示"通过"而不是 pass，以后改了定义也不变
            Map<String, Object> data = outcome == null ? Map.of() : Map.of(FlowEvent.OUTCOME_LABEL, outcome.label());
            run.addEvent(FlowEvent.WORK_ITEM_COMPLETED, task, null, outcomeKey, item.id(), command.comment(), data);

            // 6. 往下走
            Outcome chosen = outcome != null ? outcome : implicitApprove(outcomeKey);
            if (item.parentItemId() != null) {
                // 委派、加签的人办完：回到原来的待办（SubItems）；后加签、并加的都办完了才按原来的人的结果往下
                SubItems.childDone(context, run, task, item, (parent, parentOutcome) -> settle(run, task, parent, parentOutcome, command.comment()));
            } else if (!SubItems.holdsParent(context, run, task, item, chosen)) {
                settle(run, task, item, chosen, command.comment());
            }

            // 7. 写回
            run.flush(null);
            return new CommandResult(run.instance().view(), run.created, run.closed, run.events);
        });
    }

    /**
     * 一个待办（会签时是其中一份）按这个结果办完了，节点怎样往下：不是会签时直接往下；会签时先问 {@link Countersign}——
     * 还要等别人、轮到下一个人、或按某个结果结束（结束时取消其余的待办）。
     */
    private void settle(Execution run, ProcessNode.Task task, WorkItem item, Outcome chosen, String comment) {
        if (task.multi() == null) {
            goOn(run, task, chosen, comment);
            return;
        }
        // 这一轮的会签待办：不含转办取消掉的、委派加签挂在下面的
        List<WorkItem> round = context.stores().workItems().ofInstance(item.instanceId()).stream()
            .filter(i -> i.node().equals(task.id()) && i.round() == item.round() && i.parentItemId() == null)
            .filter(i -> !WorkItem.CANCELED.equals(i.state())).toList();
        Countersign.Decision decision = Countersign.decide(task.multi(), chosen, round, run.instance().state().queue(task.id()).size());
        if (decision instanceof Countersign.Decision.Next) {
            run.nextSigner(task, round.size() + 1);
        } else if (decision instanceof Countersign.Decision.Finish finish) {
            run.closeCountersign(task);
            goOn(run, task, chosenOutcome(task, finish.outcome()), comment);
        }
        // Wait：还要等别人，这一步只记下这个人的处理
    }

    /**
     * 节点按这个结果往下走：同意时被退回过、退回它的节点要求直接回去就直接回去，否则照常往下（Execution.proceed）；
     * 否决、退回、提级按结果选线（退回、提级的线带 kind，Execution.leave 按它分配处理人）；终止直接结束实例。
     */
    private static void goOn(Execution run, ProcessNode.Task task, Outcome outcome, String comment) {
        switch (outcome.kindOrDefault()) {
            case "approve" -> run.proceed(task.id(), outcome.key());
            case "reject", "return", "escalate" -> run.leave(task.id(), outcome.key());
            case "terminate" -> run.finish(ProcessInstance.TERMINATED, outcome.result(), FlowEvent.INSTANCE_TERMINATED, comment);
            default -> throw new UnsupportedOperationException("outcome kind '" + outcome.kindOrDefault() + "' is not supported");
        }
    }

    /** 节点没写处理结果时，提交的结果当作一个"同意"（按没写结果的出边走） */
    private static Outcome implicitApprove(String key) {
        return new Outcome(key, key, null, null, null, false, List.of(), false, null, null);
    }

    /** 选的结果：节点写了结果时必须是其中一个；节点没写结果时为 null（按没写结果的出边走） */
    private static Outcome chosenOutcome(ProcessNode.Task task, String key) {
        if (task.outcomes().isEmpty()) return null;
        Outcome outcome = task.outcomes().stream().filter(o -> o.key().equals(key)).findFirst()
            .orElseThrow(() -> FlowErrors.invalid("outcome", "unknown-outcome"));
        if (!outcome.guards().isEmpty()) {
            throw new UnsupportedOperationException("outcome guards " + outcome.guards() + " are not supported yet");
        }
        return outcome;
    }

    /** 结果要求写意见时（{@code comment: "required"}）不能空 */
    private static void checkComment(Outcome outcome, String comment) {
        if (outcome != null && "required".equals(outcome.comment()) && (comment == null || comment.isBlank())) {
            throw FlowErrors.invalid("comment", "required");
        }
    }
}
