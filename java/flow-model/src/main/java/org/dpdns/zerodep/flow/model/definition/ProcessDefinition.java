package org.dpdns.zerodep.flow.model.definition;

import java.util.List;
import java.util.Map;

/**
 * 一个流程（一个版本）：纯 JSON，前端设计器、两个后端读同一份（docs/decisions/0005）。从 JSON 读见 {@link Definitions#parse}。
 *
 * @param key         同一流程各版本相同
 * @param name        名称
 * @param description 说明
 * @param category    分类
 * @param version     发布时后端给；草稿里没有
 * @param form        一个流程一张表单；null 时没有表单
 * @param variables   流程变量的声明
 * @param summary     待办列表的摘要列：{@code { ref }} 的名字
 * @param title       实例标题的模板：花括号里是 {@code { ref }} 的名字
 * @param starters    谁能发起；空时人人可发起
 * @param settings    流程级设置
 * @param lanes       泳道
 * @param nodes       节点
 * @param edges       连线
 * @param layout      布局，引擎不读（JSON 原样）
 */
public record ProcessDefinition(String key, String name, String description, String category, Integer version, FormRef form,
                                List<Variable> variables, List<String> summary, String title, List<AssigneeSource> starters,
                                Settings settings, List<Lane> lanes, List<ProcessNode> nodes, List<ProcessEdge> edges,
                                Map<String, Object> layout) {
    public ProcessDefinition {
        variables = variables == null ? List.of() : List.copyOf(variables);
        summary = summary == null ? List.of() : List.copyOf(summary);
        starters = starters == null ? List.of() : List.copyOf(starters);
        settings = settings == null ? Settings.NONE : settings;
        lanes = lanes == null ? List.of() : List.copyOf(lanes);
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        layout = layout == null ? Map.of() : layout;
    }

    /**
     * 流程引用的表单。
     *
     * @param key     表单的标识
     * @param version 发布时固定的版本；草稿里没有
     */
    public record FormRef(String key, Integer version) { }

    /**
     * 流程变量的声明。
     *
     * @param name         名字（不带点）
     * @param type         类型：{@code text}、{@code number}、{@code decimal}、{@code boolean}、{@code date}、{@code datetime}、{@code select}、{@code people}
     * @param label        显示名
     * @param defaultValue 默认值
     */
    public record Variable(String name, String type, String label, Object defaultValue) { }

    /**
     * 流程级设置（0005 的 2.1）。
     *
     * @param calendar      工作日历
     * @param withdraw      发起人撤回整个实例的规则
     * @param emptyAssignee 处理人解析为空时
     * @param autoSkip      自动跳过（JSON 原样）
     * @param formView      不在待办里看表单的人对哪些字段隐藏
     * @param notifications 通知（JSON 的 notify，原样）
     */
    public record Settings(String calendar, String withdraw, String emptyAssignee, Map<String, Object> autoSkip,
                           Map<String, FieldAccess> formView, Map<String, Object> notifications) {
        /** 什么都没写 */
        public static final Settings NONE = new Settings(null, null, null, Map.of(), Map.of(), Map.of());
    }
}
