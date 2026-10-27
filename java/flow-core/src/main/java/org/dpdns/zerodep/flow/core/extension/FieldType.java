package org.dpdns.zerodep.flow.core.extension;

import java.util.List;
import java.util.Map;

/**
 * 接入方注册的表单字段类型（0004 的"可替换的部分"，如带币种的金额 {@code money}）：字段清单里写 {@code "type": "money"}。
 * 发布表单时清单认得这个类型；提交、暂存时引擎调 {@link #check} 查值，问题代号同内置类型（{@code type}、{@code min}……）标到字段上。
 * 值在线上是 typed JSON，到这里已经是 json-wire 还原出的强类型。前端的控件由表单代码或设计器里登记的组件给。
 */
public interface FieldType {
    /** 清单里写的类型名 */
    String name();

    /** 设计器里的显示名与说明；不给时只显示类型名 */
    default ExtensionInfo info() { return null; }

    /**
     * 查一个值；空值（null、空文字、空数组）不会传进来，必填由引擎另查。
     *
     * @param attributes 字段在清单里的其余属性（label、type 以外的原样）
     * @return 问题代号；合规时是空的
     */
    List<String> check(Map<String, Object> attributes, Object value);
}
