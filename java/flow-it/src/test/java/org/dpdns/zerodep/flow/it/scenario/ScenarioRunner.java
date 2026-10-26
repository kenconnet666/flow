package org.dpdns.zerodep.flow.it.scenario;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.command.ClaimCommand;
import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.command.CompleteCommand;
import org.dpdns.zerodep.flow.core.command.FormPublication;
import org.dpdns.zerodep.flow.core.command.InstanceDetail;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.command.WithdrawCommand;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.Variables;
import org.dpdns.zerodep.flow.it.scenario.Scenario.Step;
import org.dpdns.zerodep.flow.it.support.LiveMysql;
import org.dpdns.zerodep.flow.it.support.TestOrg;
import org.dpdns.zerodep.flow.it.support.TestUser;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.dpdns.zerodep.flow.model.runtime.TodoItem;
import org.dpdns.zerodep.query.exec.Page;
import org.dpdns.zerodep.wire.Json;
import org.dpdns.zerodep.wire.Wire;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 跑一个场景：清库建表（自增的编号从 1 开始，两个后端跑出来的编号相同），组装一个固定时钟的引擎，发布表单与流程，再逐步执行命令、比期望。
 * 每一步的结果写成线上的 JSON 形状（{@link Wire#encode}：编号、小数、时间都是文字），TS 一侧写成同样的形状，所以两边比的是同一份期望。
 */
final class ScenarioRunner {
    /** 发布表单与流程的人 */
    private static final TestUser ADMIN = new TestUser(1000L, "管理员", Set.of(), Set.of());
    /** 引用：$名字.路径 或 $.路径（上一步） */
    private static final Pattern REFERENCE = Pattern.compile("^\\$([\\w-]*)\\.(.+)$");
    private static final Pattern SEGMENT = Pattern.compile("^(\\w+)(?:\\[(\\d+)])?$");

    private final Scenario scenario;
    private final FlowEngine<TestUser> engine;
    private final Map<String, Map<String, Object>> named = new HashMap<>();
    private Map<String, Object> previous = Map.of();

    ScenarioRunner(Scenario scenario) {
        this.scenario = scenario;
        LiveMysql.recreateTables();
        Map<Long, String> names = new HashMap<>();
        scenario.actors().values().forEach(user -> names.put(user.id(), user.name()));
        this.engine = FlowEngine.<TestUser>builder(LiveMysql.dataSource())
            .orgDirectory(new TestOrg(names))
            .clock(Clock.fixed(scenario.clock(), ZoneOffset.UTC))
            .build();
    }

    /** 发布，再逐步执行；哪一步不符就报 AssertionError，消息里带步骤的序号 */
    void run() {
        for (Scenario.FormFile form : scenario.forms()) {
            engine.publishForm(ADMIN, new FormPublication(form.key(), form.name(), null, form.manifest(), null, null, null, null, null, null));
        }
        for (String process : scenario.processes()) engine.publishDefinition(ADMIN, process, null);
        List<Step> steps = scenario.steps();
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            String where = "step " + (i + 1) + " (" + step.as() + " " + step.command() + ")";
            Map<String, Object> result = execute(step, where);
            check(step, result, where);
            if (step.id() != null) named.put(step.id(), result);
            previous = result;
        }
    }

    /** 执行一步：成功时是结果，出了带码的错时是 {@code { error: … }} */
    private Map<String, Object> execute(Step step, String where) {
        TestUser actor = scenario.actors().get(step.as());
        if (actor == null) throw new IllegalArgumentException(where + ": unknown actor " + step.as());
        Arguments args = new Arguments(resolve(step.args()));
        try {
            return switch (step.command()) {
                case "start" -> commandResult(engine.start(actor, new StartCommand(args.text("key"), FormValues.of(args.map("form")),
                    Variables.of(args.map("variables")), args.text("businessKey"), args.text("title"), args.number("priority", 0))));
                case "claim" -> commandResult(engine.claim(actor, new ClaimCommand(args.id("workItem"))));
                case "complete" -> commandResult(engine.complete(actor, new CompleteCommand(args.id("workItem"), args.text("outcome"),
                    args.text("comment"), FormValues.of(args.map("form")))));
                case "withdraw" -> commandResult(engine.withdraw(actor, new WithdrawCommand(args.id("instance"), args.text("reason"))));
                case "todo" -> page(engine.todo(actor, args.number("page", 1), args.number("size", 20)));
                case "instance" -> detail(engine.instance(actor, args.id("instance")));
                case "publishDefinition" -> {
                    engine.publishDefinition(actor, Json.write(args.raw("definition")), null);
                    yield Map.of();
                }
                default -> throw new IllegalArgumentException(where + ": unknown command " + step.command());
            };
        } catch (FlowException e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", e.code().code());
            error.put("params", e.params());
            error.put("errors", e.errors());
            return Map.of("error", wire(error));
        }
    }

    /** 比期望：期望出错时结果必须是错；没期望出错时结果不能是错 */
    private static void check(Step step, Map<String, Object> result, String where) {
        boolean expectsError = step.expect().containsKey("error");
        if (result.containsKey("error") && !expectsError) throw new AssertionError(where + ": unexpected " + result.get("error"));
        if (!result.containsKey("error") && expectsError) throw new AssertionError(where + ": expected " + step.expect().get("error") + " but it succeeded");
        Expectation.check(where, step.expect(), result);
    }

    // ───────── 结果写成线上的 JSON 形状 ─────────

    private static Map<String, Object> commandResult(CommandResult r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("instance", r.instance());
        out.put("workItems", r.created());
        out.put("closed", r.closed());
        out.put("events", r.events());
        return wire(out);
    }

    /** 一页列表：同 query 的表格接口（{@link Page#toWire}），total 是数字 */
    private static Map<String, Object> page(Page<TodoItem> page) {
        Map<String, Object> tree = new LinkedHashMap<>(page.toWire());
        tree.remove(Wire.TYPES);
        return tree;
    }

    private static Map<String, Object> detail(InstanceDetail d) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("instance", d.instance());
        out.put("form", d.form().asMap());
        out.put("workItems", d.workItems());
        out.put("timeline", d.timeline());
        return wire(out);
    }

    /** 记录、时间、小数写成 JSON 树（同 HTTP 接口发给前端的），去掉根上给前端还原用的 $types */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> wire(Map<String, Object> value) {
        Map<String, Object> tree = new LinkedHashMap<>((Map<String, Object>) Wire.encode(value));
        tree.remove(Wire.TYPES);
        return tree;
    }

    // ───────── 引用 ─────────

    /** 参数里的引用换成前面步骤的结果里的编号 */
    private Map<String, Object> resolve(Map<String, Object> args) {
        Map<String, Object> out = new LinkedHashMap<>();
        args.forEach((key, value) -> out.put(key, value instanceof String text && text.startsWith("$") ? reference(text) : value));
        return out;
    }

    private String reference(String text) {
        Matcher m = REFERENCE.matcher(text);
        if (!m.matches()) throw new IllegalArgumentException("bad reference " + text);
        Object value = m.group(1).isEmpty() ? previous : named.get(m.group(1));
        if (value == null) throw new IllegalArgumentException("reference " + text + " names no earlier step");
        for (String segment : m.group(2).split("\\.")) {
            Matcher s = SEGMENT.matcher(segment);
            if (!s.matches()) throw new IllegalArgumentException("bad reference " + text);
            value = ((Map<?, ?>) value).get(s.group(1));
            if (s.group(2) != null) value = ((List<?>) value).get(Integer.parseInt(s.group(2)));
        }
        // 引用到一个对象（待办、实例）时取它的编号
        return value instanceof Map<?, ?> object ? (String) object.get("id") : (String) value;
    }

    /** 一步的参数，按名字取 */
    private record Arguments(Map<String, Object> values) {
        String text(String name) { return (String) values.get(name); }

        /** 编号：引用解出来的是线上的文字 */
        Long id(String name) { return values.get(name) == null ? null : Long.valueOf((String) values.get(name)); }

        int number(String name, int fallback) {
            Object value = values.get(name);
            return value == null ? fallback : ((Number) value).intValue();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> map(String name) { return values.get(name) == null ? Map.of() : (Map<String, Object>) values.get(name); }

        Object raw(String name) { return values.get(name); }
    }
}
