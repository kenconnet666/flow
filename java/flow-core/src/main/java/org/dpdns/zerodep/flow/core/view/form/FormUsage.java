package org.dpdns.zerodep.flow.core.view.form;

import lombok.Data;

/** 用了某张表单的流程（最新发布的版本引用它）：表单设计器按某个节点的权限预览时列出来 */
@Data
public class FormUsage {
    private final String processKey;
    private final String processName;
    /** 引用这张表单的流程版本（最新发布的） */
    private final int version;
}
