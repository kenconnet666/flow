package org.dpdns.zerodep.flow.testkit;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.command.ClaimCommand;
import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.command.CompleteCommand;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.engine.store.Archives;
import org.dpdns.zerodep.flow.core.entity.CalendarDayEntity;
import org.dpdns.zerodep.flow.core.entity.CalendarEntity;
import org.dpdns.zerodep.flow.core.entity.CandidateEntity;
import org.dpdns.zerodep.flow.core.entity.CcEntity;
import org.dpdns.zerodep.flow.core.entity.DraftEntity;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.entity.FormDataEntity;
import org.dpdns.zerodep.flow.core.entity.FormEntity;
import org.dpdns.zerodep.flow.core.entity.FormVersionEntity;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.entity.JobEntity;
import org.dpdns.zerodep.flow.core.entity.ProcessEntity;
import org.dpdns.zerodep.flow.core.entity.ProcessVersionEntity;
import org.dpdns.zerodep.flow.core.entity.RequestEntity;
import org.dpdns.zerodep.flow.core.entity.StepEntity;
import org.dpdns.zerodep.flow.core.entity.WaitEntity;
import org.dpdns.zerodep.flow.core.entity.WorkItemEntity;
import org.dpdns.zerodep.flow.core.extension.Choice;
import org.dpdns.zerodep.flow.core.extension.ExtensionParam;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.identity.OrgDirectory;
import org.dpdns.zerodep.flow.core.view.definition.ExtensionCatalog;
import org.dpdns.zerodep.flow.core.view.workitem.TodoItem;
import org.dpdns.zerodep.flow.core.view.workitem.WorkItemVO;
import org.dpdns.zerodep.flow.model.error.FlowException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 契约核对：接入方的部署与实现是不是引擎要的样子。每一项一个名字、一段检查，不过时抛 {@link AssertionError}，消息说哪里不对、怎么改。
 * 跑在测试库上——冒烟那一项会发布一个小流程、走完一单（跑完把流程停用）。
 */
public class FlowContractChecks {
    /** 参数、业务信息字段能写的类型（同表单字段清单的名字） */
    private static final Set<String> PARAM_TYPES = Set.of("text", "number", "decimal", "boolean", "date", "select");

    private FlowContractChecks() { }

    /** 一项核对 */
    @Getter
    @RequiredArgsConstructor
    public static class Check {
        private final String name;
        private final Runnable run;
    }

    /** 全部核对，按先后：表 → 组织架构 → 扩展的声明 → 冒烟 */
    public static <U extends Actor> List<Check> of(FlowContract<U> contract) {
        List<Check> checks = new ArrayList<>();
        checks.add(new Check("引擎的表都在、列对得上", () -> tables(contract.getDb())));
        checks.add(new Check("组织架构：查得到人的名字", () -> names(contract)));
        checks.add(new Check("组织架构：角色的成员两边对得上", () -> members(contract)));
        checks.add(new Check("组织架构：有流程管理员", () -> administrators(contract.getOrg())));
        checks.add(new Check("扩展的声明写得对", () -> extensions(contract.getEngine().extensions(contract.getAdmin()))));
        checks.add(new Check("冒烟：用真实的组织架构走一单", () -> smoke(contract)));
        return checks;
    }

    // ───────── 表 ─────────

