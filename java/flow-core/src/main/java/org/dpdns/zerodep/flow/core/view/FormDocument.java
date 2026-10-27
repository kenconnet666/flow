package org.dpdns.zerodep.flow.core.view;

import lombok.Data;

/** 表单的一个版本的全部内容（管理端的表单编辑载入它，改完再发布成新版本；0004 的 5.2）。不含编译结果：发布时重新编译。 */
@Data
public class FormDocument {
    /** 表单标识 */
    private final String key;
    /** 名称（表单现在的） */
    private final String name;
    /** 分类（表单现在的） */
    private final String category;
    /** 版本号 */
    private final int version;
    /** 字段清单的 JSON 文字 */
    private final String manifest;
    /** 前端源码（TSX）；只有字段清单、自动排版的表单为 null */
    private final String clientSource;
    /** 后端代码的语言：{@code java}、{@code ts}；没有后端代码时为 null */
    private final String serverLanguage;
    /** 后端源码 */
    private final String serverSource;
}
