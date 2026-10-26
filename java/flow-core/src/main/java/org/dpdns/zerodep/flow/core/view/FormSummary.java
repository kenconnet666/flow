package org.dpdns.zerodep.flow.core.view;

/**
 * 管理端的表单列表的一行。
 *
 * @param key           表单标识
 * @param name          名称
 * @param category      分类
 * @param status        {@code active} 能被流程选用，{@code disabled} 停用（已经引用它的流程照常用）
 * @param latestVersion 最新发布的版本号
 */
public record FormSummary(String key, String name, String category, String status, int latestVersion) { }
