package org.dpdns.zerodep.flow.core.spi;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 扩展的一个参数（或节点业务信息的一个字段）：设计器按它画控件，填的值写进定义（解析器、动作的 {@code args}，自定义节点的属性，节点的 {@code meta}）。
 * 类型沿用表单字段清单的名字：{@code text}、{@code number}、{@code decimal}、{@code boolean}、{@code date}、{@code select}。
 * 能从 {@code application.yml} 绑定（{@code flow.designer.node-meta}），所以是可变的 JavaBean。
 */
@Data
@NoArgsConstructor
public class ExtensionParam {
    /** 写进定义的键 */
    private String key;
    /** 显示名；不给时显示键 */
    private String label;
    /** 类型；默认 text */
    private String type = "text";
    /** 必填：设计器里标星，没填时校验提示 */
    private boolean required;
    /** 填写说明 */
    private String description;
    /** select 的选项 */
    private List<Choice> options = new ArrayList<>();

    /** 一个参数 */
    public static ExtensionParam of(String key, String label, String type) {
        ExtensionParam param = new ExtensionParam();
        param.setKey(key);
        param.setLabel(label);
        param.setType(type);
        return param;
    }

    /** 下拉选择的参数 */
    public static ExtensionParam select(String key, String label, Choice... options) {
        ExtensionParam param = of(key, label, "select");
        param.setOptions(new ArrayList<>(List.of(options)));
        return param;
    }

    /** 标成必填 */
    public ExtensionParam required() {
        this.required = true;
        return this;
    }

    /** 加上填写说明 */
    public ExtensionParam describedAs(String description) {
        this.description = description;
        return this;
    }
}
