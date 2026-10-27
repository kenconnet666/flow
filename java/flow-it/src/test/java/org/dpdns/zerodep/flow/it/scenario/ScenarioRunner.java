package org.dpdns.zerodep.flow.it.scenario;

import lombok.Data;
import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.command.AddSignersCommand;
import org.dpdns.zerodep.flow.core.command.AssignCommand;
import org.dpdns.zerodep.flow.core.command.CcCommand;
import org.dpdns.zerodep.flow.core.command.ClaimCommand;
import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.command.CommentCommand;
import org.dpdns.zerodep.flow.core.command.CompleteCommand;
import org.dpdns.zerodep.flow.core.command.DelegateCommand;
import org.dpdns.zerodep.flow.core.command.FormPublication;
import org.dpdns.zerodep.flow.core.command.JumpCommand;
import org.dpdns.zerodep.flow.core.command.ReadCcCommand;
import org.dpdns.zerodep.flow.core.command.ReassignCommand;
import org.dpdns.zerodep.flow.core.command.ReleaseCommand;
import org.dpdns.zerodep.flow.core.command.RemindCommand;
import org.dpdns.zerodep.flow.core.command.RemoveSignersCommand;
import org.dpdns.zerodep.flow.core.command.ResumeCommand;
import org.dpdns.zerodep.flow.core.command.RetryJobCommand;
import org.dpdns.zerodep.flow.core.command.SaveDraftCommand;
import org.dpdns.zerodep.flow.core.command.SignalCommand;
import org.dpdns.zerodep.flow.core.command.SkipJobCommand;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.command.SuspendCommand;
import org.dpdns.zerodep.flow.core.command.TerminateCommand;
import org.dpdns.zerodep.flow.core.command.TransferCommand;
import org.dpdns.zerodep.flow.core.command.WithdrawCommand;
import org.dpdns.zerodep.flow.core.command.WithdrawStepCommand;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.Variables;
import org.dpdns.zerodep.flow.core.engine.query.Queries.InstanceSearch;
import org.dpdns.zerodep.flow.core.view.Page;
import org.dpdns.zerodep.flow.it.scenario.Scenario.Step;
import org.dpdns.zerodep.flow.it.support.LiveMysql;
import org.dpdns.zerodep.flow.it.support.TestExtensions;
import org.dpdns.zerodep.flow.it.support.TestOrg;
import org.dpdns.zerodep.flow.it.support.TestUser;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.dpdns.zerodep.wire.Json;
import org.dpdns.zerodep.wire.Wire;

import java.time.Instant;
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
class ScenarioRunner {
    /** 发布表单与流程的人 */
    private static final TestUser ADMIN = new TestUser(1000L, "管理员", Set.of(), Set.of());
    /** 引用：$名字.路径 或 $.路径（上一步） */
    private static final Pattern REFERENCE = Pattern.compile("^\\$([\\w-]*)\\.(.+)$");
    private static final Pattern SEGMENT = Pattern.compile("^(\\w+)(?:\\[(\\d+)])?$");

    private final Scenario scenario;
    private final FlowEngine<TestUser> engine;
    private final MovableClock clock;
    private final Map<String, Map<String, Object>> named = new HashMap<>();
    private Map<String, Object> previous = Map.of();

    ScenarioRunner(Scenario scenario) {
        this.scenario = scenario;
        LiveMysql.recreateTables();
        this.clock = new MovableClock(scenario.getClock());
        FlowEngine.Builder<TestUser> builder = FlowEngine.<TestUser>builder(LiveMysql.easyQuery())
            .orgDirectory(new TestOrg(scenario.getActors().values()))
            .clock(clock);
        this.engine = new TestExtensions().register(builder).build();
    }

