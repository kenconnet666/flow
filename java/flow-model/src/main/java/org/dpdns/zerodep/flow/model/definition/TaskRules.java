package org.dpdns.zerodep.flow.model.definition;

/** 人工节点的时限之外的规则（0005 的 5.3–5.5），各是一个小 record */
public final class TaskRules {
    private TaskRules() { }

    /**
     * 超时怎么办。
     *
     * @param action  {@code complete}（自动按 outcome 完成）、{@code escalate}、{@code mark}
     * @param outcome {@code complete} 时的结果
     */
    public record Overdue(String action, String outcome) { }

    /**
     * 提醒：{@code before} 到期前；{@code every} 超时后每隔多久，最多 {@code times} 次。
     *
     * @param before 到期前多久；与 every 二选一
     * @param every  超时后每隔多久
     * @param times  最多几次；null 时不限
     */
    public record Reminder(Duration before, Duration every, Integer times) { }

    /**
     * 进收件箱后多久没人认领怎么办。
     *
     * @param after  多久
     * @param action {@code assignByManager} 或 {@code remind}
     */
    public record Unclaimed(Duration after, String action) { }

    /** 允许的动作，没写的默认允许 */
    public record AllowedActions(boolean transfer, boolean delegate, boolean addSign, boolean withdraw, boolean remind) {
        /** 全部允许 */
        public static final AllowedActions ALL = new AllowedActions(true, true, true, true, true);
    }
}
