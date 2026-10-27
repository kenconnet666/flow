package org.dpdns.zerodep.flow.core.view;

import lombok.Data;

import java.util.Map;

/** 校验定义（不发布）找出的一个问题（0005 第 7 节的问题代号）：设计、编辑定义时一条条标在节点、连线上。 */
@Data
public class DefinitionProblem {
    /** 问题代号（{@code gateway-unpaired}……） */
    private final String code;
    /** {@code error} 不能发布，{@code warning} 多半是画漏了 */
    private final String severity;
    /** 出问题的节点 */
    private final String node;
    /** 出问题的连线 */
    private final String edge;
    /** 出问题的泳道 */
    private final String lane;
    /** 出问题的字段 */
    private final String field;
    /** 问题的参数（引用的名字、类型……） */
    private final Map<String, String> params;
}
