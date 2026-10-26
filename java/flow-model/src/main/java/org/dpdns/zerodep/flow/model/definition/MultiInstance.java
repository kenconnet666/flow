package org.dpdns.zerodep.flow.model.definition;

import java.math.BigDecimal;
import java.util.List;

/**
 * 会签（0005 的 5.2）。
 *
 * @param participants 参与的人：来源列表（{@code List<AssigneeSource>}），或一个 {@link Ref}
 * @param complete     完成规则：{@code all}、{@code any}、{@code ratio}、{@code votes}、{@code sequential}，或接入方注册的规则名
 * @param ratio        {@code ratio}：比例（0–1）
 * @param votes        {@code votes}：票数
 * @param fallback     {@code ratio}、{@code votes}：都没达到门槛时按哪个结果
 */
public record MultiInstance(Object participants, String complete, BigDecimal ratio, Integer votes, String fallback) {
    /** 内置的完成规则 */
    public static final List<String> BUILTIN = List.of("all", "any", "ratio", "votes", "sequential");

    /** 参与人是来源列表时返回它，是引用时返回空列表 */
    @SuppressWarnings("unchecked")
    public List<AssigneeSource> participantSources() {
        return participants instanceof List<?> list ? (List<AssigneeSource>) list : List.of();
    }
}
