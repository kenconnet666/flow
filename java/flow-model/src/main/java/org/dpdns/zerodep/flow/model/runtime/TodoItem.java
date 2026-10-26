package org.dpdns.zerodep.flow.model.runtime;

import java.util.Map;

/**
 * 待办列表的一行（0006 第 4 节的 {@code todo}、{@code done}）：待办，加上它所在实例的标题、摘要列（0005 第 2 节的 {@code summary}）
 * 与状态，列表不用再一个个去查实例。
 *
 * @param workItem     待办
 * @param title        实例标题
 * @param summary      实例的摘要列：{@code { ref }} 的名字 → 值
 * @param status       实例的状态（我已办里看流程走到哪了）
 * @param outcomeLabel 处理结果的名字（定义里 outcome 的 label）；还没处理时为 null
 */
public record TodoItem(WorkItem workItem, String title, Map<String, Object> summary, String status, String outcomeLabel) { }
