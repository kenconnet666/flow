package org.dpdns.zerodep.flow.core.extension;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 扩展在设计器里的样子：显示名、说明、参数、可能的结果。接入方在扩展上覆盖 {@code info()} 给出；不给时设计器只显示名字，参数写 JSON。
 * <pre>{@code
 *   public ExtensionInfo info() {
 *       return ExtensionInfo.of("部门负责人").describedAs("发起人所在部门往上第 N 级的负责人")
 *           .param(ExtensionParam.of("level", "往上几级", "number").required());
 *   }
 * }</pre>
 */
@Getter
public class ExtensionInfo {
    /** 显示名 */
    private final String label;
    /** 说明 */
    private String description;
    /** 参数，按先后 */
    private final List<ExtensionParam> params = new ArrayList<>();
    /** 动作、自定义节点做完可能给的结果：设计器里给出边选 */
    private final List<Choice> outcomes = new ArrayList<>();
    /** 计算值的类型（{@code text}、{@code number}、{@code decimal}、{@code boolean}、{@code date}）：条件里按它比较；别的扩展为 null */
    private String valueType;

    private ExtensionInfo(String label) { this.label = label; }

    /** 显示名为 label */
    public static ExtensionInfo of(String label) { return new ExtensionInfo(label); }

    /** 加上说明 */
    public ExtensionInfo describedAs(String description) {
        this.description = description;
        return this;
    }

    /** 加一个参数 */
    public ExtensionInfo param(ExtensionParam param) {
        params.add(param);
        return this;
    }

    /** 加一个可能的结果 */
    public ExtensionInfo outcome(String key, String label) {
        outcomes.add(new Choice(key, label));
        return this;
    }

    /** 计算值的类型 */
    public ExtensionInfo valueType(String valueType) {
        this.valueType = valueType;
        return this;
    }
}
