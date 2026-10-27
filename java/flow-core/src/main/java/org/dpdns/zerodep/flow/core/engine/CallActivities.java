package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.RunState;
import org.dpdns.zerodep.flow.core.data.Variables;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.definition.Ref;

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
class CallActivities {
    private CallActivities() { }

    /** 到达调用节点（轮次已经加过）：发起子实例；同步的停下等，异步的接着走 */
    static void call(Execution run, ProcessNode.Call call) {
        InstanceEntity parent = run.instance();
        int round = parent.getState().round(call.getId());
        List<Object> items = call.getForEach() == null ? Collections.singletonList(null) : list(run.read(call.getForEach().getRef()));
        boolean sync = "sync".equals(call.getMode());
        if (sync && !items.isEmpty()) run.waitForChildren(call.getId(), items.size());

        StartHandler starter = new StartHandler(run.context());
        LoadedDefinition child = starter.latestActive(call.getProcess());
        Long root = parent.getRootInstanceId() != null ? parent.getRootInstanceId() : parent.getId();
        for (Object item : items) {
            Function<String, Object> read = name -> scoped(name, call.getAs(), item, run::read);
            Map<String, Object> form = new LinkedHashMap<>();
            Map<String, Object> variables = new LinkedHashMap<>();
            call.getInput().forEach((target, value) -> {
                Object resolved = value instanceof Ref ref ? read.apply(ref.getRef()) : value;
                if (target.startsWith("form.")) form.put(target.substring("form.".length()), resolved);
                else variables.put(target, resolved);
            });
            StartHandler.Launch launch = new StartHandler.Launch(parent.getStartedBy(), parent.getStartedByName(), parent.getBusinessKey(), parent.getPriority(),
                Variables.of(variables), FormValues.of(form), true, new StartHandler.ChildOf(parent.getId(), call.getId(), round, root, run), Map.of());
            Execution childRun = starter.launch(child, run.actor(), launch, run.now());
            childRun.flush(null);
            run.created.addAll(childRun.created);
        }
        if (!sync || items.isEmpty()) run.leave(call.getId(), null);
    }

    /**
     * 子实例结束了：父实例还停在调用它的节点的这一轮上（同步）时少等一个；都结束了收 output、按结果往下走。
     * 父实例是这个命令里调用它的那个时直接接着推进；否则锁住父实例重读，推进完写回。
     */
    static void childEnded(Execution child) {
        InstanceEntity ended = child.instance();
        Execution parentRun = child.caller;
        boolean loaded = parentRun == null;
        if (loaded) {
            parentRun = load(child, ended.getParentInstanceId());
            if (parentRun == null) return;
        }
        RunState state = parentRun.instance().getState();
        String node = ended.getParentNode();
        if (!state.getWaits().containsKey(node) || state.round(node) != ended.getParentRound()) return;   // 异步调用的，或父实例已经不等它了
        if (parentRun.childArrived(node)) {
            ProcessNode.Call call = (ProcessNode.Call) parentRun.graph().node(node);
            // 这一轮的子实例；刚结束的这个用推进到此刻的（还没写回）
            List<InstanceEntity> children = child.context().getDb().queryable(InstanceEntity.class)
                .where(i -> { i.parentInstanceId().eq(ended.getParentInstanceId()); i.parentNode().eq(node); i.parentRound().eq(ended.getParentRound()); })
                .orderBy(i -> i.id().asc()).toList().stream()
                .map(c -> c.getId().equals(ended.getId()) ? ended : c).toList();
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
        List<Long> active = context.getDb().queryable(InstanceEntity.class)
            .where(i -> { i.parentInstanceId().eq(parentRun.instance().getId()); i.endedAt().isNull(); })
            .orderBy(i -> i.id().asc()).selectColumn(i -> i.id()).toList();
        for (Long id : active) {
            InstanceEntity instance = Instances.lock(context.getDb(), id);
            if (instance.getEndedAt() != null) continue;
            Execution childRun = new Execution(context, parentRun.actor(), parentRun.now(), context.getDefinitions().of(instance), instance,
                FormValues.empty(), instance.getRev());
            childRun.quiet = true;
            childRun.finish(InstanceEntity.TERMINATED, InstanceEntity.TERMINATED, EventEntity.INSTANCE_TERMINATED, reason);
            childRun.flush(null);
            parentRun.closed.addAll(childRun.closed);
        }
    }

    /** 锁住父实例重读；结束了、暂停了的不推进（返回 null） */
    private static Execution load(Execution child, Long parentId) {
        EngineContext context = child.context();
        InstanceEntity parent = Instances.lockIfExists(context.getDb(), parentId);
        if (parent == null || !InstanceEntity.ACTIVE.equals(parent.getStatus())) return null;
        FormValues form = Instances.form(context.getDb(), parentId);
        return new Execution(context, child.actor(), child.now(), context.getDefinitions().of(parent), parent, form, parent.getRev());
    }

    /** 几个子实例都是同一个结束结果才按它，否则为 null（走没写结果的出边） */
    private static String outcomeOf(List<InstanceEntity> children) {
        String first = children.isEmpty() ? null : children.get(0).getResult();
        return children.stream().allMatch(c -> Objects.equals(c.getResult(), first)) ? first : null;
    }

    /** output：父实例的流程变量 ← 子实例那一侧的值；forEach 时收成列表（按发起的先后） */
    private static Map<String, Object> outputOf(ProcessNode.Call call, List<InstanceEntity> children, Execution child) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (call.getOutput().isEmpty()) return out;
        EngineContext context = child.context();
        List<FormValues> forms = children.stream().map(c -> c.getId().equals(child.instance().getId()) ? child.form()
            : Instances.form(context.getDb(), c.getId())).toList();
        call.getOutput().forEach((target, name) -> {
            List<Object> values = new ArrayList<>();
            for (int i = 0; i < children.size(); i++) values.add(References.read(name, children.get(i), forms.get(i)));
            out.put(target, call.getForEach() == null ? (values.isEmpty() ? null : values.get(0)) : values);
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
