package org.dpdns.zerodep.flow.core.view;

/**
 * 管理端的流程列表的一行。
 *
 * @param key           流程标识
 * @param name          名称
 * @param category      分类
 * @param status        {@code active} 能发起，其余停用
 * @param latestVersion 最新发布的版本号
 */
public record ProcessSummary(String key, String name, String category, String status, int latestVersion) { }
