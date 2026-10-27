package org.dpdns.zerodep.flow.core.extension;

import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 接入方按名字注册的扩展（0006 第 6 节"业务"一行）：处理人解析器、提交前的守卫、自动节点的动作。
 * 同名的后注册的换掉先注册的。定义里写了没注册的名字，发布时就报 {@code extension-unknown}；万一用到（换了注册后旧版本的实例）报
 * {@code flow.extension-failed}。
 */
public class Extensions {
    /** 什么也没注册 */
    public static final Extensions NONE = new Extensions(List.of(), List.of(), List.of());

    private final Map<String, PeopleResolver> resolvers;
    private final Map<String, OutcomeGuard> guards;
    private final Map<String, ServiceAction> actions;

    public Extensions(List<PeopleResolver> resolvers, List<OutcomeGuard> guards, List<ServiceAction> actions) {
        this.resolvers = byName(resolvers, PeopleResolver::name);
        this.guards = byName(guards, OutcomeGuard::name);
        this.actions = byName(actions, ServiceAction::name);
    }

    /** 处理人解析器 */
    public PeopleResolver resolver(String name) { return find(resolvers, "resolver", name); }

    /** 提交前的守卫 */
    public OutcomeGuard guard(String name) { return find(guards, "guard", name); }

    /** 自动节点的动作 */
    public ServiceAction action(String name) { return find(actions, "action", name); }

    /** 注册了这个名字的解析器没有 */
    public boolean hasResolver(String name) { return resolvers.containsKey(name); }

    /** 注册了这个名字的守卫没有 */
    public boolean hasGuard(String name) { return guards.containsKey(name); }

    /** 注册了这个名字的动作没有 */
    public boolean hasAction(String name) { return actions.containsKey(name); }

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
