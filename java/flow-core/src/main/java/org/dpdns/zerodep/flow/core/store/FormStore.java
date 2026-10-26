package org.dpdns.zerodep.flow.core.store;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 表单本身与发布的版本（0007 的 {@code flow_form}、{@code flow_form_version}，0004 的 5.2）。默认实现是 {@link QueryStores}。
 */
public interface FormStore {
    /** 发布：表单还没有时建上，最新版本号加一，存一份不可变的版本 */
    StoredForm publish(Publication publication);

    /** 某个版本 */
    Optional<StoredForm> version(String formKey, int version);

    /** 最新发布的版本 */
    Optional<StoredForm> latest(String formKey);

    /** 按编号取一个版本 */
    Optional<StoredForm> byId(Long formVersionId);

    /** 一个版本的前端部分（源码与编译结果，可能很大，只在前端要渲染表单时读） */
    Optional<FormClient> client(Long formVersionId);

    /** 全部表单（停用的也在），按分类、名称排 */
    List<FormInfo> forms();

    /** 一张表单本身 */
    Optional<FormInfo> form(String formKey);

    /** 表单的全部版本，新的在前 */
    List<FormVersionInfo> versions(String formKey);

    /** 某个版本的源码与字段清单（管理端编辑时载入） */
    Optional<FormSource> source(String formKey, int version);

    /** 停用、启用；表单不存在时返回 false */
    boolean setStatus(String formKey, String status);

    /**
     * 发布一个版本要的东西（0004 的 1.1、1.5）：字段清单是 JSON 文字；前端源码与编译结果、后端源码原样存。
     *
     * @param serverLanguage {@code java}、{@code ts} 或 null（没有后端代码）
     */
    record Publication(String formKey, String name, String category, String manifest, String clientSource, String clientCompiled,
                       String compilerVersion, String serverLanguage, String serverSource, String note, Long publishedBy,
                       String publishedByName, Instant at) { }

    /**
     * 一个版本的前端部分（0004 的 5.2、5.4）。
     *
     * @param clientSource    前端源码
     * @param clientCompiled  编译结果
     * @param compilerVersion 编译它的编译器版本
     */
    record FormClient(String clientSource, String clientCompiled, String compilerVersion) { }

    /** 发布的一个版本：引擎用到的是字段清单 */
    record StoredForm(Long id, String formKey, int version, String manifest) { }

    /** 表单本身：启用停用与最新版本 */
    record FormInfo(String formKey, String name, String category, String status, int latestVersion) {
        /** 能被流程选用 */
        public static final String ACTIVE = "active";
        /** 停用：新发布的流程不能再选它，已经引用它的照常用 */
        public static final String DISABLED = "disabled";
    }

    /** 版本列表的一行：谁、什么时候发布的、说明 */
    record FormVersionInfo(int version, String note, String publishedByName, Instant publishedAt) { }

    /** 一个版本的源码与字段清单（不含编译结果） */
    record FormSource(String formKey, int version, String manifest, String clientSource, String serverLanguage, String serverSource) { }
}
