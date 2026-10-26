package org.dpdns.zerodep.flow.model.validate;

import org.dpdns.zerodep.flow.model.form.FormManifest;

import java.util.List;

/**
 * 校验时能用到的外部信息；后端发布时给全，设计器里按能拿到的给。每一项返回 null 表示"不知道"，那条规则就不查（同 TS 的可选函数）。
 */
public interface ValidationContext {
    /** 什么都不知道：只查定义本身 */
    ValidationContext NONE = new ValidationContext() { };

    /** 扩展的种类 */
    enum ExtensionKind { RESOLVER, ACTION, GUARD, COMPUTED, NODE, COMPLETION }

    /** 流程引用的表单的字段清单 */
    default FormManifest form() { return null; }

    /** 扩展有没有注册；null 表示不知道 */
    default Boolean isRegistered(ExtensionKind kind, String name) { return null; }

    /** 流程 key 存不存在、有没有停用；null 表示不知道 */
    default Boolean processExists(String key) { return null; }

    /** 某个流程（最新版本）调用了哪些流程；null 表示不知道，不查调用成环 */
    default List<String> callsOf(String key) { return null; }

    /** 工作日历存不存在；null 表示不知道 */
    default Boolean calendarExists(String name) { return null; }
}
