package org.dpdns.zerodep.flow.core.extension;

import org.dpdns.zerodep.flow.core.view.definition.ExtensionCatalog;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 接入方按名字注册的扩展（0006 第 6 节"业务"一行）：处理人解析器、提交前的守卫、自动节点的动作、计算值、自定义节点类型、会签的完成规则、表单字段类型，
 * 加上节点业务信息（{@code meta}）的字段声明。设计器从 {@link #catalog} 知道有哪些。
 * 同名的后注册的换掉先注册的。定义里写了没注册的名字，发布时就报 {@code extension-unknown}；万一用到（换了注册后旧版本的实例）报
 * {@code flow.extension-failed}。
 */
public class Extensions {
    /** 什么也没注册 */
    public static final Extensions NONE = new Extensions(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

    private final Map<String, PeopleResolver> resolvers;
    private final Map<String, OutcomeGuard> guards;
    private final Map<String, ServiceAction> actions;
    private final Map<String, CustomNode> nodes;
    private final Map<String, CountersignRule> countersignRules;
    private final Map<String, FieldType> fieldTypes;
    private final Map<String, ComputedValue> computed;
    private final List<ExtensionParam> nodeMeta;

    public Extensions(List<PeopleResolver> resolvers, List<OutcomeGuard> guards, List<ServiceAction> actions, List<CustomNode> nodes,
                      List<CountersignRule> countersignRules, List<FieldType> fieldTypes, List<ComputedValue> computed,
                      List<ExtensionParam> nodeMeta) {
        this.resolvers = byName(resolvers, PeopleResolver::name);
        this.guards = byName(guards, OutcomeGuard::name);
        this.actions = byName(actions, ServiceAction::name);
        this.nodes = byName(nodes, CustomNode::type);
        this.countersignRules = byName(countersignRules, CountersignRule::name);
        this.fieldTypes = byName(fieldTypes, FieldType::name);
        this.computed = byName(computed, ComputedValue::name);
        this.nodeMeta = List.copyOf(nodeMeta);
    }

    /** 处理人解析器 */
    public PeopleResolver resolver(String name) { return find(resolvers, "resolver", name); }

    /** 提交前的守卫 */
    public OutcomeGuard guard(String name) { return find(guards, "guard", name); }

    /** 自动节点的动作 */
    public ServiceAction action(String name) { return find(actions, "action", name); }

    /** 计算值（{@code fn.名字}） */
    public ComputedValue computed(String name) { return find(computed, "computed", name); }

    /** 自定义节点类型 */
    public CustomNode node(String type) { return find(nodes, "node", type); }

    /** 会签的完成规则 */
    public CountersignRule countersignRule(String name) { return find(countersignRules, "completion", name); }

    /** 注册的表单字段类型；没注册时为 null（内置类型都不在这里） */
    public FieldType fieldType(String name) { return fieldTypes.get(name); }

    /** 注册了这个表单字段类型没有 */
    public boolean hasFieldType(String name) { return fieldTypes.containsKey(name); }

    /** 注册了这个自定义节点类型没有 */
    public boolean hasNode(String type) { return nodes.containsKey(type); }

    /** 注册了这个名字的会签规则没有 */
    public boolean hasCountersignRule(String name) { return countersignRules.containsKey(name); }

    /** 注册了这个名字的解析器没有 */
    public boolean hasResolver(String name) { return resolvers.containsKey(name); }

    /** 注册了这个名字的守卫没有 */
    public boolean hasGuard(String name) { return guards.containsKey(name); }

    /** 注册了这个名字的计算值没有 */
    public boolean hasComputed(String name) { return computed.containsKey(name); }

    /** 注册了这个名字的动作没有 */
    public boolean hasAction(String name) { return actions.containsKey(name); }

    /** 给设计器的目录：每个扩展的名字与它在 info() 里给的显示名、参数、结果 */
    public ExtensionCatalog catalog(boolean formServerCode) {
        return new ExtensionCatalog(
            entries(resolvers, PeopleResolver::info), entries(guards, OutcomeGuard::info), entries(actions, ServiceAction::info),
            entries(computed, ComputedValue::info), entries(nodes, CustomNode::info), entries(countersignRules, CountersignRule::info),
            entries(fieldTypes, FieldType::info), nodeMeta, formServerCode);
    }

    private static <T> List<ExtensionCatalog.Entry> entries(Map<String, T> registry, Function<T, ExtensionInfo> info) {
        return registry.entrySet().stream().sorted(Map.Entry.comparingByKey())
            .map(e -> ExtensionCatalog.Entry.of(e.getKey(), info.apply(e.getValue()))).toList();
    }

    private static <T> T find(Map<String, T> registry, String kind, String name) {
        T found = registry.get(name);
        if (found == null) {
            throw new FlowException(FlowErrorCode.EXTENSION_FAILED, Map.of("kind", kind, "name", String.valueOf(name), "reason", "not-registered"));
        }
        return found;
    }

    private static <T> Map<String, T> byName(List<T> items, Function<T, String> name) {
        Map<String, T> out = new LinkedHashMap<>();
        for (T item : items) out.put(name.apply(item), item);
        return Map.copyOf(out);
    }
}
