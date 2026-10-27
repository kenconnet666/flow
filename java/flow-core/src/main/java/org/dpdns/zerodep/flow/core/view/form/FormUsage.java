package org.dpdns.zerodep.flow.core.view.form;

import lombok.Data;

/** 用了某张表单的流程（最新发布的版本引用它）：表单设计器按某个节点的权限预览时列出来，发布表单后提示哪些流程还在用旧版本 */
@Data
public class FormUsage {
    private final String processKey;
    private final String processName;
    /** 引用这张表单的流程版本（最新发布的） */
    private final int version;
    /** 那个流程版本发布时定下的表单版本：比表单最新的旧时，要重新发布流程，新发起的才用新表单 */
    private final int formVersion;
}
