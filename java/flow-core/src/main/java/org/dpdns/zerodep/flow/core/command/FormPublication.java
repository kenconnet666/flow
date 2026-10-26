package org.dpdns.zerodep.flow.core.command;

/**
 * 发布一张表单（0006 的 3.5，表单的内容见 0004 的 1.1、1.5）。版本号由后端给。
 *
 * @param key             表单标识
 * @param name            名称
 * @param category        分类
 * @param manifest        字段清单的 JSON 文字
 * @param clientSource    前端源码（TSX）
 * @param clientCompiled  前端的编译结果
 * @param compilerVersion 编译它的编译器版本
 * @param serverLanguage  后端代码的语言：{@code java}、{@code ts}；没有后端代码时为 null
 * @param serverSource    后端源码
 * @param note            发布说明
 */
public record FormPublication(String key, String name, String category, String manifest, String clientSource, String clientCompiled,
                              String compilerVersion, String serverLanguage, String serverSource, String note) { }
