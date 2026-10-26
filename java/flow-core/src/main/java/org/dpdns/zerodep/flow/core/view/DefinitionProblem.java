package org.dpdns.zerodep.flow.core.view;

import java.util.Map;

/**
 * 校验定义（不发布）找出的一个问题（0005 第 7 节的问题代号）：设计、编辑定义时一条条标在节点、连线上。
 *
 * @param code     问题代号（{@code gateway-unpaired}……）
 * @param severity {@code error} 不能发布，{@code warning} 多半是画漏了
 * @param node     出问题的节点
 * @param edge     出问题的连线
 * @param lane     出问题的泳道
 * @param field    出问题的字段
 * @param params   问题的参数（引用的名字、类型……）
 */
public record DefinitionProblem(String code, String severity, String node, String edge, String lane, String field, Map<String, String> params) { }
