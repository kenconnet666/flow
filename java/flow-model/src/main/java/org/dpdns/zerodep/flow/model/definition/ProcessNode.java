package org.dpdns.zerodep.flow.model.definition;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程节点（0005 第 4、5 节）：内置的十一种，加接入方注册的自定义节点（{@code type} 以 {@code x-} 开头）。
 * 共同的字段放在 {@link Info}，各类节点的 record 只多它自己的字段。
 */
public sealed interface ProcessNode {
    /** 内置的节点类型 */
    Set<String> BUILTIN_TYPES = Set.of("start", "end", "task", "exclusive", "parallel", "inclusive", "service", "call", "timer", "receive", "cc");

    /** 按结果分出边的类型：人工、自动、调用流程、等待外部事件 */
    Set<String> BRANCHING_TYPES = Set.of("task", "service", "call", "receive");

    /**
     * 所有节点共有的字段。
     *
     * @param id            标识，流程内唯一
     * @param type          类型
     * @param name          名称
     * @param lane          所在泳道
     * @param description   说明
     * @param meta          业务附加信息，引擎原样保存不解释
     * @param offline       线下动作
     * @param emptyAssignee 覆盖流程级的"处理人为空时"
     * @param autoSkip      覆盖流程级的"自动跳过"（JSON 原样）
     * @param notifications 覆盖流程级的通知设置（JSON 的 notify，原样）
     */
    record Info(String id, String type, String name, String lane, String description, Map<String, Object> meta, boolean offline,
                String emptyAssignee, Map<String, Object> autoSkip, Map<String, Object> notifications) {
        public Info {
            meta = meta == null ? Map.of() : meta;
        }
    }

    /** 共同的字段 */
    Info info();

    /** 标识 */
    default String id() { return info().id(); }

    /** 类型 */
    default String type() { return info().type(); }

    /** 名称 */
    default String name() { return info().name(); }

    /** 所在泳道 */
    default String lane() { return info().lane(); }

    /** 开始；可以写表单在发起时的权限 */
    record Start(Info info, NodeFormAccess form) implements ProcessNode { }

    /** 结束 */
    record End(Info info, String result, boolean terminate) implements ProcessNode { }

    /** 人工节点 */
    record Task(Info info, List<AssigneeSource> assignees, List<Outcome> outcomes, MultiInstance multi, Due due,
                List<TaskRules.Reminder> reminders, TaskRules.Overdue overdue, TaskRules.Unclaimed unclaimed, String returnTo,
                TaskRules.AllowedActions allow, NodeFormAccess form) implements ProcessNode {
        public Task {
            assignees = assignees == null ? List.of() : List.copyOf(assignees);
            outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
            reminders = reminders == null ? List.of() : List.copyOf(reminders);
            allow = allow == null ? TaskRules.AllowedActions.ALL : allow;
        }
    }

    /** 排他、并行、包容网关 */
    record Gateway(Info info) implements ProcessNode { }

    /** 自动节点 */
    record Service(Info info, String action, Map<String, Object> args, String mode, Integer retryTimes, Duration retryBackoff,
                   String onFailure) implements ProcessNode {
        public Service { args = args == null ? Map.of() : args; }
    }

    /** 调用流程；{@code input} 的值、{@code forEach} 可以是 {@link Ref} */
    record Call(Info info, String process, String mode, Ref forEach, String as, Map<String, Object> input,
                Map<String, String> output) implements ProcessNode {
        public Call {
            input = input == null ? Map.of() : input;
            output = output == null ? Map.of() : output;
        }
    }

    /** 定时等待 */
    record Timer(Info info, Due waitFor) implements ProcessNode { }

    /** 等待外部事件 */
    record Receive(Info info, String event, List<String> outcomes, Due due, TaskRules.Overdue overdue,
                   Map<String, String> output) implements ProcessNode {
        public Receive {
            outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
            output = output == null ? Map.of() : output;
        }
    }

    /** 抄送 */
    record Cc(Info info, List<AssigneeSource> to, String message) implements ProcessNode {
        public Cc { to = to == null ? List.of() : List.copyOf(to); }
    }

    /** 接入方注册的自定义节点；{@code attributes} 是除共同字段以外的全部字段，由注册的实现解释 */
    record Custom(Info info, Map<String, Object> attributes) implements ProcessNode { }
}
