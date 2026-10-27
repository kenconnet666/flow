package org.dpdns.zerodep.flow.core.extension;

import lombok.Data;
import org.dpdns.zerodep.flow.core.view.workitem.WorkItemVO;

import java.util.List;

/**
 * 会签的完成规则（0005 的 5.2，定义里 {@code "multi": { "complete": "名字" }}）：内置的 all、any、sequential、ratio、votes 之外，
 * 接入方按名字注册。每有一个人处理完就问一次：返回一个结果的键，节点就按它结束（其余还开着的待办取消）；返回 null 再等别人。
 * 都处理完了还返回 null 时按 {@code fallback} 结束，没写 fallback 时按最后这个人选的。自定义规则的参与人一起处理（不是依次）。
 */
public interface CountersignRule {
    /** 定义里写的名字 */
    String name();

    /** 刚有一个人处理完：按哪个结果结束节点；还要等别人时返回 null */
    String decide(Context context);

    /** 问的时候能拿到的。 */
    @Data
    static class Context {
        /** 节点 */
        private final String node;
        /** 刚处理完的人选的结果 */
        private final String chosen;
        /** 这个节点这一轮的全部待办（含刚处理完的）：处理完的有 outcome，权重在 weight */
        private final List<WorkItemVO> items;
        /** 还有人没处理完 */
        private final boolean someoneOpen;
    }
}
