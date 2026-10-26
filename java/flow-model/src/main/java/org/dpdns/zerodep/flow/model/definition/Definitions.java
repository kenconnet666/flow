package org.dpdns.zerodep.flow.model.definition;

import org.dpdns.zerodep.flow.model.Conditions;
import org.dpdns.zerodep.wire.Json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * 从 JSON 读流程定义：{@code Json.read} 读出来的 Map、List 树 → {@link ProcessDefinition}。只查形状（该是对象的是对象、该有的键有），
 * 内容对不对交给校验（{@code ProcessValidator}）；类型不认识的节点读成 {@link ProcessNode.Custom}，由校验报出来。
 * 形状不对时报 IllegalArgumentException，消息里带路径（{@code $.nodes[2].outcomes[0].key: must be a string}）。
 * <pre>{@code
 * ProcessDefinition definition = Definitions.parse(Json.read(text));
 * }</pre>
 */
public final class Definitions {
    /** 节点上共同的键；自定义节点的 attributes 不含它们 */
    private static final Set<String> COMMON_KEYS =
        Set.of("id", "type", "name", "lane", "description", "meta", "offline", "emptyAssignee", "autoSkip", "notify");

    private Definitions() { }

    /** 读一段 JSON 文字 */
    public static ProcessDefinition parse(String json) {
        return parse(Json.read(json));
    }

    /** 读 JSON 树 */
    public static ProcessDefinition parse(Object tree) {
        Map<?, ?> map = object(tree, "$");
        Map<?, ?> form = optObject(map.get("form"), "$.form");
        return new ProcessDefinition(
            string(map, "key", "$"), string(map, "name", "$"), optString(map, "description", "$"), optString(map, "category", "$"),
            optInteger(map, "version", "$"),
            form == null ? null : new ProcessDefinition.FormRef(string(form, "key", "$.form"), optInteger(form, "version", "$.form")),
            list(map.get("variables"), "$.variables", Definitions::variable),
            list(map.get("summary"), "$.summary", (item, at) -> text(item, at)),
            optString(map, "title", "$"),
            list(map.get("starters"), "$.starters", Definitions::source),
            settings(map.get("settings")),
            list(map.get("lanes"), "$.lanes", Definitions::lane),
            requiredList(map, "nodes", "$", Definitions::node),
            requiredList(map, "edges", "$", Definitions::edge),
            optMap(map.get("layout"), "$.layout"));
    }

    /** 读节点上的表单权限（{@code { "default": ..., "fields": {...} }}）；null 读成 null */
    public static NodeFormAccess parseFormAccess(Object tree) {
        return formAccess(tree, "$");
    }

    /** 读连线的副作用列表 */
    public static List<ProcessEdge.Effect> parseEffects(Object tree) {
        return list(tree, "$", Definitions::effect);
    }

    // ---------- 顶层 ----------

    private static ProcessDefinition.Variable variable(Object tree, String path) {
        Map<?, ?> map = object(tree, path);
        return new ProcessDefinition.Variable(string(map, "name", path), string(map, "type", path), optString(map, "label", path), map.get("default"));
    }

    private static ProcessDefinition.Settings settings(Object tree) {
        if (tree == null) return ProcessDefinition.Settings.NONE;
        String path = "$.settings";
        Map<?, ?> map = object(tree, path);
        Map<String, FieldAccess> formView = new LinkedHashMap<>();
        Map<?, ?> view = optObject(map.get("formView"), path + ".formView");
        if (view != null) for (Map.Entry<?, ?> e : view.entrySet()) formView.put(String.valueOf(e.getKey()), access(e.getValue(), path + ".formView." + e.getKey()));
        return new ProcessDefinition.Settings(optString(map, "calendar", path), optString(map, "withdraw", path), optString(map, "emptyAssignee", path),
            optMap(map.get("autoSkip"), path + ".autoSkip"), formView, optMap(map.get("notify"), path + ".notify"));
    }

    private static Lane lane(Object tree, String path) {
        Map<?, ?> map = object(tree, path);
        Map<?, ?> inbox = optObject(map.get("inbox"), path + ".inbox");
        Lane.Inbox parsed = null;
        if (inbox != null) {
            AssigneeSource source = source(inbox, path + ".inbox");
            if (!source.isInbox()) throw new IllegalArgumentException(path + ".inbox: must be a role, a department or a group");
            parsed = new Lane.Inbox(source, list(inbox.get("manager"), path + ".inbox.manager", Definitions::source));
        }
        return new Lane(string(map, "id", path), string(map, "name", path), parsed);
    }

