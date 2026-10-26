package org.dpdns.zerodep.flow.model.runtime;

import java.util.Map;

/**
 * 收件箱的一行（0006 第 4 节的 {@code todo}）：待办，加上它所在实例的标题与摘要列（0005 第 2 节的 {@code summary}），
 * 列表不用再一个个去查实例。
 *
 * @param workItem 待办
 * @param title    实例标题
 * @param summary  实例的摘要列：{@code { ref }} 的名字 → 值
 */
public record TodoItem(WorkItem workItem, String title, Map<String, Object> summary) { }
