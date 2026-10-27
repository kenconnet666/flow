package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

/**
 * 保存设计中的草稿（0006 的 3.5 {@code saveDraft}，0004 的 5.2）：流程设计器、表单编辑器自动保存。
 * 第一次保存不带 {@code rev}；之后带上次读到、存到的版本，别人在这之间存过时报 {@code flow.concurrent-modification}，不覆盖。
 */
@Data
public class SaveDesignDraftCommand {
    /** {@code process} 流程定义，或 {@code form} 表单 */
    private final String kind;
    /** 流程或表单的标识 */
    private final String key;
    /** 名称 */
    private final String name;
    /** 设计器的内容：流程是定义的 JSON，表单是源码与清单（引擎不解释） */
    private final String content;
    /** 基于哪个已发布的版本改的；新建的为 null */
    private final Integer baseVersion;
    /** 上次读到、存到的版本；第一次保存为 null */
    private final Integer rev;
}