    // ---------- 取值、时长、处理人 ----------

    /** 直接写的值，或 {@code { "ref": "..." }} → {@link Ref} */
    static Object value(Object tree) {
        if (tree instanceof Map<?, ?> map && map.get("ref") instanceof String ref) return new Ref(ref);
        return tree;
    }

    private static Duration duration(Object tree, String path) {
        Map<?, ?> map = object(tree, path);
        for (Duration.Unit unit : Duration.Unit.values()) {
            if (map.containsKey(unit.json())) return new Duration(unit, number(map.get(unit.json()), path + "." + unit.json()));
        }
        throw new IllegalArgumentException(path + ": a duration needs workdays, days, hours or minutes");
    }

    private static Due due(Object tree, String path) {
        if (tree == null) return null;
        Map<?, ?> map = object(tree, path);
        return map.containsKey("until") ? new Due.Until(value(map.get("until"))) : new Due.After(duration(map, path));
    }

    static AssigneeSource source(Object tree, String path) {
        Map<?, ?> map = object(tree, path);
        if (map.containsKey("user")) return new AssigneeSource.User(value(map.get("user")), optInteger(map, "weight", path));
        if (map.containsKey("role")) return new AssigneeSource.Role(value(map.get("role")));
        if (map.containsKey("department")) return new AssigneeSource.Department(value(map.get("department")));
        if (map.containsKey("initiator")) return new AssigneeSource.Initiator();
        if (map.containsKey("sameAs")) return new AssigneeSource.SameAs(string(map, "sameAs", path));
        if (map.containsKey("resolver")) return new AssigneeSource.Resolver(string(map, "resolver", path), optMap(map.get("args"), path + ".args"));
        if (map.containsKey("group")) return new AssigneeSource.Group(string(map, "group", path), value(map.get("id")));
        if (map.containsKey("pick")) {
            Map<?, ?> pick = object(map.get("pick"), path + ".pick");
            return new AssigneeSource.Pick(list(pick.get("from"), path + ".pick.from", Definitions::source), optInteger(pick, "max", path + ".pick"));
        }
        throw new IllegalArgumentException(path + ": unknown assignee source " + map.keySet());
    }

    private static NodeFormAccess formAccess(Object tree, String path) {
        if (tree == null) return null;
        Map<?, ?> map = object(tree, path);
        Map<String, FieldAccess> fields = new LinkedHashMap<>();
        Map<?, ?> raw = optObject(map.get("fields"), path + ".fields");
        if (raw != null) for (Map.Entry<?, ?> e : raw.entrySet()) fields.put(String.valueOf(e.getKey()), access(e.getValue(), path + ".fields." + e.getKey()));
        Object defaultAccess = map.get("default");
        return new NodeFormAccess(defaultAccess == null ? null : access(defaultAccess, path + ".default"), fields);
    }

    private static FieldAccess access(Object tree, String path) {
        try {
            return FieldAccess.of(text(tree, path));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(path + ": " + e.getMessage(), e);
        }
    }

    // ---------- 节点 ----------

