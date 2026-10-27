package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

/** 发布一张表单（0006 的 3.5，表单的内容见 0004 的 1.1、1.5）。版本号由后端给。 */
@Data
public class FormPublication {
    /** 表单标识 */
    private final String key;
    /** 名称 */
    private final String name;
    /** 分类 */
    private final String category;
    /** 字段清单的 JSON 文字 */
    private final String manifest;
    /** 前端源码（TSX） */
    private final String clientSource;
    /** 前端的编译结果 */
    private final String clientCompiled;
    /** 编译它的编译器版本 */
    private final String compilerVersion;
    /** 后端代码的语言：{@code java}、{@code ts}；没有后端代码时为 null */
    private final String serverLanguage;
    /** 后端源码 */
    private final String serverSource;
    /** 发布说明 */
    private final String note;
}
