package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.entity.WorkItemEntity;
import org.dpdns.zerodep.flow.core.view.WorkItemVO;
import org.dpdns.zerodep.flow.model.definition.Outcome;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * 委派、加签生成的待办（挂在原来的待办下面，0006 的 3.3）怎样回到原来的待办：
 * <pre>
 *   addMode    原来的待办在等什么                         子待办都办完了
 *   delegate   等着（waiting），被委派的人先办            原来的待办打开，回到原来的处理人手里
 *   before     等着（waiting），加的人先办                同上
 *   parallel   照常打开，一起办                           原来的人也办完了，节点按原来的人的结果往下走
 *   after      照常打开；加的人的待办先等着               原来的人办完时打开它们；都办完了按原来的人的结果往下走
 * </pre>
 * 加的人、被委派的人办的结果只是意见（记在流转记录里），节点按原来的人的结果往下走。
 */
final class SubItems {
    private SubItems() { }

    /**
     * 一个子待办办完了。
     *
     * @param settle 节点按原来的待办的结果往下走（会签时先看会签结束了没有）：后加签、并加的人都办完、原来的人也办完时调用
     */
    static void childDone(EngineContext context, Execution run, ProcessNode.Task task, WorkItemVO child,
                          BiConsumer<WorkItemVO, Outcome> settle) {
        WorkItemVO parent = WorkItems.find(context.db(), child.getParentItemId());
        switch (child.getAddMode()) {
            case "delegate", "before" -> resumeIfSettled(context, run, parent);
            case "after", "parallel" -> {
                if (WorkItemEntity.DONE.equals(parent.getState()) && !hasPending(context, parent)) settle.accept(parent, outcomeOf(task, parent));
            }
            default -> throw new IllegalStateException("unknown add mode " + child.getAddMode());
        }
    }

    /**
     * 原来的待办办完了：有还在办的并加的人、或等着的后加签的人时，节点先不往下走（返回 true）；
     * 后加签的人的待办这时打开。原来的人选了非同意的结果（否决、退回……）时不等加签的人，把它们取消、照常往下走。
     */
    static boolean holdsParent(EngineContext context, Execution run, ProcessNode.Task task, WorkItemVO parent, Outcome chosen) {
        List<WorkItemVO> pending = WorkItems.children(context.db(), parent.getId()).stream()
            .filter(c -> WorkItemEntity.PENDING.contains(c.getState())).toList();
        if (pending.isEmpty()) return false;
        if (!"approve".equals(chosen.kindOrDefault())) {
            for (WorkItemVO child : pending) {
                WorkItems.cancel(context.db(), child.getId(), run.now());
                run.closed.add(child);
                run.addEvent(EventEntity.WORK_ITEM_CANCELED, task, null, null, child.getId(), null);
            }
            return false;
        }
        for (WorkItemVO child : pending) {
            if ("after".equals(child.getAddMode())) WorkItems.changeState(context.db(), child.getId(), WorkItemEntity.WAITING, WorkItemEntity.OPEN);
        }
        return true;
    }

    /** 委派、前加签的人都办完了（或都减掉了）：原来的待办打开，回到原来的处理人手里 */
    static void resumeIfSettled(EngineContext context, Execution run, WorkItemVO parent) {
        if (!WorkItemEntity.WAITING.equals(parent.getState()) || hasPending(context, parent)) return;
        WorkItems.changeState(context.db(), parent.getId(), WorkItemEntity.WAITING, WorkItemEntity.OPEN);
        run.addEvent(EventEntity.WORK_ITEM_RESUMED, run.graph().node(parent.getNode()), null, null, parent.getId(), null);
    }

    /** 还有没办完的子待办（打开着的，或后加签还在等的） */
    private static boolean hasPending(EngineContext context, WorkItemVO parent) {
        return context.db().queryable(WorkItemEntity.class)
            .where(w -> { w.parentItemId().eq(parent.getId()); w.state().in(WorkItemEntity.PENDING); })
            .any();
    }

    /** 原来的待办选的结果 */
    private static Outcome outcomeOf(ProcessNode.Task task, WorkItemVO parent) {
        return task.outcomes().stream().filter(o -> o.key().equals(parent.getOutcome())).findFirst()
            .orElseGet(() -> new Outcome(parent.getOutcome(), parent.getOutcome(), null, null, null, false, List.of(), false, null, null));
    }
}
