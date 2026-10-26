package org.dpdns.zerodep.flow.model.definition;

import java.util.List;

/**
 * 人工节点的一个处理结果（一个按钮，0005 的 5.1）。
 *
 * @param key         标识，节点内唯一
 * @param label       按钮上的文字
 * @param kind        {@code approve}（默认）、{@code reject}、{@code return}、{@code escalate}、{@code terminate}
 * @param comment     意见：{@code required} / {@code optional} / null（不显示）
 * @param attachments 附件：同上
 * @param veto        会签时一票否决
 * @param guards      提交前的守卫：接入方注册的名字
 * @param confirm     点了先确认
 * @param result      只用于 {@code terminate}：终止后实例的结束结果
 * @param target      只用于 {@code return}：{@code choose} 时处理时选退回的目标
 */
public record Outcome(String key, String label, String kind, String comment, String attachments, boolean veto,
                      List<String> guards, boolean confirm, String result, String target) {
    public Outcome { guards = guards == null ? List.of() : List.copyOf(guards); }

    /** 种类，没写时是 {@code approve} */
    public String kindOrDefault() { return kind == null ? "approve" : kind; }

    /** 处理时选退回到哪个节点（{@code kind: "return"}、{@code target: "choose"}）：不走出边 */
    public boolean choosesTarget() { return "return".equals(kind) && "choose".equals(target); }
}