    /** 发布，再逐步执行；哪一步不符就报 AssertionError，消息里带步骤的序号 */
    void run() {
        for (Scenario.FormFile form : scenario.getForms()) {
            engine.publishForm(ADMIN, new FormPublication(form.getKey(), form.getName(), null, form.getManifest(), null, null, null, null, null, null));
        }
        for (String process : scenario.getProcesses()) {
            try {
                engine.publishDefinition(ADMIN, process, null);
            } catch (FlowException e) {
                throw new AssertionError("publishing a process failed: " + e.errors(), e);   // 定义写错了：列出问题代号与位置
            }
        }
        List<Step> steps = scenario.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            String where = "step " + (i + 1) + " (" + step.getAs() + " " + step.getCommand() + ")";
            Map<String, Object> result = execute(step, where);
            check(step, result, where);
            if (step.getId() != null) named.put(step.getId(), result);
            previous = result;
        }
    }

    /** 执行一步：成功时是结果，出了带码的错时是 {@code { error: … }} */
    private Map<String, Object> execute(Step step, String where) {
        // 拨时钟、做到期的作业：引擎自己做的事，没有操作人
        if ("runJobs".equals(step.getCommand())) {
            clock.moveTo(Instant.parse((String) step.getArgs().get("at")));
            return Map.of("ran", engine.runDueJobs());
        }
        TestUser actor = scenario.getActors().get(step.getAs());
        if (actor == null) throw new IllegalArgumentException(where + ": unknown actor " + step.getAs());
        Arguments args = new Arguments(resolve(step.getArgs()));
        try {
            return switch (step.getCommand()) {
                case "start" -> commandResult(engine.start(actor, new StartCommand(args.text("key"), FormValues.of(args.map("form")),
                    Variables.of(args.map("variables")), args.text("businessKey"), args.text("title"), args.number("priority", 0), args.picks())));
                case "claim" -> commandResult(engine.claim(actor, new ClaimCommand(args.id("workItem"))));
                case "complete" -> commandResult(engine.complete(actor, new CompleteCommand(args.id("workItem"), args.text("outcome"),
                    args.text("comment"), FormValues.of(args.map("form")), args.picks(), args.text("returnTarget"))));
                case "saveDraft" -> commandResult(engine.saveDraft(actor, new SaveDraftCommand(args.id("workItem"), FormValues.of(args.map("form")), args.text("comment"))));
                case "withdraw" -> commandResult(engine.withdraw(actor, new WithdrawCommand(args.id("instance"), args.text("reason"))));
                case "startable" -> wire(Map.of("processes", engine.startable(actor)));
                case "startView" -> view(engine.startView(actor, args.text("key")));
                case "todo" -> page(engine.todo(actor, args.number("page", 1), args.number("size", 20)));
                case "workItem" -> view(engine.workItem(actor, args.id("workItem")));
                case "done" -> page(engine.done(actor, args.number("page", 1), args.number("size", 20)));
                case "instances" -> page(engine.instances(actor, new InstanceSearch(args.text("status"), args.text("processKey"),
                    args.text("keyword"), args.id("startedBy")), args.number("page", 1), args.number("size", 20)));
                case "validateDefinition" -> wire(Map.of("problems", engine.validateDefinition(actor, Json.write(args.raw("definition")))));
                case "disableProcess" -> view(engine.setProcessActive(actor, args.text("key"), false));
                case "enableProcess" -> view(engine.setProcessActive(actor, args.text("key"), true));
                case "versions" -> wire(Map.of("versions", engine.versions(actor, args.text("key"))));
                case "forms" -> wire(Map.of("forms", engine.forms(actor)));
                case "formVersions" -> wire(Map.of("versions", engine.formVersions(actor, args.text("key"))));
                case "formDocument" -> view(engine.formDocument(actor, args.text("key"), args.has("version") ? args.number("version", 0) : null));
                case "disableForm" -> view(engine.setFormActive(actor, args.text("key"), false));
                case "enableForm" -> view(engine.setFormActive(actor, args.text("key"), true));
                case "publishForm" -> {
                    var form = engine.publishForm(actor, new FormPublication(args.text("key"), args.text("name"), args.text("category"),
                        Json.write(args.raw("manifest")), args.text("clientSource"), null, null, null, null, args.text("note")));
                    yield wire(Map.of("id", form.getId(), "key", form.getFormKey(), "version", form.getVersion()));   // 同 HTTP 接口的响应
                }
                case "processes" -> wire(Map.of("processes", engine.processes(actor)));
                case "incidents" -> page(engine.incidents(actor, args.id("instance"), args.number("page", 1), args.number("size", 20)));
                case "started" -> page(engine.started(actor, args.number("page", 1), args.number("size", 20)));
                case "instance" -> view(engine.instance(actor, args.id("instance")));
                case "transfer" -> commandResult(engine.transfer(actor, new TransferCommand(args.id("workItem"), args.id("user"), args.text("reason"))));
                case "delegate" -> commandResult(engine.delegate(actor, new DelegateCommand(args.id("workItem"), args.id("user"), args.text("reason"))));
                case "addSigners" -> commandResult(engine.addSigners(actor,
                    new AddSignersCommand(args.id("workItem"), args.ids("users"), args.text("mode"), args.text("reason"))));
                case "removeSigners" -> commandResult(engine.removeSigners(actor, new RemoveSignersCommand(args.id("workItem"), args.ids("users"))));
                case "release" -> commandResult(engine.release(actor, new ReleaseCommand(args.id("workItem"), args.text("reason"))));
                case "remind" -> commandResult(engine.remind(actor, new RemindCommand(args.id("instance"), args.id("workItem"), args.text("message"))));
                case "comment" -> commandResult(engine.comment(actor, new CommentCommand(args.id("instance"), args.text("text"))));
                case "signal" -> Map.of("results", engine.signal(actor, new SignalCommand(args.id("instance"), args.text("businessKey"),
                    args.text("event"), args.text("outcome"), args.map("data"))).stream().map(ScenarioRunner::commandResult).toList());
                case "withdrawStep" -> commandResult(engine.withdrawStep(actor, new WithdrawStepCommand(args.id("workItem"), args.text("reason"))));
                case "assign" -> commandResult(engine.assign(actor, new AssignCommand(args.id("workItem"), args.id("user"))));
                case "designations" -> page(engine.designations(actor, args.number("page", 1), args.number("size", 20)));
                case "retryJob" -> Map.of("revived", engine.retryJob(actor, new RetryJobCommand(args.id("job"))));
                case "skipJob" -> commandResult(engine.skipJob(actor, new SkipJobCommand(args.id("job"), args.text("outcome"), args.text("reason"))));
                case "cc" -> commandResult(engine.cc(actor, new CcCommand(args.id("instance"), args.ids("users"), args.text("message"))));
                case "readCc" -> Map.of("read", engine.readCc(actor, new ReadCcCommand(args.id("instance"))));
                case "ccList" -> page(engine.ccList(actor, args.number("page", 1), args.number("size", 20)));
                case "terminate" -> commandResult(engine.terminate(actor, new TerminateCommand(args.id("instance"), args.text("reason"))));
                case "suspend" -> commandResult(engine.suspend(actor, new SuspendCommand(args.id("instance"), args.text("reason"))));
                case "resume" -> commandResult(engine.resume(actor, new ResumeCommand(args.id("instance"))));
                case "jump" -> commandResult(engine.jump(actor, new JumpCommand(args.id("instance"), args.text("node"), args.text("reason"))));
                case "reassign" -> commandResult(engine.reassign(actor, new ReassignCommand(args.id("workItem"), args.id("user"), args.text("reason"))));
                case "publishDefinition" -> {
                    engine.publishDefinition(actor, Json.write(args.raw("definition")), null);
                    yield Map.of();
                }
                default -> throw new IllegalArgumentException(where + ": unknown command " + step.getCommand());
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
        boolean expectsError = step.getExpect().containsKey("error");
        if (result.containsKey("error") && !expectsError) throw new AssertionError(where + ": unexpected " + result.get("error"));
        if (!result.containsKey("error") && expectsError) throw new AssertionError(where + ": expected " + step.getExpect().get("error") + " but it succeeded");
        Expectation.check(where, step.getExpect(), result);
    }

    // ───────── 结果写成线上的 JSON 形状 ─────────

    private static Map<String, Object> commandResult(CommandResult r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("instance", r.getInstance());
        out.put("workItems", r.getCreated());
        out.put("closed", r.getClosed());
        out.put("events", r.getEvents());
        return wire(out);
    }

    /** 一页列表：{ rows, total, page, size, totalPages }，同 HTTP 接口 */
    private static Map<String, Object> page(Page<?> page) { return view(page); }

    /** 查询结果本身（发起页、待办详情、实例详情）：记录按属性名写成对象 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> view(Object value) {
        Map<String, Object> tree = new LinkedHashMap<>((Map<String, Object>) Wire.encode(value));
        tree.remove(Wire.TYPES);
        return tree;
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
        if (value instanceof Map<?, ?> object) return (String) object.get("id");
        return value instanceof Number number ? String.valueOf(number) : (String) value;   // 流转记录 data 里的编号是数
    }

    /** 一步的参数，按名字取 */
    @Data
    private static class Arguments {
        private final Map<String, Object> values;

        String text(String name) { return (String) values.get(name); }

        /** 编号：引用解出来的是线上的文字，直接写的是数字（用户编号） */
        Long id(String name) { return toId(values.get(name)); }

        /** 下一步选的处理人：{ 节点: [用户编号…] } */
        Map<String, List<Long>> picks() {
            Map<String, List<Long>> out = new LinkedHashMap<>();
            map("picks").forEach((node, users) -> out.put(node, ((List<?>) users).stream().map(Arguments::toId).toList()));
            return out;
        }

        /** 一串编号 */
        List<Long> ids(String name) {
            Object value = values.get(name);
            return value == null ? List.of() : ((List<?>) value).stream().map(Arguments::toId).toList();
        }

        private static Long toId(Object value) {
            if (value == null) return null;
            return value instanceof Number n ? n.longValue() : Long.valueOf((String) value);
        }

        int number(String name, int fallback) {
            Object value = values.get(name);
            return value == null ? fallback : ((Number) value).intValue();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> map(String name) { return values.get(name) == null ? Map.of() : (Map<String, Object>) values.get(name); }

        Object raw(String name) { return values.get(name); }

        boolean has(String name) { return values.get(name) != null; }
    }
}