    private static ProcessNode node(Object tree, String path) {
        Map<?, ?> map = object(tree, path);
        String type = string(map, "type", path);
        ProcessNode.Info info = new ProcessNode.Info(string(map, "id", path), type, optString(map, "name", path),
            optString(map, "lane", path), optString(map, "description", path), optMap(map.get("meta"), path + ".meta"),
            Boolean.TRUE.equals(map.get("offline")), optString(map, "emptyAssignee", path),
            optMap(map.get("autoSkip"), path + ".autoSkip"), optMap(map.get("notify"), path + ".notify"));
        return switch (type) {
            case "start" -> new ProcessNode.Start(info, formAccess(map.get("form"), path + ".form"));
            case "end" -> new ProcessNode.End(info, optString(map, "result", path), Boolean.TRUE.equals(map.get("terminate")));
            case "task" -> task(info, map, path);
            case "exclusive", "parallel", "inclusive" -> new ProcessNode.Gateway(info);
            case "service" -> {
                Map<?, ?> retry = optObject(map.get("retry"), path + ".retry");
                yield new ProcessNode.Service(info, string(map, "action", path), values(optMap(map.get("args"), path + ".args")),
                    optString(map, "mode", path), retry == null ? null : optInteger(retry, "times", path + ".retry"),
                    retry == null || retry.get("backoff") == null ? null : duration(retry.get("backoff"), path + ".retry.backoff"),
                    optString(map, "onFailure", path));
            }
            case "call" -> {
                Object forEach = map.get("forEach");
                Object forEachValue = forEach == null ? null : value(forEach);
                if (forEachValue != null && !(forEachValue instanceof Ref)) throw new IllegalArgumentException(path + ".forEach: must be a ref");
                yield new ProcessNode.Call(info, string(map, "process", path), optString(map, "mode", path), (Ref) forEachValue,
                    optString(map, "as", path), values(optMap(map.get("input"), path + ".input")), strings(map.get("output"), path + ".output"));
            }
            case "timer" -> new ProcessNode.Timer(info, due(required(map, "wait", path), path + ".wait"));
            case "receive" -> new ProcessNode.Receive(info, string(map, "event", path),
                list(map.get("outcomes"), path + ".outcomes", Definitions::text), due(map.get("due"), path + ".due"),
                overdue(map.get("overdue"), path + ".overdue"), strings(map.get("output"), path + ".output"));
            case "cc" -> new ProcessNode.Cc(info, list(map.get("to"), path + ".to", Definitions::source), optString(map, "message", path));
            default -> {
                Map<String, Object> attributes = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (!COMMON_KEYS.contains(String.valueOf(e.getKey()))) attributes.put(String.valueOf(e.getKey()), e.getValue());
                }
                yield new ProcessNode.Custom(info, attributes);
            }
        };
    }

    private static ProcessNode.Task task(ProcessNode.Info info, Map<?, ?> map, String path) {
        Map<?, ?> multi = optObject(map.get("multi"), path + ".multi");
        Map<?, ?> unclaimed = optObject(map.get("unclaimed"), path + ".unclaimed");
        Map<?, ?> allow = optObject(map.get("allow"), path + ".allow");
        return new ProcessNode.Task(info,
            list(map.get("assignees"), path + ".assignees", Definitions::source),
            list(map.get("outcomes"), path + ".outcomes", Definitions::outcome),
            multi == null ? null : multi(multi, path + ".multi"),
            due(map.get("due"), path + ".due"),
            list(map.get("reminders"), path + ".reminders", Definitions::reminder),
            overdue(map.get("overdue"), path + ".overdue"),
            unclaimed == null ? null : new TaskRules.Unclaimed(duration(required(unclaimed, "after", path + ".unclaimed"), path + ".unclaimed.after"),
                string(unclaimed, "action", path + ".unclaimed")),
            optString(map, "returnTo", path),
            allow == null ? null : new TaskRules.AllowedActions(allowed(allow, "transfer"), allowed(allow, "delegate"),
                allowed(allow, "addSign"), allowed(allow, "withdraw"), allowed(allow, "remind")),
            formAccess(map.get("form"), path + ".form"));
    }

    private static boolean allowed(Map<?, ?> allow, String key) {
        return !Boolean.FALSE.equals(allow.get(key));
    }

    private static Outcome outcome(Object tree, String path) {
        Map<?, ?> map = object(tree, path);
        return new Outcome(string(map, "key", path), optString(map, "label", path), optString(map, "kind", path),
            optString(map, "comment", path), optString(map, "attachments", path), Boolean.TRUE.equals(map.get("veto")),
            list(map.get("guards"), path + ".guards", Definitions::text), Boolean.TRUE.equals(map.get("confirm")),
            optString(map, "result", path), optString(map, "target", path));
    }

    private static MultiInstance multi(Map<?, ?> map, String path) {
        Object raw = required(map, "participants", path);
        Object participants = raw instanceof List<?> ? list(raw, path + ".participants", Definitions::source) : value(raw);
        if (!(participants instanceof List<?>) && !(participants instanceof Ref)) {
            throw new IllegalArgumentException(path + ".participants: must be a list of sources or a ref");
        }
        Object ratio = map.get("ratio");
        return new MultiInstance(participants, string(map, "complete", path), ratio == null ? null : number(ratio, path + ".ratio"),
            optInteger(map, "votes", path), optString(map, "fallback", path));
    }

    private static TaskRules.Reminder reminder(Object tree, String path) {
        Map<?, ?> map = object(tree, path);
        if (map.containsKey("before")) return new TaskRules.Reminder(duration(map.get("before"), path + ".before"), null, null);
        return new TaskRules.Reminder(null, duration(required(map, "every", path), path + ".every"), optInteger(map, "times", path));
    }

    private static TaskRules.Overdue overdue(Object tree, String path) {
        if (tree == null) return null;
        Map<?, ?> map = object(tree, path);
        return new TaskRules.Overdue(string(map, "action", path), optString(map, "outcome", path));
    }

    // ---------- 连线 ----------

    private static ProcessEdge edge(Object tree, String path) {
        Map<?, ?> map = object(tree, path);
        Object when = map.get("when");
        return new ProcessEdge(string(map, "id", path), string(map, "from", path), string(map, "to", path), optString(map, "outcome", path),
            when == null ? null : conditionAt(when, path + ".when"), Boolean.TRUE.equals(map.get("default")), optString(map, "kind", path),
            list(map.get("effects"), path + ".effects", Definitions::effect), optString(map, "label", path));
    }

    private static org.dpdns.zerodep.flow.model.Condition conditionAt(Object tree, String path) {
        try {
            return Conditions.parse(tree);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(path + e.getMessage().substring(1), e);
        }
    }

    private static ProcessEdge.Effect effect(Object tree, String path) {
        Map<?, ?> map = object(tree, path);
        if (map.containsKey("set")) return new ProcessEdge.Effect.Set(string(map, "set", path), value(map.get("value")));
        if (map.containsKey("increment")) {
            Object by = map.get("by");
            return new ProcessEdge.Effect.Increment(string(map, "increment", path), by == null ? BigDecimal.ONE : number(by, path + ".by"));
        }
        throw new IllegalArgumentException(path + ": an effect needs set or increment");
    }

    // ---------- JSON 树的读法 ----------

    private static Map<?, ?> object(Object tree, String path) {
        if (tree instanceof Map<?, ?> map) return map;
        throw new IllegalArgumentException(path + ": must be an object");
    }

    private static Map<?, ?> optObject(Object tree, String path) {
        return tree == null ? null : object(tree, path);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> optMap(Object tree, String path) {
        return tree == null ? Map.of() : (Map<String, Object>) object(tree, path);
    }

    /** 每个值读成直接写的值或 {@link Ref} */
    private static Map<String, Object> values(Map<String, Object> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        map.forEach((k, v) -> out.put(k, value(v)));
        return out;
    }

    private static Map<String, String> strings(Object tree, String path) {
        Map<String, String> out = new LinkedHashMap<>();
        if (tree != null) for (Map.Entry<?, ?> e : object(tree, path).entrySet()) out.put(String.valueOf(e.getKey()), text(e.getValue(), path + "." + e.getKey()));
        return out;
    }

    private static Object required(Map<?, ?> map, String key, String path) {
        Object value = map.get(key);
        if (value == null) throw new IllegalArgumentException(path + "." + key + ": is required");
        return value;
    }

    private static String text(Object value, String path) {
        if (value instanceof String s) return s;
        throw new IllegalArgumentException(path + ": must be a string");
    }

    private static String string(Map<?, ?> map, String key, String path) {
        return text(required(map, key, path), path + "." + key);
    }

    private static String optString(Map<?, ?> map, String key, String path) {
        Object value = map.get(key);
        return value == null ? null : text(value, path + "." + key);
    }

    private static BigDecimal number(Object value, String path) {
        if (value instanceof BigDecimal d) return d;
        if (value instanceof Long l) return BigDecimal.valueOf(l);
        throw new IllegalArgumentException(path + ": must be a number");
    }

    private static Integer optInteger(Map<?, ?> map, String key, String path) {
        Object value = map.get(key);
        if (value == null) return null;
        try {
            return number(value, path + "." + key).intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(path + "." + key + ": must be an integer", e);
        }
    }

    private static <T> List<T> list(Object tree, String path, BiFunction<Object, String, T> item) {
        if (tree == null) return List.of();
        if (!(tree instanceof List<?> raw)) throw new IllegalArgumentException(path + ": must be an array");
        List<T> out = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) out.add(item.apply(raw.get(i), path + "[" + i + "]"));
        return out;
    }

    private static <T> List<T> requiredList(Map<?, ?> map, String key, String path, BiFunction<Object, String, T> item) {
        return list(required(map, key, path), path + "." + key, item);
    }
}
