package org.dpdns.zerodep.flow.model.validate;

import lombok.Data;
import org.dpdns.zerodep.flow.model.Conditions;
import org.dpdns.zerodep.flow.model.definition.AssigneeSource;
import org.dpdns.zerodep.flow.model.definition.Due;
import org.dpdns.zerodep.flow.model.definition.FieldAccess;
import org.dpdns.zerodep.flow.model.definition.GatewayPairs;
import org.dpdns.zerodep.flow.model.definition.Lane;
import org.dpdns.zerodep.flow.model.definition.MultiInstance;
import org.dpdns.zerodep.flow.model.definition.OrgId;
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
public class ProcessValidator {
    /** 各代号的严重程度；没列出的都是错误 */
    private static final Set<String> WARNINGS = Set.of(
        "node-unreachable", "edge-default-not-gateway", "gateway-edge-unconditional", "parallel-edge-condition", "parallel-useless",
        "form-required-unfillable");

    /** {@code sys.} 下引擎给的只读值；{@code sys.round.节点} 另外判断 */
    private static final Set<String> SYSTEM_NAMES = Set.of("initiator", "startedAt", "instanceId", "businessKey");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)\\}");

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
        for (ProcessNode node : definition.getNodes()) validator.checkNode(node);
        validator.checkReachability();
        validator.checkGateways();
        validator.checkReturnsAndEscalations();
        validator.checkReferences();
        validator.checkOrgIds();
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
        for (String id : duplicates(definition.getNodes().stream().map(ProcessNode::getId).toList())) atNode("node-duplicate", id);
        for (String id : duplicates(definition.getEdges().stream().map(ProcessEdge::getId).toList())) atEdge("edge-duplicate", id);
        for (String id : duplicates(definition.getLanes().stream().map(Lane::getId).toList())) report("lane-duplicate", null, null, id);
        for (ProcessNode node : definition.getNodes()) {
            if (!ProcessNode.BUILTIN_TYPES.contains(node.getType()) && !node.getType().startsWith("x-")) atNode("node-type-unknown", node.getId(), "type", node.getType());
            if (node.getLane() != null && definition.getLanes().stream().noneMatch(l -> l.getId().equals(node.getLane()))) {
                atNode("lane-unknown", node.getId(), "lane", node.getLane());
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
        List<ProcessNode> starts = definition.getNodes().stream().filter(n -> n.getType().equals("start")).toList();
        if (starts.isEmpty()) atDefinition("start-missing");
        for (ProcessNode extra : starts.subList(Math.min(1, starts.size()), starts.size())) atNode("start-multiple", extra.getId());
        for (ProcessNode start : starts) if (!graph.incoming(start.getId()).isEmpty()) atNode("start-incoming", start.getId());
        if (definition.getNodes().stream().noneMatch(n -> n.getType().equals("end"))) atDefinition("end-missing");
    }

    private void checkEdges() {
        for (ProcessEdge edge : definition.getEdges()) {
            if (graph.node(edge.getFrom()) == null) atEdge("edge-unknown-node", edge.getId(), "side", "from", "node", edge.getFrom());
            if (graph.node(edge.getTo()) == null) atEdge("edge-unknown-node", edge.getId(), "side", "to", "node", edge.getTo());
            ProcessNode from = graph.node(edge.getFrom());
            if (from == null) continue;
            if (edge.getOutcome() != null) {
                if (!ProcessNode.isBranching(from.getType())) atEdge("edge-outcome-not-branching", edge.getId(), "outcome", edge.getOutcome());
                else {
                    List<String> declared = declaredOutcomes(from);
                    if (declared != null && !declared.contains(edge.getOutcome())) atEdge("edge-unknown-outcome", edge.getId(), "outcome", edge.getOutcome());
                }
            }
            if (edge.isDefault() && !from.getType().equals("exclusive") && !from.getType().equals("inclusive")) atEdge("edge-default-not-gateway", edge.getId());
            if (edge.getWhen() != null && !Conditions.isComplete(edge.getWhen())) atEdge("condition-incomplete", edge.getId());
        }
    }

    /** 节点写明了的结果；自动节点、调用流程的结果由运行时给出，没有清单 */
    private static List<String> declaredOutcomes(ProcessNode node) {
        if (node instanceof ProcessNode.Task task) return task.getOutcomes().stream().map(Outcome::getKey).toList();
        if (node instanceof ProcessNode.Receive receive) return receive.getOutcomes();
        return null;
    }

    // ---------- 各类节点 ----------

    private void checkNode(ProcessNode node) {
        List<ProcessEdge> out = graph.outgoing(node.getId());
        if (node.getType().equals("end")) {
            if (!out.isEmpty()) atNode("end-outgoing", node.getId());
            return;
        }
        if (out.isEmpty()) atNode("node-no-outgoing", node.getId());
        if (node instanceof ProcessNode.Task task) checkTask(task);
        if (node instanceof ProcessNode.Receive receive) {
            for (String key : duplicates(receive.getOutcomes())) atNode("outcome-duplicate", node.getId(), "outcome", key);
            checkUnrouted(node.getId(), receive.getOutcomes(), out);
            if (receive.getOverdue() != null && "complete".equals(receive.getOverdue().getAction()) && !receive.getOutcomes().contains(receive.getOverdue().getOutcome())) {
                atNode("overdue-unknown-outcome", node.getId(), "outcome", receive.getOverdue().getOutcome());
            }
        }
        if (node.getType().equals("exclusive") || node.getType().equals("inclusive")) {
            if (out.stream().filter(ProcessEdge::isDefault).count() > 1) atNode("gateway-multiple-default", node.getId());
            if (out.size() > 1) for (ProcessEdge edge : out) if (edge.getWhen() == null && !edge.isDefault()) atEdge("gateway-edge-unconditional", edge.getId());
        }
        if (node.getType().equals("parallel")) {
            for (ProcessEdge edge : out) if (edge.getWhen() != null) atEdge("parallel-edge-condition", edge.getId());
            if (out.size() == 1 && graph.incoming(node.getId()).size() == 1) atNode("parallel-useless", node.getId());
        }
    }

    private void checkTask(ProcessNode.Task node) {
        List<String> keys = node.getOutcomes().stream().map(Outcome::getKey).toList();
        if (keys.isEmpty()) atNode("task-no-outcome", node.getId());
        for (String key : duplicates(keys)) atNode("outcome-duplicate", node.getId(), "outcome", key);
        // 终止的结果、处理时选退回节点的结果不走出边
        checkUnrouted(node.getId(), node.getOutcomes().stream().filter(o -> !"terminate".equals(o.getKind()) && !o.choosesTarget())
            .map(Outcome::getKey).toList(), graph.outgoing(node.getId()));
        for (Outcome outcome : node.getOutcomes()) {
            if (outcome.getTarget() != null && !outcome.choosesTarget()) atNode("return-target-invalid", node.getId(), "outcome", outcome.getKey());
        }
        if (node.getOverdue() != null && "complete".equals(node.getOverdue().getAction()) && !keys.contains(node.getOverdue().getOutcome())) {
            atNode("overdue-unknown-outcome", node.getId(), "outcome", node.getOverdue().getOutcome());
        }
        // 引擎自己选的结果（超时自动完成、会签没达到门槛时的 fallback）没人选退回的节点
        if (node.getOverdue() != null && "complete".equals(node.getOverdue().getAction())) checkNotChoosing(node, node.getOverdue().getOutcome());
        if (node.getMulti() != null && node.getMulti().getFallback() != null) checkNotChoosing(node, node.getMulti().getFallback());
        Lane lane = graph.lane(node.getLane());
        if (node.getAssignees().isEmpty() && (lane == null || lane.getInbox() == null)) atNode("assignee-missing", node.getId());
        List<AssigneeSource> sources = new ArrayList<>(node.getAssignees());
        if (node.getMulti() != null) sources.addAll(node.getMulti().participantSources());
        for (AssigneeSource source : flatten(sources)) {
            if (source instanceof AssigneeSource.SameAs sameAs && !(graph.node(sameAs.getNode()) instanceof ProcessNode.Task)) {
                atNode("assignee-unknown-node", node.getId(), "sameAs", sameAs.getNode());
            }
        }
        MultiInstance multi = node.getMulti();
        if (multi != null && (multi.getComplete().equals("ratio") || multi.getComplete().equals("votes"))) {
            if (multi.getFallback() == null) atNode("multi-no-fallback", node.getId());
            else if (!keys.contains(multi.getFallback())) atNode("multi-no-fallback", node.getId(), "fallback", multi.getFallback());
        }
        if (multi != null && !MultiInstance.BUILTIN.contains(multi.getComplete())
            && Boolean.FALSE.equals(context.isRegistered(ExtensionKind.COMPLETION, multi.getComplete()))) {
            atNode("extension-unknown", node.getId(), "kind", "completion", "name", multi.getComplete());
        }
    }

    /** 引擎自己选的结果不能是要处理人选退回节点的 */
    private void checkNotChoosing(ProcessNode.Task node, String key) {
        boolean choosing = node.getOutcomes().stream().anyMatch(o -> o.getKey().equals(key) && o.choosesTarget());
        if (choosing) atNode("outcome-choose-automatic", node.getId(), "outcome", key);
    }

    /** 每个结果都要有出边：写明这个结果的，或没写结果的 */
    private void checkUnrouted(String nodeId, List<String> outcomes, List<ProcessEdge> out) {
        boolean catchAll = out.stream().anyMatch(e -> e.getOutcome() == null);
        for (String key : new LinkedHashSet<>(outcomes)) {
            if (!catchAll && out.stream().noneMatch(e -> key.equals(e.getOutcome()))) atNode("outcome-unrouted", nodeId, "outcome", key);
        }
    }

    /** 来源连同"上一步选"里嵌着的来源，平铺开 */
    private static List<AssigneeSource> flatten(List<AssigneeSource> sources) {
        List<AssigneeSource> out = new ArrayList<>();
        for (AssigneeSource source : sources) {
            out.add(source);
            if (source instanceof AssigneeSource.Pick pick) out.addAll(flatten(pick.getFrom()));
        }
        return out;
    }

    // ---------- 走得通 ----------

    private void checkReachability() {
        ProcessNode.Start start = graph.start();
        if (start == null) return;
        Set<String> reached = walk(List.of(start.getId()), id -> graph.outgoing(id).stream().map(ProcessEdge::getTo).toList());
        for (ProcessNode node : definition.getNodes()) if (!reached.contains(node.getId())) atNode("node-unreachable", node.getId());
        List<String> ends = definition.getNodes().stream().filter(n -> n.getType().equals("end")).map(ProcessNode::getId).toList();
        if (ends.isEmpty()) return;
        Set<String> reachesEnd = walk(ends, id -> graph.incoming(id).stream().map(ProcessEdge::getFrom).toList());
        for (ProcessNode node : definition.getNodes()) {
            if (reached.contains(node.getId()) && !reachesEnd.contains(node.getId())) atNode("node-dead-end", node.getId());
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
        return definition.getEdges().stream()
            .filter(e -> !e.isBackward() && graph.node(e.getFrom()) != null && graph.node(e.getTo()) != null).toList();
    }

    /** 并行、包容的分叉与汇合要配对（算法在 GatewayPairs，引擎推进时用同一份） */
    private void checkGateways() {
        for (String id : GatewayPairs.of(definition).unpaired()) atNode("gateway-unpaired", id);
    }

    // ---------- 退回与提级 ----------

    private void checkReturnsAndEscalations() {
        List<ProcessEdge> edges = forwardEdges();
        Function<String, List<String>> next = id -> edges.stream().filter(e -> e.getFrom().equals(id)).map(ProcessEdge::getTo).toList();
        for (ProcessEdge edge : definition.getEdges()) {
            if (graph.node(edge.getFrom()) == null || graph.node(edge.getTo()) == null) continue;
            if ("return".equals(edge.getKind()) && !walk(List.of(edge.getTo()), next).contains(edge.getFrom())) atEdge("return-not-upstream", edge.getId());
            if ("escalate".equals(edge.getKind())) {
                if (!(graph.node(edge.getTo()) instanceof ProcessNode.Task target) || !hasInbox(target)) atEdge("escalate-no-inbox", edge.getId());
            }
        }
    }

    /** 人工节点有没有收件箱：处理人里有角色、部门或扩展的种类，或者没写处理人而泳道有收件箱 */
    private boolean hasInbox(ProcessNode.Task node) {
        if (node.getAssignees().isEmpty()) {
            Lane lane = graph.lane(node.getLane());
            return lane != null && lane.getInbox() != null;
        }
        return node.getAssignees().stream().anyMatch(AssigneeSource::isInbox);
    }

    // ---------- 引用（0005 的 1.1） ----------

    /** 期望的类型，查 ref-type-mismatch */
    private enum Expect { ANY, PEOPLE, DATE, LIST }

    /** 一处引用 */
    @Data
    private static class Reference {
        private final String name;
        private final String node;
        private final String edge;
        private final boolean inCondition;
        private final Expect expect;
    }

    private void checkReferences() {
        Map<String, ProcessDefinition.Variable> variables = new HashMap<>();
        for (ProcessDefinition.Variable v : definition.getVariables()) variables.putIfAbsent(v.getName(), v);
        for (Reference ref : collectReferences()) {
            String[] problem = resolve(ref, variables);
            if (problem != null) {
                String[] params = java.util.Arrays.copyOfRange(problem, 1, problem.length);
                report(problem[0], ref.getNode(), ref.getEdge(), null, params);
            }
        }
        for (ProcessEdge edge : definition.getEdges()) {
            for (ProcessEdge.Effect effect : edge.getEffects()) {
                if (!variables.containsKey(effect.getTarget())) atEdge("ref-unknown", edge.getId(), "ref", effect.getTarget());
            }
        }
    }

    /** 引用有问题时返回 {代号, 参数名, 参数值, ...}，没问题时为 null */
    private String[] resolve(Reference ref, Map<String, ProcessDefinition.Variable> variables) {
        String name = ref.getName();
        String[] unknown = {"ref-unknown", "ref", name};
        if (name.startsWith("form.")) {
            if (definition.getForm() == null) return unknown;
            FormManifest form = context.form();
            if (form == null) return null;
            FormManifest.Field field = form.field(name.substring(5));
            if (field == null) return unknown;
            return mismatch(ref.getExpect(), field.getType(), field.isMultiple()) ? new String[] {"ref-type-mismatch", "ref", name, "type", field.getType()} : null;
        }
        if (name.startsWith("sys.")) {
            String rest = name.substring(4);
            if (SYSTEM_NAMES.contains(rest)) return null;
            if (rest.startsWith("round.") && graph.node(rest.substring(6)) != null) return null;
            return unknown;
        }
        if (name.startsWith("fn.")) {
            if (!ref.isInCondition()) return unknown;
            return Boolean.FALSE.equals(context.isRegistered(ExtensionKind.COMPUTED, name.substring(3)))
                ? new String[] {"extension-unknown", "kind", "computed", "name", name.substring(3)} : null;
        }
        ProcessDefinition.Variable variable = variables.get(name);
        if (variable == null) return unknown;
        return mismatch(ref.getExpect(), variable.getType(), false) ? new String[] {"ref-type-mismatch", "ref", name, "type", variable.getType()} : null;
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
        for (String name : definition.getSummary()) refs.add(new Reference(name, null, null, false, Expect.ANY));
        if (definition.getTitle() != null) {
            Matcher m = PLACEHOLDER.matcher(definition.getTitle());
            while (m.find()) refs.add(new Reference(m.group(1).trim(), null, null, false, Expect.ANY));
        }
        addSources(refs, definition.getStarters(), null);
        for (Lane lane : definition.getLanes()) if (lane.getInbox() != null) addSources(refs, lane.getInbox().getManager(), null);
        for (ProcessNode node : definition.getNodes()) {
            String at = node.getId();
            if (node instanceof ProcessNode.Task task) {
                addSources(refs, task.getAssignees(), at);
                if (task.getMulti() != null) {
                    if (task.getMulti().getParticipants() instanceof Ref r) add(refs, r, at, null, Expect.LIST);
                    else addSources(refs, task.getMulti().participantSources(), at);
                }
                addDue(refs, task.getDue(), at);
            } else if (node instanceof ProcessNode.Timer timer) {
                addDue(refs, timer.getWaitFor(), at);
            } else if (node instanceof ProcessNode.Receive receive) {
                addDue(refs, receive.getDue(), at);
            } else if (node instanceof ProcessNode.Cc cc) {
                addSources(refs, cc.getTo(), at);
            } else if (node instanceof ProcessNode.Call call) {
                add(refs, call.getForEach(), at, null, Expect.LIST);
                for (Object value : call.getInput().values()) {
                    // forEach 循环里那一项（及它的字段 as.名字）只在 input 里有效
                    if (value instanceof Ref r && call.getAs() != null && (r.getRef().equals(call.getAs()) || r.getRef().startsWith(call.getAs() + "."))) continue;
                    add(refs, value, at, null, Expect.ANY);
                }
            } else if (node instanceof ProcessNode.Service service) {
                for (Object value : service.getArgs().values()) add(refs, value, at, null, Expect.ANY);
            }
        }
        for (ProcessEdge edge : definition.getEdges()) {
            if (edge.getWhen() != null) {
                for (String field : Conditions.fields(edge.getWhen())) refs.add(new Reference(field, null, edge.getId(), true, Expect.ANY));
            }
            for (ProcessEdge.Effect effect : edge.getEffects()) {
                if (effect instanceof ProcessEdge.Effect.Set set) add(refs, set.getValue(), null, edge.getId(), Expect.ANY);
            }
        }
        return refs;
    }

    private static void add(List<Reference> refs, Object value, String node, String edge, Expect expect) {
        if (value instanceof Ref r) refs.add(new Reference(r.getRef(), node, edge, false, expect));
    }

    private static void addSources(List<Reference> refs, List<AssigneeSource> sources, String node) {
        for (AssigneeSource source : flatten(sources)) {
            if (source instanceof AssigneeSource.User u) add(refs, u.getUser(), node, null, Expect.PEOPLE);
            if (source instanceof AssigneeSource.Role r) add(refs, r.getRole(), node, null, Expect.ANY);
            if (source instanceof AssigneeSource.Department d) add(refs, d.getDepartment(), node, null, Expect.ANY);
            if (source instanceof AssigneeSource.Group g) add(refs, g.getId(), node, null, Expect.ANY);
        }
    }

    private static void addDue(List<Reference> refs, Due due, String node) {
        if (due instanceof Due.Until until) add(refs, until.getValue(), node, null, Expect.DATE);
    }

    // ---------- 组织的编号 ----------

    /**
     * 直接写在定义里的用户、角色、部门编号要是正整数（接入方组织表的主键），写成名字（{@code "role": "finance"}）时报 {@code org-id-invalid}；
     * 引用（{@code { ref }}）到运行时才有值，不在这里查。走的来源同 {@link #collectReferences}，另加泳道的收件箱本身。
     */
    private void checkOrgIds() {
        checkOrgIds(definition.getStarters(), null);
        for (Lane lane : definition.getLanes()) {
            if (lane.getInbox() == null) continue;
            checkOrgIds(List.of(lane.getInbox().getSource()), null);
            checkOrgIds(lane.getInbox().getManager(), null);
        }
        for (ProcessNode node : definition.getNodes()) {
            if (node instanceof ProcessNode.Task task) {
                checkOrgIds(task.getAssignees(), node.getId());
                if (task.getMulti() != null && !(task.getMulti().getParticipants() instanceof Ref)) checkOrgIds(task.getMulti().participantSources(), node.getId());
            } else if (node instanceof ProcessNode.Cc cc) {
                checkOrgIds(cc.getTo(), node.getId());
            }
        }
    }

    private void checkOrgIds(List<AssigneeSource> sources, String node) {
        for (AssigneeSource source : flatten(sources)) {
            Object id = source instanceof AssigneeSource.User u ? u.getUser()
                : source instanceof AssigneeSource.Role r ? r.getRole()
                : source instanceof AssigneeSource.Department d ? d.getDepartment()
                : source instanceof AssigneeSource.Group g ? g.getId()
                : null;
            if (id != null && !(id instanceof Ref) && !OrgId.isValid(id)) atNode("org-id-invalid", node, "value", String.valueOf(id));
        }
    }

    // ---------- 表单（0004 第 4 节） ----------

    private void checkForm() {
        FormManifest manifest = context.form();
        if (manifest == null) return;
        for (ProcessNode node : definition.getNodes()) {
            var access = node instanceof ProcessNode.Task t ? t.getForm() : node instanceof ProcessNode.Start s ? s.getForm() : null;
            if (access == null) continue;
            for (String path : access.getFields().keySet()) {
                if (manifest.field(path) == null) atNode("form-field-unknown", node.getId(), "field", path);
            }
        }
        ProcessNode.Start start = definition.getNodes().stream()
            .filter(n -> n.getType().equals("start")).findFirst()
            .filter(n -> n instanceof ProcessNode.Start).map(n -> (ProcessNode.Start) n).orElse(null);
        if (start == null) return;
        for (Map.Entry<String, FormManifest.Field> entry : manifest.fields().entrySet()) {
            FieldAccess access = startAccess(start, entry.getKey());
            if (entry.getValue().isRequired() && (access == FieldAccess.HIDDEN || access == FieldAccess.READONLY)) {
                atNode("form-required-unfillable", start.getId(), "field", entry.getKey());
            }
        }
    }

    private static FieldAccess startAccess(ProcessNode.Start start, String field) {
        if (start.getForm() == null) return FieldAccess.EDITABLE;
        FieldAccess own = start.getForm().getFields().get(field);
        if (own != null) return own;
        return start.getForm().getDefaultAccess() == null ? FieldAccess.EDITABLE : start.getForm().getDefaultAccess();
    }

    // ---------- 只在发布时查的（要后端的注册情况） ----------

    private void checkPublishOnly() {
        for (ProcessNode node : definition.getNodes()) {
            if (node.getType().startsWith("x-")) need(ExtensionKind.NODE, node.getType(), node.getId());
            if (node instanceof ProcessNode.Service service) need(ExtensionKind.ACTION, service.getAction(), node.getId());
            if (node instanceof ProcessNode.Task task) {
                for (Outcome outcome : task.getOutcomes()) for (String guard : outcome.getGuards()) need(ExtensionKind.GUARD, guard, node.getId());
                for (AssigneeSource source : flatten(task.getAssignees())) {
                    if (source instanceof AssigneeSource.Resolver resolver) need(ExtensionKind.RESOLVER, resolver.getName(), node.getId());
                }
            }
        }
        for (ProcessNode node : definition.getNodes()) {
            if (node instanceof ProcessNode.Call call && Boolean.FALSE.equals(context.processExists(call.getProcess()))) {
                atNode("call-unknown-process", node.getId(), "process", call.getProcess());
            }
        }
        List<String> calls = definition.getNodes().stream()
            .filter(n -> n instanceof ProcessNode.Call).map(n -> ((ProcessNode.Call) n).getProcess()).toList();
        if (!calls.isEmpty() && context.callsOf(calls.get(0)) != null) {
            Set<String> reaches = walk(calls, key -> {
                if (key.equals(definition.getKey())) return List.of();
                List<String> next = context.callsOf(key);
                return next == null ? List.of() : next;
            });
            if (reaches.contains(definition.getKey())) atDefinition("call-cycle", "process", definition.getKey());
        }
        String calendar = definition.getSettings().getCalendar();
        if (calendar != null && Boolean.FALSE.equals(context.calendarExists(calendar))) atDefinition("due-unknown-calendar", "calendar", calendar);
    }

    private void need(ExtensionKind kind, String name, String node) {
        if (Boolean.FALSE.equals(context.isRegistered(kind, name))) {
            atNode("extension-unknown", node, "kind", kind.name().toLowerCase(Locale.ROOT), "name", name);
        }
    }
}
