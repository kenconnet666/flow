package org.dpdns.zerodep.flow.model.validate;

import org.dpdns.zerodep.flow.model.Conditions;
import org.dpdns.zerodep.flow.model.definition.AssigneeSource;
import org.dpdns.zerodep.flow.model.definition.Due;
import org.dpdns.zerodep.flow.model.definition.FieldAccess;
import org.dpdns.zerodep.flow.model.definition.Lane;
import org.dpdns.zerodep.flow.model.definition.MultiInstance;
import org.dpdns.zerodep.flow.model.definition.Outcome;
import org.dpdns.zerodep.flow.model.definition.ProcessDefinition;
import org.dpdns.zerodep.flow.model.definition.ProcessEdge;
import org.dpdns.zerodep.flow.model.definition.ProcessGraph;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.definition.Ref;
import org.dpdns.zerodep.flow.model.form.FormManifest;
import org.dpdns.zerodep.flow.model.validate.ValidationContext.ExtensionKind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 流程定义的校验（docs/decisions/0005 第 7 节），与 TS 模型包的 {@code validate.ts} 逐条相同，两边跑 spec/definitions 的样例。
 * 设计器保存时、后端发布时跑同一份规则；只给代号和参数。错误不能发布，警告可以发布。
 * <pre>{@code
 * List<Problem> problems = ProcessValidator.validate(definition, context);
 * }</pre>
 * 要查注册情况的几条只在 {@link ValidationContext} 给出答案（不是 null）时才查，后端发布时给。
 */
public final class ProcessValidator {
    /** 各代号的严重程度；没列出的都是错误 */
    private static final Set<String> WARNINGS = Set.of(
        "node-unreachable", "edge-default-not-gateway", "gateway-edge-unconditional", "parallel-edge-condition", "parallel-useless",
        "form-required-unfillable");

