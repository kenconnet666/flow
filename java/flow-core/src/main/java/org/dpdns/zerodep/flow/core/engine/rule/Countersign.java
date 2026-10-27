package org.dpdns.zerodep.flow.core.engine.rule;

import lombok.Data;
import org.dpdns.zerodep.flow.core.entity.WorkItemEntity;
import org.dpdns.zerodep.flow.core.view.workitem.WorkItemVO;
import org.dpdns.zerodep.flow.model.definition.MultiInstance;
import org.dpdns.zerodep.flow.model.definition.Outcome;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 会签的一个人处理完之后，节点怎样往下（0005 的 5.2，同钉钉会签"一人拒绝即拒绝"的做法）：
 * <pre>
 *   complete     何时结束                                          按哪个结果结束
 *   all          有人选了非同意的结果、或所有人都处理完              非同意的那个；都同意时按同意
 *   sequential   同上，一个接一个                                  同上
 *   any          第一个人处理完                                    他选的
 *   ratio        选同一结果的人数占比达到 ratio                     先达到的那个；都处理完还没有就按 fallback
 *   votes        选同一结果的票数（权重之和）达到 votes             同上
 * </pre>
 * {@code veto}（一票否决）只对 ratio、votes 有意义：有人选了它，节点立即按它结束。
 * 只看这个节点这一轮的待办（同一节点再到一次是新的一轮）。
 */
public class Countersign {
    private Countersign() { }

    /** 处理完一个人之后的决定 */
    public interface Decision {
        /** 还要等别人（并行的会签有人没处理完） */
        @Data
        static class Wait implements Decision {
        }

        /** 轮到下一个人（依次会签） */
        @Data
        static class Next implements Decision {
        }

        /** 节点结束，按这个结果往下走 */
        @Data
        static class Finish implements Decision {
            private final String outcome;
        }
    }

    /**
     * 决定节点怎样往下。
     *
     * @param multi   节点上的会签规则
     * @param chosen  刚处理完的人选的结果
     * @param items   这个节点这一轮的全部待办（含刚处理完的那个）
     * @param waiting 依次会签还没轮到的人数
     */
    public static Decision decide(MultiInstance multi, Outcome chosen, List<WorkItemVO> items, int waiting) {
        boolean approve = "approve".equals(chosen.kindOrDefault());
        boolean someoneOpen = items.stream().anyMatch(i -> WorkItemEntity.OPEN.equals(i.getState()) || WorkItemEntity.WAITING.equals(i.getState())) || waiting > 0;
        return switch (multi.getComplete()) {
            case "any" -> new Decision.Finish(chosen.getKey());
            case "all" -> !approve || !someoneOpen ? new Decision.Finish(chosen.getKey()) : new Decision.Wait();
            case "sequential" -> !approve ? new Decision.Finish(chosen.getKey()) : waiting > 0 ? new Decision.Next() : new Decision.Finish(chosen.getKey());
            case "ratio", "votes" -> threshold(multi, chosen, items, someoneOpen);
            default -> throw new UnsupportedOperationException("countersign rule '" + multi.getComplete() + "' is not supported");
        };
    }

    /** 票签：一票否决立即结束；这个结果达到门槛就按它结束；都处理完还没有达到的就按 fallback */
    private static Decision threshold(MultiInstance multi, Outcome chosen, List<WorkItemVO> items, boolean someoneOpen) {
        if (chosen.isVeto()) return new Decision.Finish(chosen.getKey());
        List<WorkItemVO> same = items.stream().filter(i -> chosen.getKey().equals(i.getOutcome())).toList();
        boolean reached = "ratio".equals(multi.getComplete())
            ? BigDecimal.valueOf(same.size()).divide(BigDecimal.valueOf(items.size()), 6, RoundingMode.HALF_UP).compareTo(multi.getRatio()) >= 0
            : same.stream().mapToInt(i -> i.getWeight() == null ? 1 : i.getWeight()).sum() >= multi.getVotes();
        if (reached) return new Decision.Finish(chosen.getKey());
        return someoneOpen ? new Decision.Wait() : new Decision.Finish(multi.getFallback());
    }
}
