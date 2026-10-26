package org.dpdns.zerodep.flow.core.view;

/**
 * 表单的一个版本的全部内容（管理端的表单编辑载入它，改完再发布成新版本；0004 的 5.2）。不含编译结果：发布时重新编译。
 *
 * @param key            表单标识
 * @param name           名称（表单现在的）
 * @param category       分类（表单现在的）
 * @param version        版本号
 * @param manifest       字段清单的 JSON 文字
 * @param clientSource   前端源码（TSX）；只有字段清单、自动排版的表单为 null
 * @param serverLanguage 后端代码的语言：{@code java}、{@code ts}；没有后端代码时为 null
 * @param serverSource   后端源码
 */
public record FormDocument(String key, String name, String category, int version, String manifest, String clientSource,
                           String serverLanguage, String serverSource) { }
