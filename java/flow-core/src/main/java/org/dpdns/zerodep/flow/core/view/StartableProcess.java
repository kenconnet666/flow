package org.dpdns.zerodep.flow.core.view;

/**
 * 我能发起的一个流程（发起列表上的一项）。
 *
 * @param key      流程标识
 * @param name     名称
 * @param category 分类
 * @param version  最新发布的版本号
 */
public record StartableProcess(String key, String name, String category, int version) { }
