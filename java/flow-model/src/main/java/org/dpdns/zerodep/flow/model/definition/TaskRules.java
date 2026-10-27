package org.dpdns.zerodep.flow.model.definition;

import lombok.Data;

/** 人工节点的时限之外的规则（0005 的 5.3–5.5），各是一个小 record */
public class TaskRules {
    private TaskRules() { }

    /** 超时怎么办。 */
    @Data
    public static class Overdue {
        /** {@code complete}（自动按 outcome 完成）、{@code escalate}、{@code mark} */
        private final String action;
        /** {@code complete} 时的结果 */
        private final String outcome;
    }

    /** 提醒：{@code before} 到期前；{@code every} 超时后每隔多久，最多 {@code times} 次。 */
    @Data
    public static class Reminder {
        /** 到期前多久；与 every 二选一 */
        private final Duration before;
        /** 超时后每隔多久 */
        private final Duration every;
        /** 最多几次；null 时不限 */
        private final Integer times;
    }

    /** 进收件箱后多久没人认领怎么办。 */
    @Data
    public static class Unclaimed {
        /** 多久 */
        private final Duration after;
        /** {@code assignByManager} 或 {@code remind} */
        private final String action;
    }

    /** 允许的动作，没写的默认允许 */
    @Data
    public static class AllowedActions {
        private final boolean transfer;
        private final boolean delegate;
        private final boolean addSign;
        private final boolean withdraw;
        private final boolean remind;

        /** 全部允许 */
        public static final AllowedActions ALL = new AllowedActions(true, true, true, true, true);
    }
}
