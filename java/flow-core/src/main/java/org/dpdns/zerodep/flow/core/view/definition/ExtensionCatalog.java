package org.dpdns.zerodep.flow.core.view.definition;

import lombok.Data;
import org.dpdns.zerodep.flow.core.spi.Choice;
import org.dpdns.zerodep.flow.core.spi.ExtensionInfo;
import org.dpdns.zerodep.flow.core.spi.ExtensionParam;

import java.util.List;

/**
 * 接入方注册了哪些扩展（{@code GET /queries/extensions}）：流程设计器把手填的名字换成下拉，按参数画控件，校验时认得这些名字；
 * 自定义节点进节点面板；节点的业务信息（{@code meta}）按 {@link #nodeMeta} 画成表单。
 */
@Data
public class ExtensionCatalog {
    /** 处理人解析器 */
    private final List<Entry> resolvers;
    /** 提交前的守卫 */
    private final List<Entry> guards;
    /** 自动节点的动作 */
    private final List<Entry> actions;
    /** 计算值（条件里的 {@code fn.名字}） */
    private final List<Entry> computed;
    /** 自定义节点类型（{@code x-名字}） */
    private final List<Entry> nodes;
    /** 会签的完成规则 */
    private final List<Entry> completions;
    /** 表单字段类型 */
    private final List<Entry> fieldTypes;
    /** 节点的业务信息有哪些字段（写进节点的 {@code meta}）；没声明时为空，设计器不显示这一块 */
    private final List<ExtensionParam> nodeMeta;
    /** 能不能在线写表单的后端代码（关掉时表单编辑器不显示后端代码页签，发布带后端代码的表单报错） */
    private final boolean formServerCode;

    /** 一个扩展 */
    @Data
    public static class Entry {
        /** 定义里写的名字（自定义节点是类型） */
        private final String name;
        /** 显示名；没给时同名字 */
        private final String label;
        private final String description;
        private final List<ExtensionParam> params;
        private final List<Choice> outcomes;
        /** 计算值的类型；别的扩展为 null */
        private final String valueType;

        /** 由扩展的名字与它给的说明（可以为 null）组成 */
        public static Entry of(String name, ExtensionInfo info) {
            if (info == null) return new Entry(name, name, null, List.of(), List.of(), null);
            return new Entry(name, info.getLabel() == null ? name : info.getLabel(), info.getDescription(), List.copyOf(info.getParams()),
                List.copyOf(info.getOutcomes()), info.getValueType());
        }
    }
}
