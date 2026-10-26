package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.RunState;
import org.dpdns.zerodep.flow.core.data.Variables;
import org.dpdns.zerodep.flow.core.store.InstanceStore.InstanceRecord;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.definition.Ref;
import org.dpdns.zerodep.flow.model.runtime.FlowEvent;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 调用流程（0005 的 4.2 {@code call}）：父实例在调用节点上发起子实例，子实例结束时回到父实例。
 * <ul>
 *   <li>发起：{@code forEach} 时列表里每一项一个子实例（{@code as} 是那一项的名字），否则一个；{@code input} 的键是子实例那一侧
 *       （{@code form.} 开头写进子实例的表单，否则是流程变量），值是父实例这一侧的值或引用。子实例的发起人是父实例的发起人；</li>
 *   <li>同步（{@code mode: "sync"}）：父实例停在调用节点，等这一轮的子实例都结束；结果是子实例的结束结果（几个都一样才按它，
 *       否则走没写结果的出边），{@code output} 把子实例那一侧的值收进父实例的流程变量（{@code forEach} 时收成列表）；</li>
 *   <li>异步（默认）：发起就往下走，不等；</li>
 *   <li>父实例被终止、撤回时，没结束的子实例一起终止。</li>
 * </ul>
 * 子实例在同一个命令里马上就结束了（全是自动节点）时，直接回到这个命令里的父实例（{@link Execution#caller}），不从库里重读。
 */
final class CallActivities {
    private CallActivities() { }

    /** 到达调用节点（轮次已经加过）：发起子实例；同步的停下等，异步的接着走 */
    static void call(Execution run, ProcessNode.Call call) {
        InstanceRecord parent = run.instance();
        int round = parent.state().round(call.id());
        List<Object> items = call.forEach() == null ? Collections.singletonList(null) : list(run.read(call.forEach().ref()));
        boolean sync = "sync".equals(call.mode());
        if (sync && !items.isEmpty()) run.waitForChildren(call.id(), items.size());

        StartHandler starter = new StartHandler(run.context());
        LoadedDefinition child = starter.latestActive(call.process());
        Long root = parent.rootInstanceId() != null ? parent.rootInstanceId() : parent.id();
        for (Object item : items) {
            Function<String, Object> read = name -> scoped(name, call.as(), item, run::read);
            Map<String, Object> form = new LinkedHashMap<>();
            Map<String, Object> variables = new LinkedHashMap<>();
            call.input().forEach((target, value) -> {
                Object resolved = value instanceof Ref ref ? read.apply(ref.ref()) : value;
                if (target.startsWith("form.")) form.put(target.substring("form.".length()), resolved);
                else variables.put(target, resolved);
            });
            StartHandler.Launch launch = new StartHandler.Launch(parent.startedBy(), parent.startedByName(), parent.businessKey(), parent.priority(),
                Variables.of(variables), FormValues.of(form), true, new StartHandler.ChildOf(parent.id(), call.id(), round, root, run), Map.of());
            Execution childRun = starter.launch(child, run.actor(), launch, run.now());
            childRun.flush(null);
            run.created.addAll(childRun.created);
        }
        if (!sync || items.isEmpty()) run.leave(call.id(), null);
    }

    /**
     * 子实例结束了：父实例还停在调用它的节点的这一轮上（同步）时少等一个；都结束了收 output、按结果往下走。
     * 父实例是这个命令里调用它的那个时直接接着推进；否则锁住父实例重读，推进完写回。
     */
    static void childEnded(Execution child) {
        InstanceRecord ended = child.instance();
        Execution parentRun = child.caller;
        boolean loaded = parentRun == null;
        if (loaded) {
            parentRun = load(child, ended.parentInstanceId());
            if (parentRun == null) return;
        }
        RunState state = parentRun.instance().state();
        String node = ended.parentNode();
        if (!state.waits().containsKey(node) || state.round(node) != ended.parentRound()) return;   // 异步调用的，或父实例已经不等它了
        if (parentRun.childArrived(node)) {
            ProcessNode.Call call = (ProcessNode.Call) parentRun.graph().node(node);
            List<InstanceRecord> children = child.context().stores().instances().children(ended.parentInstanceId(), node, ended.parentRound()).stream()
                .map(c -> c.id().equals(ended.id()) ? ended : c).toList();
            parentRun.callReturned(call, outcomeOf(children), outputOf(call, children, child));
        }
        if (loaded) {
            parentRun.flush(null);
            child.created.addAll(parentRun.created);
            child.closed.addAll(parentRun.closed);
        }
    }

    /** 父实例终止、撤回：没结束的子实例一起终止（子实例的子实例也一样），不再回到父实例 */
    static void terminateChildren(Execution parentRun, String reason) {
        EngineContext context = parentRun.context();
        for (InstanceRecord found : context.stores().instances().activeChildren(parentRun.instance().id())) {
            int rev = context.stores().instances().lock(found.id()).orElseThrow();
            InstanceRecord instance = context.stores().instances().find(found.id()).orElseThrow();
            if (instance.endedAt() != null) continue;
            Execution childRun = new Execution(context, parentRun.actor(), parentRun.now(), context.definitions().of(instance), instance,
                FormValues.empty(), rev);
            childRun.quiet = true;
            childRun.finish(ProcessInstance.TERMINATED, ProcessInstance.TERMINATED, FlowEvent.INSTANCE_TERMINATED, reason);
            childRun.flush(null);
            parentRun.closed.addAll(childRun.closed);
        }
    }

    /** 锁住父实例重读；结束了、暂停了的不推进（返回 null） */
    private static Execution load(Execution child, Long parentId) {
        EngineContext context = child.context();
        int rev = context.stores().instances().lock(parentId).orElse(-1);
        if (rev < 0) return null;
        InstanceRecord parent = context.stores().instances().find(parentId).orElseThrow();
        if (!ProcessInstance.ACTIVE.equals(parent.status())) return null;
        FormValues form = context.stores().formData().read(parentId).map(f -> f.values()).orElse(FormValues.empty());
        return new Execution(context, child.actor(), child.now(), context.definitions().of(parent), parent, form, rev);
    }

    /** 几个子实例都是同一个结束结果才按它，否则为 null（走没写结果的出边） */
    private static String outcomeOf(List<InstanceRecord> children) {
        String first = children.isEmpty() ? null : children.get(0).result();
        return children.stream().allMatch(c -> Objects.equals(c.result(), first)) ? first : null;
    }

    /** output：父实例的流程变量 ← 子实例那一侧的值；forEach 时收成列表（按发起的先后） */
    private static Map<String, Object> outputOf(ProcessNode.Call call, List<InstanceRecord> children, Execution child) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (call.output().isEmpty()) return out;
        EngineContext context = child.context();
        List<FormValues> forms = children.stream().map(c -> c.id().equals(child.instance().id()) ? child.form()
            : context.stores().formData().read(c.id()).map(f -> f.values()).orElse(FormValues.empty())).toList();
        call.output().forEach((target, name) -> {
            List<Object> values = new ArrayList<>();
            for (int i = 0; i < children.size(); i++) values.add(References.read(name, children.get(i), forms.get(i)));
            out.put(target, call.forEach() == null ? (values.isEmpty() ? null : values.get(0)) : values);
        });
        return out;
    }

    /** 引用里 as 那一项（及它的属性 {@code as.字段}）取循环里的值，其余照常 */
    private static Object scoped(String name, String as, Object item, Function<String, Object> read) {
        if (as == null) return read.apply(name);
        if (name.equals(as)) return item;
        if (name.startsWith(as + ".") && item instanceof Map<?, ?> map) return map.get(name.substring(as.length() + 1));
        return read.apply(name);
    }

    private static List<Object> list(Object value) {
        if (value instanceof Collection<?> c) return new ArrayList<>(c);
        return value == null ? List.of() : List.of(value);
    }
}