    /** 每张表读一行：表不在、少了列（建表脚本没执行到最新）时读不了 */
    private static void tables(EasyEntityQuery db) {
        Map<String, Runnable> reads = new LinkedHashMap<>();
        reads.put("flow_process", () -> db.queryable(ProcessEntity.class).limit(1).toList());
        reads.put("flow_definition", () -> db.queryable(ProcessVersionEntity.class).limit(1).toList());
        reads.put("flow_form", () -> db.queryable(FormEntity.class).limit(1).toList());
        reads.put("flow_form_version", () -> db.queryable(FormVersionEntity.class).limit(1).toList());
        reads.put("flow_form_data", () -> db.queryable(FormDataEntity.class).limit(1).toList());
        reads.put("flow_instance", () -> db.queryable(InstanceEntity.class).limit(1).toList());
        reads.put("flow_work_item", () -> db.queryable(WorkItemEntity.class).limit(1).toList());
        reads.put("flow_candidate", () -> db.queryable(CandidateEntity.class).limit(1).toList());
        reads.put("flow_event", () -> db.queryable(EventEntity.class).limit(1).toList());
        reads.put("flow_job", () -> db.queryable(JobEntity.class).limit(1).toList());
        reads.put("flow_cc", () -> db.queryable(CcEntity.class).limit(1).toList());
        reads.put("flow_wait", () -> db.queryable(WaitEntity.class).limit(1).toList());
        reads.put("flow_request", () -> db.queryable(RequestEntity.class).limit(1).toList());
        reads.put("flow_step", () -> db.queryable(StepEntity.class).limit(1).toList());
        reads.put("flow_draft", () -> db.queryable(DraftEntity.class).limit(1).toList());
        reads.put("flow_calendar", () -> db.queryable(CalendarEntity.class).limit(1).toList());
        reads.put("flow_calendar_day", () -> db.queryable(CalendarDayEntity.class).limit(1).toList());
        reads.put("flow_instance_archive", () -> Archives.query(db, InstanceEntity.class).limit(1).toList());
        reads.put("flow_work_item_archive", () -> Archives.query(db, WorkItemEntity.class).limit(1).toList());
        reads.put("flow_event_archive", () -> Archives.query(db, EventEntity.class).limit(1).toList());
        reads.put("flow_form_data_archive", () -> Archives.query(db, FormDataEntity.class).limit(1).toList());
        reads.put("flow_cc_archive", () -> Archives.query(db, CcEntity.class).limit(1).toList());
        List<String> broken = new ArrayList<>();
        reads.forEach((table, read) -> {
            try {
                read.run();
            } catch (RuntimeException e) {
                broken.add(table + "（" + rootMessage(e) + "）");
            }
        });
        if (!broken.isEmpty()) {
            fail("这些表读不了：建表脚本可能没执行到最新（flow.flyway.enabled，或把 flow-core 里 db/flow/mysql 的脚本都执行一遍）；"
                + "报 conversion unknown 时是给核对的 db 不是引擎用的那个 easy-query。" + String.join("；", broken));
        }
    }

    // ───────── 组织架构 ─────────

    private static <U extends Actor> void names(FlowContract<U> contract) {
        for (U user : List.of(contract.getAdmin(), contract.getStarter(), contract.getMember())) {
            String name = contract.getOrg().userName(user.getId());
            if (name == null || name.isBlank() || name.equals(String.valueOf(user.getId()))) {
                fail("OrgDirectory.userName(" + user.getId() + ") 没查到名字（返回了 " + name + "）：待办、流转记录上的名字快照会是编号");
            }
        }
    }

    private static <U extends Actor> void members(FlowContract<U> contract) {
        Long role = contract.getRole();
        U member = contract.getMember();
        if (!member.getRoles().contains(role)) {
            fail("样例成员 " + member.getId() + " 的 Actor.getRoles() 里没有角色 " + role + "：角色收件箱按它匹配候选，他会看不到这个收件箱的待办");
        }
        List<Long> members;
        try {
            members = contract.getOrg().members("role", role);
        } catch (UnsupportedOperationException e) {
            fail("OrgDirectory.members 没实现：会签、抄送写成角色、部门时要它展开成人，收件箱的到达通知也按它发");
            return;
        }
        if (!members.contains(member.getId())) {
            fail("OrgDirectory.members(\"role\", " + role + ") 里没有 " + member.getId() + "，而他的 Actor.getRoles() 里有：两边的组织数据要一致");
        }
    }

    private static void administrators(OrgDirectory org) {
        if (org.administrators().isEmpty()) {
            fail("OrgDirectory.administrators() 是空的：处理人解析为空、定义写了 emptyAssignee: admin 时没人接，流程管理员也看不到流程变量");
        }
    }

    // ───────── 扩展的声明 ─────────

    private static void extensions(ExtensionCatalog catalog) {
        List<String> problems = new ArrayList<>();
        Map<String, List<ExtensionCatalog.Entry>> kinds = new LinkedHashMap<>();
        kinds.put("解析器", catalog.getResolvers());
        kinds.put("守卫", catalog.getGuards());
        kinds.put("动作", catalog.getActions());
        kinds.put("计算值", catalog.getComputed());
        kinds.put("自定义节点", catalog.getNodes());
        kinds.put("会签规则", catalog.getCompletions());
        kinds.put("字段类型", catalog.getFieldTypes());
        kinds.forEach((kind, entries) -> {
            for (ExtensionCatalog.Entry entry : entries) {
                String at = kind + " " + entry.getName();
                params(at, entry.getParams(), problems::add);
                choices(at + " 的结果", entry.getOutcomes(), problems::add);
                if (entry.getValueType() != null && !PARAM_TYPES.contains(entry.getValueType())) problems.add(at + " 的值类型 " + entry.getValueType() + " 不认识");
            }
        });
        for (ExtensionCatalog.Entry node : catalog.getNodes()) {
            if (!node.getName().startsWith("x-")) problems.add("自定义节点 " + node.getName() + " 的类型要以 x- 开头");
        }
        params("节点的业务信息（flow.designer.node-meta）", catalog.getNodeMeta(), problems::add);
        if (!problems.isEmpty()) fail("扩展的声明（info()）有问题：" + String.join("；", problems));
    }

