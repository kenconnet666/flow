package org.dpdns.zerodep.flow.core.view;

import java.util.List;

/**
 * 发起页要的：流程本身与开始节点上的表单。
 *
 * @param process 流程
 * @param form    开始节点上的表单；流程没有表单时为 null
 * @param picks   发起时要选处理人的第一步（0005 的 5.6）；没有时是空的
 */
public record StartView(StartableProcess process, FormView form, List<PickView> picks) { }
