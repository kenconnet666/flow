package org.dpdns.zerodep.flow.model.definition;

import lombok.Data;

import java.util.List;

/** 人工节点的一个处理结果（一个按钮，0005 的 5.1）。 */
@Data
public class Outcome {
    /** 标识，节点内唯一 */
    private final String key;
    /** 按钮上的文字 */
    private final String label;
    /** {@code approve}（默认）、{@code reject}、{@code return}、{@code escalate}、{@code terminate} */
    private final String kind;
    /** 意见：{@code required} / {@code optional} / null（不显示） */
    private final String comment;
    /** 附件：同上 */
    private final String attachments;
    /** 会签时一票否决 */
    private final boolean veto;
    /** 提交前的守卫：接入方注册的名字 */
    private final List<String> guards;
    /** 点了先确认 */
    private final boolean confirm;
    /** 只用于 {@code terminate}：终止后实例的结束结果 */
    private final String result;
    /** 只用于 {@code return}：{@code choose} 时处理时选退回的目标 */
    private final String target;

    public Outcome(String key, String label, String kind, String comment, String attachments, boolean veto, List<String> guards, boolean confirm, String result, String target) { guards = guards == null ? List.of() : List.copyOf(guards);
        this.key = key;
        this.label = label;
        this.kind = kind;
        this.comment = comment;
        this.attachments = attachments;
        this.veto = veto;
        this.guards = guards;
        this.confirm = confirm;
        this.result = result;
        this.target = target;
    }

    /** 种类，没写时是 {@code approve} */
    public String kindOrDefault() { return kind == null ? "approve" : kind; }

    /** 处理时选退回到哪个节点（{@code kind: "return"}、{@code target: "choose"}）：不走出边 */
    public boolean choosesTarget() { return "return".equals(kind) && "choose".equals(target); }
}