    private static void params(String at, List<ExtensionParam> params, Consumer<String> problems) {
        Set<String> keys = new HashSet<>();
        for (ExtensionParam param : params) {
            if (param.getKey() == null || param.getKey().isBlank()) {
                problems.accept(at + " 有参数没写键");
                continue;
            }
            if (!keys.add(param.getKey())) problems.accept(at + " 的参数 " + param.getKey() + " 重复了");
            if (!PARAM_TYPES.contains(param.getType())) problems.accept(at + " 的参数 " + param.getKey() + " 的类型 " + param.getType() + " 不认识（能写 " + PARAM_TYPES + "）");
            if ("select".equals(param.getType()) && param.getOptions().isEmpty()) problems.accept(at + " 的参数 " + param.getKey() + " 是下拉，但没有选项");
            choices(at + " 的参数 " + param.getKey() + " 的选项", param.getOptions(), problems);
        }
    }

    private static void choices(String at, List<Choice> choices, Consumer<String> problems) {
        Set<String> values = new HashSet<>();
        for (Choice choice : choices) {
            if (choice.getValue() == null || choice.getValue().isBlank()) problems.accept(at + " 有一项没写值");
            else if (!values.add(choice.getValue())) problems.accept(at + " 的 " + choice.getValue() + " 重复了");
        }
    }

    // ───────── 冒烟 ─────────

    /**
     * 发布一个小流程：发起 → 角色收件箱里的"核对" → 结束。发起人发起，成员在待办里看到、认领、处理，实例走完；最后停用这个流程。
     * 哪一步不过就说明权限、组织架构、Actor 的哪一样没接好。
     */
    private static <U extends Actor> void smoke(FlowContract<U> contract) {
        FlowEngine<U> engine = contract.getEngine();
        String key = contract.getSmokeProcess();
        String definition = """
            { "key": "%s", "name": "契约核对（可以删）", "title": "契约核对",
              "nodes": [ { "id": "start", "type": "start", "name": "发起" },
                         { "id": "check", "type": "task", "name": "核对", "assignees": [{ "role": %d }], "outcomes": [{ "key": "ok", "label": "通过" }] },
                         { "id": "end", "type": "end", "name": "结束", "result": "ok" } ],
              "edges": [ { "id": "e1", "from": "start", "to": "check" }, { "id": "e2", "from": "check", "to": "end", "outcome": "ok" } ] }
            """.formatted(key, contract.getRole());
        step("管理员发布流程（PermissionPolicy 要放行 definitions.publish）", () -> engine.publishDefinition(contract.getAdmin(), definition, "契约核对"));
        // 上一次跑完停用了：发布新版本不会重新启用
        step("管理员启用流程（PermissionPolicy 要放行 definitions.status）", () -> engine.setProcessActive(contract.getAdmin(), key, true));
        try {
            CommandResult started = step("发起人发起（PermissionPolicy 要放行 start）", () -> engine.start(contract.getStarter(), StartCommand.of(key, Map.of())));
            WorkItemVO item = started.getCreated().stream().findFirst()
                .orElseThrow(() -> new AssertionError("发起后没有生成待办：角色 " + contract.getRole() + " 的收件箱没生成"));
            List<TodoItem> todo = step("成员查待办", () -> engine.todo(contract.getMember(), 1, 50).getRows());
            if (todo.stream().noneMatch(t -> t.getId().equals(item.getId()))) {
                fail("成员 " + contract.getMember().getId() + " 的待办里没有这一单：Actor.getRoles() 与角色收件箱对不上，或者 TodoQueryCustomizer 把它筛掉了");
            }
            step("成员认领", () -> engine.claim(contract.getMember(), new ClaimCommand(item.getId())));
            CommandResult done = step("成员处理", () -> engine.complete(contract.getMember(), CompleteCommand.of(item.getId(), "ok")));
            if (!"completed".equals(done.getInstance().getStatus())) fail("处理后实例没有走完，状态是 " + done.getInstance().getStatus());
        } finally {
            engine.setProcessActive(contract.getAdmin(), key, false);
        }
    }

    /** 冒烟的一步：出错时说是哪一步 */
    private static <T> T step(String what, Supplier<T> action) {
        try {
            return action.get();
        } catch (RuntimeException e) {
            throw new AssertionError("冒烟在「" + what + "」这一步失败：" + rootMessage(e), e);
        }
    }

    private static void fail(String message) { throw new AssertionError(message); }

    /** 最里层异常的话；引擎的业务错误带上参数（forbidden 的是哪个操作、什么原因） */
    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        if (root instanceof FlowException flow) return flow.getCode().code() + " " + flow.getParams();
        return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    }
}
