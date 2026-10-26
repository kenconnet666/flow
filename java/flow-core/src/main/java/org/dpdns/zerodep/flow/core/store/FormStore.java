package org.dpdns.zerodep.flow.core.store;

import java.time.Instant;
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
    Optional<StoredForm> byId(String formVersionId);

    /**
     * 发布一个版本要的东西（0004 的 1.1、1.5）：字段清单是 JSON 文字；前端源码与编译结果、后端源码原样存。
     *
     * @param serverLanguage {@code java}、{@code ts} 或 null（没有后端代码）
     */
    record Publication(String formKey, String name, String category, String manifest, String clientSource, String clientCompiled,
                       String compilerVersion, String serverLanguage, String serverSource, String note, String publishedBy,
                       String publishedByName, Instant at) { }

    /** 发布的一个版本：引擎用到的是字段清单 */
    record StoredForm(String id, String formKey, int version, String manifest) { }
}
