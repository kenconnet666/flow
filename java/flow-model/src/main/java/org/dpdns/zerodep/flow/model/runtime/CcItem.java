package org.dpdns.zerodep.flow.model.runtime;

import java.util.Map;

/**
 * "抄送我的"的一行（0006 第 4 节的 {@code cc}）：抄送，加上它所在实例的标题、摘要列与状态（同待办列表的行）。
 *
 * @param cc      抄送
 * @param title   实例标题
 * @param summary 实例的摘要列
 * @param status  实例的状态
 */
public record CcItem(Cc cc, String title, Map<String, Object> summary, String status) { }
