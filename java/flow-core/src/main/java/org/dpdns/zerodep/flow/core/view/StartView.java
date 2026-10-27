package org.dpdns.zerodep.flow.core.view;

import lombok.Data;

import java.util.List;

/** 发起页要的：流程本身与开始节点上的表单。 */
@Data
public class StartView {
    /** 流程 */
    private final StartableProcess process;
    /** 开始节点上的表单；流程没有表单时为 null */
    private final FormView form;
    /** 发起时要选处理人的第一步（0005 的 5.6）；没有时是空的 */
    private final List<PickView> picks;
}