    /** {@code sys.} 下引擎给的只读值；{@code sys.round.节点} 另外判断 */
    private static final Set<String> SYSTEM_NAMES = Set.of("initiator", "startedAt", "instanceId", "businessKey");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)\\}");

    private static final String EXIT = "\u0000exit";

    private final ProcessDefinition definition;
    private final ValidationContext context;
    private final ProcessGraph graph;
    private final List<Problem> problems = new ArrayList<>();

    private ProcessValidator(ProcessDefinition definition, ValidationContext context) {
        this.definition = definition;
        this.context = context == null ? ValidationContext.NONE : context;
        this.graph = ProcessGraph.of(definition);
    }

    /** 校验一个流程定义；问题按规则的先后、再按定义里的先后排 */
    public static List<Problem> validate(ProcessDefinition definition, ValidationContext context) {
        ProcessValidator validator = new ProcessValidator(definition, context);
        validator.checkIdentities();
        validator.checkStartAndEnd();
        validator.checkEdges();
        for (ProcessNode node : definition.nodes()) validator.checkNode(node);
        validator.checkReachability();
        validator.checkGateways();
        validator.checkReturnsAndEscalations();
        validator.checkReferences();
        validator.checkForm();
        validator.checkPublishOnly();
        return validator.deduped();
    }

    /** 只查定义本身 */
    public static List<Problem> validate(ProcessDefinition definition) {
        return validate(definition, ValidationContext.NONE);
    }

    // ---------- 记问题 ----------

    private void report(String code, String node, String edge, String lane, String... params) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < params.length; i += 2) map.put(params[i], params[i + 1]);
        problems.add(new Problem(code, WARNINGS.contains(code) ? Problem.Severity.WARNING : Problem.Severity.ERROR, node, edge, lane, null, map));
    }

    private void atNode(String code, String node, String... params) { report(code, node, null, null, params); }

    private void atEdge(String code, String edge, String... params) { report(code, null, edge, null, params); }

    private void atDefinition(String code, String... params) { report(code, null, null, null, params); }

    /** 同一个代号、同一个位置、同样的参数只留一条 */
    private List<Problem> deduped() {
        return List.copyOf(new LinkedHashSet<>(problems));
    }

    // ---------- 标识、开始与结束、连线 ----------

    private void checkIdentities() {
        for (String id : duplicates(definition.nodes().stream().map(ProcessNode::id).toList())) atNode("node-duplicate", id);
        for (String id : duplicates(definition.edges().stream().map(ProcessEdge::id).toList())) atEdge("edge-duplicate", id);
        for (String id : duplicates(definition.lanes().stream().map(Lane::id).toList())) report("lane-duplicate", null, null, id);
        for (ProcessNode node : definition.nodes()) {
            if (!ProcessNode.BUILTIN_TYPES.contains(node.type()) && !node.type().startsWith("x-")) atNode("node-type-unknown", node.id(), "type", node.type());
            if (node.lane() != null && definition.lanes().stream().noneMatch(l -> l.id().equals(node.lane()))) {
                atNode("lane-unknown", node.id(), "lane", node.lane());
            }
        }
    }

    /** 出现不止一次的标识，按第一次重复的先后 */
    private static Set<String> duplicates(List<String> ids) {
        Set<String> seen = new HashSet<>();
        Set<String> repeated = new LinkedHashSet<>();
        for (String id : ids) if (!seen.add(id)) repeated.add(id);
        return repeated;
    }

    private void checkStartAndEnd() {
        List<ProcessNode> starts = definition.nodes().stream().filter(n -> n.type().equals("start")).toList();
        if (starts.isEmpty()) atDefinition("start-missing");
        for (ProcessNode extra : starts.subList(Math.min(1, starts.size()), starts.size())) atNode("start-multiple", extra.id());
        for (ProcessNode start : starts) if (!graph.incoming(start.id()).isEmpty()) atNode("start-incoming", start.id());
        if (definition.nodes().stream().noneMatch(n -> n.type().equals("end"))) atDefinition("end-missing");
    }

    private void checkEdges() {
        for (ProcessEdge edge : definition.edges()) {
            if (graph.node(edge.from()) == null) atEdge("edge-unknown-node", edge.id(), "side", "from", "node", edge.from());
            if (graph.node(edge.to()) == null) atEdge("edge-unknown-node", edge.id(), "side", "to", "node", edge.to());
            ProcessNode from = graph.node(edge.from());
            if (from == null) continue;
            if (edge.outcome() != null) {
                if (!ProcessNode.BRANCHING_TYPES.contains(from.type())) atEdge("edge-outcome-not-branching", edge.id(), "outcome", edge.outcome());
                else {
                    List<String> declared = declaredOutcomes(from);
                    if (declared != null && !declared.contains(edge.outcome())) atEdge("edge-unknown-outcome", edge.id(), "outcome", edge.outcome());
                }
            }
            if (edge.isDefault() && !from.type().equals("exclusive") && !from.type().equals("inclusive")) atEdge("edge-default-not-gateway", edge.id());
            if (edge.when() != null && !Conditions.isComplete(edge.when())) atEdge("condition-incomplete", edge.id());
        }
    }

    /** 节点写明了的结果；自动节点、调用流程的结果由运行时给出，没有清单 */
    private static List<String> declaredOutcomes(ProcessNode node) {
        if (node instanceof ProcessNode.Task task) return task.outcomes().stream().map(Outcome::key).toList();
        if (node instanceof ProcessNode.Receive receive) return receive.outcomes();
        return null;
    }

    // ---------- 各类节点 ----------

    private void checkNode(ProcessNode node) {
        List<ProcessEdge> out = graph.outgoing(node.id());
        if (node.type().equals("end")) {
            if (!out.isEmpty()) atNode("end-outgoing", node.id());
            return;
        }
        if (out.isEmpty()) atNode("node-no-outgoing", node.id());
        if (node instanceof ProcessNode.Task task) checkTask(task);
        if (node instanceof ProcessNode.Receive receive) {
            for (String key : duplicates(receive.outcomes())) atNode("outcome-duplicate", node.id(), "outcome", key);
            checkUnrouted(node.id(), receive.outcomes(), out);
            if (receive.overdue() != null && "complete".equals(receive.overdue().action()) && !receive.outcomes().contains(receive.overdue().outcome())) {
                atNode("overdue-unknown-outcome", node.id(), "outcome", receive.overdue().outcome());
            }
        }
        if (node.type().equals("exclusive") || node.type().equals("inclusive")) {
            if (out.stream().filter(ProcessEdge::isDefault).count() > 1) atNode("gateway-multiple-default", node.id());
            if (out.size() > 1) for (ProcessEdge edge : out) if (edge.when() == null && !edge.isDefault()) atEdge("gateway-edge-unconditional", edge.id());
        }
        if (node.type().equals("parallel")) {
            for (ProcessEdge edge : out) if (edge.when() != null) atEdge("parallel-edge-condition", edge.id());
            if (out.size() == 1 && graph.incoming(node.id()).size() == 1) atNode("parallel-useless", node.id());
        }
    }

    private void checkTask(ProcessNode.Task node) {
        List<String> keys = node.outcomes().stream().map(Outcome::key).toList();
        if (keys.isEmpty()) atNode("task-no-outcome", node.id());
        for (String key : duplicates(keys)) atNode("outcome-duplicate", node.id(), "outcome", key);
        checkUnrouted(node.id(),
            node.outcomes().stream().filter(o -> !"terminate".equals(o.kind())).map(Outcome::key).toList(), graph.outgoing(node.id()));
        if (node.overdue() != null && "complete".equals(node.overdue().action()) && !keys.contains(node.overdue().outcome())) {
            atNode("overdue-unknown-outcome", node.id(), "outcome", node.overdue().outcome());
        }
        Lane lane = graph.lane(node.lane());
        if (node.assignees().isEmpty() && (lane == null || lane.inbox() == null)) atNode("assignee-missing", node.id());
        List<AssigneeSource> sources = new ArrayList<>(node.assignees());
        if (node.multi() != null) sources.addAll(node.multi().participantSources());
        for (AssigneeSource source : flatten(sources)) {
            if (source instanceof AssigneeSource.SameAs sameAs && !(graph.node(sameAs.node()) instanceof ProcessNode.Task)) {
                atNode("assignee-unknown-node", node.id(), "sameAs", sameAs.node());
            }
        }
        MultiInstance multi = node.multi();
        if (multi != null && (multi.complete().equals("ratio") || multi.complete().equals("votes"))) {
            if (multi.fallback() == null) atNode("multi-no-fallback", node.id());
            else if (!keys.contains(multi.fallback())) atNode("multi-no-fallback", node.id(), "fallback", multi.fallback());
        }
        if (multi != null && !MultiInstance.BUILTIN.contains(multi.complete())
            && Boolean.FALSE.equals(context.isRegistered(ExtensionKind.COMPLETION, multi.complete()))) {
            atNode("extension-unknown", node.id(), "kind", "completion", "name", multi.complete());
        }
    }

    /** 每个结果都要有出边：写明这个结果的，或没写结果的 */
    private void checkUnrouted(String nodeId, List<String> outcomes, List<ProcessEdge> out) {
        boolean catchAll = out.stream().anyMatch(e -> e.outcome() == null);
        for (String key : new LinkedHashSet<>(outcomes)) {
            if (!catchAll && out.stream().noneMatch(e -> key.equals(e.outcome()))) atNode("outcome-unrouted", nodeId, "outcome", key);
        }
    }

    /** 来源连同"上一步选"里嵌着的来源，平铺开 */
    private static List<AssigneeSource> flatten(List<AssigneeSource> sources) {
        List<AssigneeSource> out = new ArrayList<>();
        for (AssigneeSource source : sources) {
            out.add(source);
            if (source instanceof AssigneeSource.Pick pick) out.addAll(flatten(pick.from()));
        }
        return out;
    }

    // ---------- 走得通 ----------

    private void checkReachability() {
        ProcessNode.Start start = graph.start();
        if (start == null) return;
        Set<String> reached = walk(List.of(start.id()), id -> graph.outgoing(id).stream().map(ProcessEdge::to).toList());
        for (ProcessNode node : definition.nodes()) if (!reached.contains(node.id())) atNode("node-unreachable", node.id());
        List<String> ends = definition.nodes().stream().filter(n -> n.type().equals("end")).map(ProcessNode::id).toList();
        if (ends.isEmpty()) return;
        Set<String> reachesEnd = walk(ends, id -> graph.incoming(id).stream().map(ProcessEdge::from).toList());
        for (ProcessNode node : definition.nodes()) {
            if (reached.contains(node.id()) && !reachesEnd.contains(node.id())) atNode("node-dead-end", node.id());
        }
    }

    /** 从 roots 出发能走到的节点 */
    private static Set<String> walk(List<String> roots, Function<String, List<String>> next) {
        Set<String> seen = new LinkedHashSet<>(roots);
        Deque<String> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            for (String to : next.apply(queue.poll())) if (seen.add(to)) queue.add(to);
        }
        return seen;
    }

    // ---------- 并行与包容的配对（0005 的 4.4） ----------

    /** 去掉退回、提级连线后的前进图 */
    private List<ProcessEdge> forwardEdges() {
        return definition.edges().stream()
            .filter(e -> !e.isBackward() && graph.node(e.from()) != null && graph.node(e.to()) != null).toList();
    }

    /** 直接支配节点（Cooper–Harvey–Kennedy 的迭代算法）；root 映射到自己，走不到的节点不在结果里 */
    private static Map<String, String> immediateDominators(String root, Function<String, List<String>> successors,
                                                           Function<String, List<String>> predecessors) {
        List<String> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        postOrder(root, successors, visited, order);
        List<String> rpo = new ArrayList<>(order);
        java.util.Collections.reverse(rpo);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < rpo.size(); i++) index.put(rpo.get(i), i);
        Map<String, String> idom = new HashMap<>();
        idom.put(root, root);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String id : rpo) {
                if (id.equals(root)) continue;
                List<String> preds = predecessors.apply(id).stream().filter(idom::containsKey).toList();
                if (preds.isEmpty()) continue;
                String next = preds.get(0);
                for (String p : preds.subList(1, preds.size())) next = intersect(next, p, idom, index);
                if (!next.equals(idom.get(id))) {
                    idom.put(id, next);
                    changed = true;
                }
            }
        }
        return idom;
    }

    private static void postOrder(String id, Function<String, List<String>> successors, Set<String> visited, List<String> order) {
        visited.add(id);
        for (String next : successors.apply(id)) if (!visited.contains(next)) postOrder(next, successors, visited, order);
        order.add(id);
    }

    private static String intersect(String a, String b, Map<String, String> idom, Map<String, Integer> index) {
        String x = a;
        String y = b;
        while (!x.equals(y)) {
            while (index.get(x) > index.get(y)) x = idom.get(x);
            while (index.get(y) > index.get(x)) y = idom.get(y);
        }
        return x;
    }

    private void checkGateways() {
        ProcessNode.Start start = graph.start();
        if (start == null) return;
        Map<String, List<String>> succ = new HashMap<>();
        Map<String, List<String>> pred = new HashMap<>();
        for (ProcessEdge e : forwardEdges()) {
            succ.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e.to());
            pred.computeIfAbsent(e.to(), k -> new ArrayList<>()).add(e.from());
        }
        for (ProcessNode node : definition.nodes()) {
            if (node.type().equals("end")) {
                succ.computeIfAbsent(node.id(), k -> new ArrayList<>()).add(EXIT);
                pred.computeIfAbsent(EXIT, k -> new ArrayList<>()).add(node.id());
            }
        }
        Function<String, List<String>> successors = id -> succ.getOrDefault(id, List.of());
        Function<String, List<String>> predecessors = id -> pred.getOrDefault(id, List.of());
        Map<String, String> idom = immediateDominators(start.id(), successors, predecessors);
        Map<String, String> ipdom = immediateDominators(EXIT, predecessors, successors);
        for (ProcessNode node : definition.nodes()) {
            if (!node.type().equals("parallel") && !node.type().equals("inclusive")) continue;
            if (!idom.containsKey(node.id()) || !ipdom.containsKey(node.id())) continue;
            boolean isFork = succ.getOrDefault(node.id(), List.of()).size() > 1;
            boolean isJoin = pred.getOrDefault(node.id(), List.of()).size() > 1;
            if (isFork && !paired(node.id(), ipdom.get(node.id()), idom, pred)) atNode("gateway-unpaired", node.id());
            if (isJoin) {
                String fork = idom.get(node.id());
                if (fork == null || !paired(fork, node.id(), idom, pred) || !node.id().equals(ipdom.get(fork))) atNode("gateway-unpaired", node.id());
            }
        }
    }

    /** fork 与 join 配对：同类型、join 有不止一条入边、join 的直接支配节点就是 fork */
    private boolean paired(String fork, String join, Map<String, String> idom, Map<String, List<String>> pred) {
        if (join == null || join.equals(EXIT)) return false;
        ProcessNode a = graph.node(fork);
        ProcessNode b = graph.node(join);
        return a != null && b != null && a.type().equals(b.type())
            && pred.getOrDefault(join, List.of()).size() > 1 && fork.equals(idom.get(join));
    }

    // ---------- 退回与提级 ----------

    private void checkReturnsAndEscalations() {
        List<ProcessEdge> edges = forwardEdges();
        Function<String, List<String>> next = id -> edges.stream().filter(e -> e.from().equals(id)).map(ProcessEdge::to).toList();
        for (ProcessEdge edge : definition.edges()) {
            if (graph.node(edge.from()) == null || graph.node(edge.to()) == null) continue;
            if ("return".equals(edge.kind()) && !walk(List.of(edge.to()), next).contains(edge.from())) atEdge("return-not-upstream", edge.id());
            if ("escalate".equals(edge.kind())) {
                if (!(graph.node(edge.to()) instanceof ProcessNode.Task target) || !hasInbox(target)) atEdge("escalate-no-inbox", edge.id());
            }
        }
    }

    /** 人工节点有没有收件箱：处理人里有角色、部门或扩展的种类，或者没写处理人而泳道有收件箱 */
    private boolean hasInbox(ProcessNode.Task node) {
        if (node.assignees().isEmpty()) {
            Lane lane = graph.lane(node.lane());
            return lane != null && lane.inbox() != null;
        }
        return node.assignees().stream().anyMatch(AssigneeSource::isInbox);
    }

    // ---------- 引用（0005 的 1.1） ----------

    /** 期望的类型，查 ref-type-mismatch */
    private enum Expect { ANY, PEOPLE, DATE, LIST }

    /** 一处引用 */
    private record Reference(String name, String node, String edge, boolean inCondition, Expect expect) { }

    private void checkReferences() {
        Map<String, ProcessDefinition.Variable> variables = new HashMap<>();
        for (ProcessDefinition.Variable v : definition.variables()) variables.putIfAbsent(v.name(), v);
        for (Reference ref : collectReferences()) {
            String[] problem = resolve(ref, variables);
            if (problem != null) {
                String[] params = java.util.Arrays.copyOfRange(problem, 1, problem.length);
                report(problem[0], ref.node(), ref.edge(), null, params);
            }
        }
        for (ProcessEdge edge : definition.edges()) {
            for (ProcessEdge.Effect effect : edge.effects()) {
                if (!variables.containsKey(effect.target())) atEdge("ref-unknown", edge.id(), "ref", effect.target());
            }
        }
    }

    /** 引用有问题时返回 {代号, 参数名, 参数值, ...}，没问题时为 null */
    private String[] resolve(Reference ref, Map<String, ProcessDefinition.Variable> variables) {
        String name = ref.name();
        String[] unknown = {"ref-unknown", "ref", name};
        if (name.startsWith("form.")) {
            if (definition.form() == null) return unknown;
            FormManifest form = context.form();
            if (form == null) return null;
            FormManifest.Field field = form.field(name.substring(5));
            if (field == null) return unknown;
            return mismatch(ref.expect(), field.type(), field.multiple()) ? new String[] {"ref-type-mismatch", "ref", name, "type", field.type()} : null;
        }
        if (name.startsWith("sys.")) {
            String rest = name.substring(4);
            if (SYSTEM_NAMES.contains(rest)) return null;
            if (rest.startsWith("round.") && graph.node(rest.substring(6)) != null) return null;
            return unknown;
        }
        if (name.startsWith("fn.")) {
            if (!ref.inCondition()) return unknown;
            return Boolean.FALSE.equals(context.isRegistered(ExtensionKind.COMPUTED, name.substring(3)))
                ? new String[] {"extension-unknown", "kind", "computed", "name", name.substring(3)} : null;
        }
        ProcessDefinition.Variable variable = variables.get(name);
        if (variable == null) return unknown;
        return mismatch(ref.expect(), variable.type(), false) ? new String[] {"ref-type-mismatch", "ref", name, "type", variable.type()} : null;
    }

    /** 引用的类型用不上 */
    private static boolean mismatch(Expect expect, String type, boolean multiple) {
        return switch (expect) {
            case PEOPLE -> !type.equals("people") && !type.equals("text") && !type.equals("select");
            case DATE -> !type.equals("date") && !type.equals("datetime");
            case LIST -> !type.equals("list") && !type.equals("people") && !(type.equals("select") && multiple);
            case ANY -> false;
        };
    }

    /** 定义里所有的引用，连同它们在哪；先后同 TS 一侧 */
    private List<Reference> collectReferences() {
        List<Reference> refs = new ArrayList<>();
        for (String name : definition.summary()) refs.add(new Reference(name, null, null, false, Expect.ANY));
        if (definition.title() != null) {
            Matcher m = PLACEHOLDER.matcher(definition.title());
            while (m.find()) refs.add(new Reference(m.group(1).trim(), null, null, false, Expect.ANY));
        }
        addSources(refs, definition.starters(), null);
        for (Lane lane : definition.lanes()) if (lane.inbox() != null) addSources(refs, lane.inbox().manager(), null);
        for (ProcessNode node : definition.nodes()) {
            String at = node.id();
            if (node instanceof ProcessNode.Task task) {
                addSources(refs, task.assignees(), at);
                if (task.multi() != null) {
                    if (task.multi().participants() instanceof Ref r) add(refs, r, at, null, Expect.LIST);
                    else addSources(refs, task.multi().participantSources(), at);
                }
                addDue(refs, task.due(), at);
            } else if (node instanceof ProcessNode.Timer timer) {
                addDue(refs, timer.waitFor(), at);
            } else if (node instanceof ProcessNode.Receive receive) {
                addDue(refs, receive.due(), at);
            } else if (node instanceof ProcessNode.Cc cc) {
                addSources(refs, cc.to(), at);
            } else if (node instanceof ProcessNode.Call call) {
                add(refs, call.forEach(), at, null, Expect.LIST);
                for (Object value : call.input().values()) {
                    // forEach 循环里那一项的名字只在 input 里有效
                    if (value instanceof Ref r && Objects.equals(r.ref(), call.as())) continue;
                    add(refs, value, at, null, Expect.ANY);
                }
            } else if (node instanceof ProcessNode.Service service) {
                for (Object value : service.args().values()) add(refs, value, at, null, Expect.ANY);
            }
        }
        for (ProcessEdge edge : definition.edges()) {
            if (edge.when() != null) {
                for (String field : Conditions.fields(edge.when())) refs.add(new Reference(field, null, edge.id(), true, Expect.ANY));
            }
            for (ProcessEdge.Effect effect : edge.effects()) {
                if (effect instanceof ProcessEdge.Effect.Set set) add(refs, set.value(), null, edge.id(), Expect.ANY);
            }
        }
        return refs;
    }

    private static void add(List<Reference> refs, Object value, String node, String edge, Expect expect) {
        if (value instanceof Ref r) refs.add(new Reference(r.ref(), node, edge, false, expect));
    }

    private static void addSources(List<Reference> refs, List<AssigneeSource> sources, String node) {
        for (AssigneeSource source : flatten(sources)) {
            if (source instanceof AssigneeSource.User u) add(refs, u.user(), node, null, Expect.PEOPLE);
            if (source instanceof AssigneeSource.Role r) add(refs, r.role(), node, null, Expect.ANY);
            if (source instanceof AssigneeSource.Department d) add(refs, d.department(), node, null, Expect.ANY);
            if (source instanceof AssigneeSource.Group g) add(refs, g.id(), node, null, Expect.ANY);
        }
    }

    private static void addDue(List<Reference> refs, Due due, String node) {
        if (due instanceof Due.Until until) add(refs, until.value(), node, null, Expect.DATE);
    }

    // ---------- 表单（0004 第 4 节） ----------

    private void checkForm() {
        FormManifest manifest = context.form();
        if (manifest == null) return;
        for (ProcessNode node : definition.nodes()) {
            var access = node instanceof ProcessNode.Task t ? t.form() : node instanceof ProcessNode.Start s ? s.form() : null;
            if (access == null) continue;
            for (String path : access.fields().keySet()) {
                if (manifest.field(path) == null) atNode("form-field-unknown", node.id(), "field", path);
            }
        }
        ProcessNode.Start start = definition.nodes().stream()
            .filter(n -> n.type().equals("start")).findFirst()
            .filter(n -> n instanceof ProcessNode.Start).map(n -> (ProcessNode.Start) n).orElse(null);
        if (start == null) return;
        for (Map.Entry<String, FormManifest.Field> entry : manifest.fields().entrySet()) {
            FieldAccess access = startAccess(start, entry.getKey());
            if (entry.getValue().required() && (access == FieldAccess.HIDDEN || access == FieldAccess.READONLY)) {
                atNode("form-required-unfillable", start.id(), "field", entry.getKey());
            }
        }
    }

    private static FieldAccess startAccess(ProcessNode.Start start, String field) {
        if (start.form() == null) return FieldAccess.EDITABLE;
        FieldAccess own = start.form().fields().get(field);
        if (own != null) return own;
        return start.form().defaultAccess() == null ? FieldAccess.EDITABLE : start.form().defaultAccess();
    }

    // ---------- 只在发布时查的（要后端的注册情况） ----------

    private void checkPublishOnly() {
        for (ProcessNode node : definition.nodes()) {
            if (node.type().startsWith("x-")) need(ExtensionKind.NODE, node.type(), node.id());
            if (node instanceof ProcessNode.Service service) need(ExtensionKind.ACTION, service.action(), node.id());
            if (node instanceof ProcessNode.Task task) {
                for (Outcome outcome : task.outcomes()) for (String guard : outcome.guards()) need(ExtensionKind.GUARD, guard, node.id());
                for (AssigneeSource source : flatten(task.assignees())) {
                    if (source instanceof AssigneeSource.Resolver resolver) need(ExtensionKind.RESOLVER, resolver.name(), node.id());
                }
            }
        }
        for (ProcessNode node : definition.nodes()) {
            if (node instanceof ProcessNode.Call call && Boolean.FALSE.equals(context.processExists(call.process()))) {
                atNode("call-unknown-process", node.id(), "process", call.process());
            }
        }
        List<String> calls = definition.nodes().stream()
            .filter(n -> n instanceof ProcessNode.Call).map(n -> ((ProcessNode.Call) n).process()).toList();
        if (!calls.isEmpty() && context.callsOf(calls.get(0)) != null) {
            Set<String> reaches = walk(calls, key -> {
                if (key.equals(definition.key())) return List.of();
                List<String> next = context.callsOf(key);
                return next == null ? List.of() : next;
            });
            if (reaches.contains(definition.key())) atDefinition("call-cycle", "process", definition.key());
        }
        String calendar = definition.settings().calendar();
        if (calendar != null && Boolean.FALSE.equals(context.calendarExists(calendar))) atDefinition("due-unknown-calendar", "calendar", calendar);
    }

    private void need(ExtensionKind kind, String name, String node) {
        if (Boolean.FALSE.equals(context.isRegistered(kind, name))) {
            atNode("extension-unknown", node, "kind", kind.name().toLowerCase(Locale.ROOT), "name", name);
        }
    }
}
