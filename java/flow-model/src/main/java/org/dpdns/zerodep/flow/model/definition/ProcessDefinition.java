package org.dpdns.zerodep.flow.model.definition;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** 一个流程（一个版本）：纯 JSON，前端设计器、两个后端读同一份（docs/decisions/0005）。从 JSON 读见 {@link Definitions#parse}。 */
@Data
public class ProcessDefinition {
    /** 同一流程各版本相同 */
    private final String key;
    /** 名称 */
    private final String name;
    /** 说明 */
    private final String description;
    /** 分类 */
    private final String category;
    /** 发布时后端给；草稿里没有 */
    private final Integer version;
    /** 一个流程一张表单；null 时没有表单 */
    private final FormRef form;
    /** 流程变量的声明 */
    private final List<Variable> variables;
    /** 待办列表的摘要列：{@code { ref }} 的名字 */
    private final List<String> summary;
    /** 实例标题的模板：花括号里是 {@code { ref }} 的名字 */
    private final String title;
    /** 谁能发起；空时人人可发起 */
    private final List<AssigneeSource> starters;
    /** 流程级设置 */
    private final Settings settings;
    /** 泳道 */
    private final List<Lane> lanes;
    /** 节点 */
    private final List<ProcessNode> nodes;
    /** 连线 */
    private final List<ProcessEdge> edges;
    /** 布局，引擎不读（JSON 原样） */
    private final Map<String, Object> layout;

    public ProcessDefinition(String key, String name, String description, String category, Integer version, FormRef form, List<Variable> variables, List<String> summary, String title, List<AssigneeSource> starters, Settings settings, List<Lane> lanes, List<ProcessNode> nodes, List<ProcessEdge> edges, Map<String, Object> layout) {
        variables = variables == null ? List.of() : List.copyOf(variables);
        summary = summary == null ? List.of() : List.copyOf(summary);
        starters = starters == null ? List.of() : List.copyOf(starters);
        settings = settings == null ? Settings.NONE : settings;
        lanes = lanes == null ? List.of() : List.copyOf(lanes);
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        layout = layout == null ? Map.of() : layout;
        this.key = key;
        this.name = name;
        this.description = description;
        this.category = category;
        this.version = version;
        this.form = form;
        this.variables = variables;
        this.summary = summary;
        this.title = title;
        this.starters = starters;
        this.settings = settings;
        this.lanes = lanes;
        this.nodes = nodes;
        this.edges = edges;
        this.layout = layout;
    }

    /** 流程引用的表单。 */
    @Data
    public static class FormRef {
        /** 表单的标识 */
        private final String key;
        /** 发布时固定的版本；草稿里没有 */
        private final Integer version;
    }

    /** 流程变量的声明。 */
    @Data
    public static class Variable {
        /** 名字（不带点） */
        private final String name;
        /** 类型：{@code text}、{@code number}、{@code decimal}、{@code boolean}、{@code date}、{@code datetime}、{@code select}、{@code people} */
        private final String type;
        /** 显示名 */
        private final String label;
        /** 默认值 */
        private final Object defaultValue;
    }

    /** 流程级设置（0005 的 2.1）。 */
    @Data
    public static class Settings {
        /** 工作日历 */
        private final String calendar;
        /** 发起人撤回整个实例的规则 */
        private final String withdraw;
        /** 处理人解析为空时 */
        private final String emptyAssignee;
        /** 自动跳过（JSON 原样） */
        private final Map<String, Object> autoSkip;
        /** 不在待办里看表单的人对哪些字段隐藏 */
        private final Map<String, FieldAccess> formView;
        /** 通知（JSON 的 notify，原样） */
        private final Map<String, Object> notifications;

        /** 什么都没写 */
        public static final Settings NONE = new Settings(null, null, null, Map.of(), Map.of(), Map.of());
    }
}
