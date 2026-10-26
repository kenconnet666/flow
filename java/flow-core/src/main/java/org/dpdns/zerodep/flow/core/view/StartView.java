package org.dpdns.zerodep.flow.core.view;

/**
 * 发起页要的：流程本身与开始节点上的表单。
 *
 * @param process 流程
 * @param form    开始节点上的表单；流程没有表单时为 null
 */
public record StartView(StartableProcess process, FormView form) { }
