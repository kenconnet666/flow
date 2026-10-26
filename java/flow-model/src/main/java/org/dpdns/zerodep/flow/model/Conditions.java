package org.dpdns.zerodep.flow.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 条件树的读取与求值，与 TS 模型包的 {@code condition.ts} 同一套规则（spec/conditions 的样例两边都跑）。
 * <pre>{@code
 * Condition c = Conditions.parse(Json.read("{\"all\":[{\"field\":\"form.amount\",\"op\":\"gt\",\"value\":\"10000\"}]}"));
 * boolean pass = Conditions.evaluate(c, scope::read);
 * }</pre>
 * 字段的值是数组时（多选、人员字段的编号列表），比较对数组里的每一项做：等于、是其中之一、包含、大小这些是"有一项满足"，
 * 不等于、不在其中、不包含是"没有一项满足"；有值、没有值看数组是不是空的。
 */
public final class Conditions {
    private Conditions() { }

    /** 从 JSON 树（{@code Json.read} 读出来的 Map、List）读一个条件；形状不对时报 IllegalArgumentException，消息里带路径 */
    public static Condition parse(Object tree) {
        return parse(tree, "$");
    }

    private static Condition parse(Object tree, String path) {
        if (!(tree instanceof Map<?, ?> map)) throw new IllegalArgumentException(path + ": a condition must be an object");
        if (map.containsKey("all")) return new Condition.All(items(map.get("all"), path + ".all"));
        if (map.containsKey("any")) return new Condition.Any(items(map.get("any"), path + ".any"));
        if (map.containsKey("not")) return new Condition.Not(parse(map.get("not"), path + ".not"));
        if (!(map.get("field") instanceof String field)) throw new IllegalArgumentException(path + ".field: must be a string");
        if (!(map.get("op") instanceof String op)) throw new IllegalArgumentException(path + ".op: must be a string");
        try {
            return new Condition.Rule(field, ConditionOp.of(op), map.get("value"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(path + ".op: " + e.getMessage(), e);
        }
    }

    private static List<Condition> items(Object value, String path) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(path + ": must be an array");
        List<Condition> items = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) items.add(parse(list.get(i), path + "[" + i + "]"));
        return items;
    }

    /** 一条比较已经写完整：要值的有值，{@code between} 至少一端有值 */
    public static boolean isRuleComplete(Condition.Rule rule) {
        if (!rule.op().needsValue()) return true;
        if (rule.op() == ConditionOp.BETWEEN) {
            return rule.value() instanceof List<?> ends && ends.stream().anyMatch(end -> !Values.isBlank(end));
        }
        return !Values.isBlank(rule.value());
    }

    /** 整棵树都写完整：没有没写完的比较、没有空的组（流程里发布时要求这样，0005 的 6.2） */
    public static boolean isComplete(Condition condition) {
        if (condition instanceof Condition.Not not) return isComplete(not.item());
        if (condition instanceof Condition.All all) return !all.items().isEmpty() && all.items().stream().allMatch(Conditions::isComplete);
        if (condition instanceof Condition.Any any) return !any.items().isEmpty() && any.items().stream().allMatch(Conditions::isComplete);
        return isRuleComplete((Condition.Rule) condition);
    }

    /** 条件里用到的字段名，按出现的先后、不重复 */
    public static Set<String> fields(Condition condition) {
        Set<String> fields = new LinkedHashSet<>();
        collect(condition, fields);
        return fields;
    }

    private static void collect(Condition condition, Set<String> fields) {
        if (condition instanceof Condition.Not not) collect(not.item(), fields);
        else if (condition instanceof Condition.All all) all.items().forEach(item -> collect(item, fields));
        else if (condition instanceof Condition.Any any) any.items().forEach(item -> collect(item, fields));
        else fields.add(((Condition.Rule) condition).field());
    }

    /**
     * 条件成不成立。没写完的比较、空的组不加条件（同组件库与 query 的 conditionBy），整棵树都不加条件时成立；
     * 流程定义在发布时就拒绝这样的条件，所以这条规则只在筛选这类场合起作用。
     */
    public static boolean evaluate(Condition condition, Function<String, Object> read) {
        Boolean result = evaluateOrSkip(condition, read);
        return result == null || result;
    }

    /** 求值；不加条件时为 null */
    private static Boolean evaluateOrSkip(Condition condition, Function<String, Object> read) {
        if (condition instanceof Condition.Not not) {
            Boolean inner = evaluateOrSkip(not.item(), read);
            return inner == null ? null : !inner;
        }
        if (condition instanceof Condition.All || condition instanceof Condition.Any) {
            List<Condition> items = condition instanceof Condition.All all ? all.items() : ((Condition.Any) condition).items();
            boolean any = false;
            boolean all = true;
            boolean counted = false;
            for (Condition item : items) {
                Boolean result = evaluateOrSkip(item, read);
                if (result == null) continue;
                counted = true;
                any |= result;
                all &= result;
            }
            if (!counted) return null;
            return condition instanceof Condition.All ? all : any;
        }
        Condition.Rule rule = (Condition.Rule) condition;
        if (!isRuleComplete(rule)) return null;
        return matchesRule(rule, read.apply(rule.field()));
    }

    private static boolean matchesRule(Condition.Rule rule, Object actual) {
        ConditionOp op = rule.op();
        if (op == ConditionOp.EXISTS || op == ConditionOp.EMPTY || !(actual instanceof List<?> list)) {
            return matchesOne(op, actual, rule.value());
        }
        return op.negative()
            ? list.stream().allMatch(item -> matchesOne(op, item, rule.value()))
            : list.stream().anyMatch(item -> matchesOne(op, item, rule.value()));
    }

    private static String lower(Object value) {
        return Values.toText(value).toLowerCase(Locale.ROOT);
    }

    /** 对一个（不是数组的）值，这条比较成不成立 */
    private static boolean matchesOne(ConditionOp op, Object actual, Object value) {
        return switch (op) {
            case EQ -> Values.same(actual, value);
            case NE -> !Values.same(actual, value);
            case GT -> actual != null && Values.compare(actual, value) > 0;
            case GTE -> actual != null && Values.compare(actual, value) >= 0;
            case LT -> actual != null && Values.compare(actual, value) < 0;
            case LTE -> actual != null && Values.compare(actual, value) <= 0;
            case BETWEEN -> {
                List<?> ends = value instanceof List<?> l ? l : List.of();
                Object low = ends.size() > 0 ? ends.get(0) : null;
                Object high = ends.size() > 1 ? ends.get(1) : null;
                yield actual != null
                    && (Values.isBlank(low) || Values.compare(actual, low) >= 0)
                    && (Values.isBlank(high) || Values.compare(actual, high) <= 0);
            }
            case IN -> value instanceof List<?> l && l.stream().anyMatch(item -> Values.same(actual, item));
            case NOT_IN -> !(value instanceof List<?> l) || l.stream().noneMatch(item -> Values.same(actual, item));
            case CONTAINS -> lower(actual).contains(lower(value));
            case NOT_CONTAINS -> !lower(actual).contains(lower(value));
            case STARTS_WITH -> lower(actual).startsWith(lower(value));
            case EXISTS -> !Values.isBlank(actual);
            case EMPTY -> Values.isBlank(actual);
        };
    }
}
