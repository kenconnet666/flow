package org.dpdns.zerodep.flow.model.definition;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程节点（0005 第 4、5 节）：内置的十一种，加接入方注册的自定义节点（{@code type} 以 {@code x-} 开头）。
 * 共同的字段放在 {@link Info}，各类节点的 record 只多它自己的字段。
 */
public interface ProcessNode {
    /** 内置的节点类型 */
    Set<String> BUILTIN_TYPES = Set.of("start", "end", "task", "exclusive", "parallel", "inclusive", "service", "call", "timer", "receive", "cc");

    /** 按结果分出边的类型：人工、自动、调用流程、等待外部事件 */
    Set<String> BRANCHING_TYPES = Set.of("task", "service", "call", "receive");

    /** 所有节点共有的字段。 */
    @Data
    static class Info {
        /** 标识，流程内唯一 */
        private final String id;
        /** 类型 */
        private final String type;
        /** 名称 */
        private final String name;
        /** 所在泳道 */
        private final String lane;
        /** 说明 */
        private final String description;
        /** 业务附加信息，引擎原样保存不解释 */
        private final Map<String, Object> meta;
        /** 线下动作 */
        private final boolean offline;
        /** 覆盖流程级的"处理人为空时" */
        private final String emptyAssignee;
        /** 覆盖流程级的"自动跳过"（JSON 原样） */
        private final Map<String, Object> autoSkip;
        /** 覆盖流程级的通知设置（JSON 的 notify，原样） */
        private final Map<String, Object> notifications;

        public Info(String id, String type, String name, String lane, String description, Map<String, Object> meta, boolean offline, String emptyAssignee, Map<String, Object> autoSkip, Map<String, Object> notifications) {
            meta = meta == null ? Map.of() : meta;
            this.id = id;
            this.type = type;
            this.name = name;
            this.lane = lane;
            this.description = description;
            this.meta = meta;
            this.offline = offline;
            this.emptyAssignee = emptyAssignee;
            this.autoSkip = autoSkip;
            this.notifications = notifications;
        }
    }

    /** 共同的字段 */
    Info getInfo();

    /** 标识 */
    default String getId() { return getInfo().getId(); }

    /** 类型 */
    default String getType() { return getInfo().getType(); }

    /** 名称 */
    default String getName() { return getInfo().getName(); }

    /** 所在泳道 */
    default String getLane() { return getInfo().getLane(); }

    /** 开始；可以写表单在发起时的权限 */
    @Data
    static class Start implements ProcessNode {
        private final Info info;
        private final NodeFormAccess form;
    }

    /** 结束 */
    @Data
    static class End implements ProcessNode {
        private final Info info;
        private final String result;
        private final boolean terminate;
    }

    /** 人工节点 */
    @Data
    static class Task implements ProcessNode {
        private final Info info;
        private final List<AssigneeSource> assignees;
        private final List<Outcome> outcomes;
        private final MultiInstance multi;
        private final Due due;
        private final List<TaskRules.Reminder> reminders;
        private final TaskRules.Overdue overdue;
        private final TaskRules.Unclaimed unclaimed;
        private final String returnTo;
        private final TaskRules.AllowedActions allow;
        private final NodeFormAccess form;

        public Task(Info info, List<AssigneeSource> assignees, List<Outcome> outcomes, MultiInstance multi, Due due, List<TaskRules.Reminder> reminders, TaskRules.Overdue overdue, TaskRules.Unclaimed unclaimed, String returnTo, TaskRules.AllowedActions allow, NodeFormAccess form) {
            assignees = assignees == null ? List.of() : List.copyOf(assignees);
            outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
            reminders = reminders == null ? List.of() : List.copyOf(reminders);
            allow = allow == null ? TaskRules.AllowedActions.ALL : allow;
            this.info = info;
            this.assignees = assignees;
            this.outcomes = outcomes;
            this.multi = multi;
            this.due = due;
            this.reminders = reminders;
            this.overdue = overdue;
            this.unclaimed = unclaimed;
            this.returnTo = returnTo;
            this.allow = allow;
            this.form = form;
        }
    }

    /** 排他、并行、包容网关 */
    @Data
    static class Gateway implements ProcessNode {
        private final Info info;
    }

    /** 自动节点 */
    @Data
    static class Service implements ProcessNode {
        private final Info info;
        private final String action;
        private final Map<String, Object> args;
        private final String mode;
        private final Integer retryTimes;
        private final Duration retryBackoff;
        private final String onFailure;

        public Service(Info info, String action, Map<String, Object> args, String mode, Integer retryTimes, Duration retryBackoff, String onFailure) { args = args == null ? Map.of() : args;
            this.info = info;
            this.action = action;
            this.args = args;
            this.mode = mode;
            this.retryTimes = retryTimes;
            this.retryBackoff = retryBackoff;
            this.onFailure = onFailure;
        }
    }

    /** 调用流程；{@code input} 的值、{@code forEach} 可以是 {@link Ref} */
    @Data
    static class Call implements ProcessNode {
        private final Info info;
        private final String process;
        private final String mode;
        private final Ref forEach;
        private final String as;
        private final Map<String, Object> input;
        private final Map<String, String> output;

        public Call(Info info, String process, String mode, Ref forEach, String as, Map<String, Object> input, Map<String, String> output) {
            input = input == null ? Map.of() : input;
            output = output == null ? Map.of() : output;
            this.info = info;
            this.process = process;
            this.mode = mode;
            this.forEach = forEach;
            this.as = as;
            this.input = input;
            this.output = output;
        }
    }

    /** 定时等待 */
    @Data
    static class Timer implements ProcessNode {
        private final Info info;
        private final Due waitFor;
    }

    /** 等待外部事件 */
    @Data
    static class Receive implements ProcessNode {
        private final Info info;
        private final String event;
        private final List<String> outcomes;
        private final Due due;
        private final TaskRules.Overdue overdue;
        private final Map<String, String> output;

        public Receive(Info info, String event, List<String> outcomes, Due due, TaskRules.Overdue overdue, Map<String, String> output) {
            outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
            output = output == null ? Map.of() : output;
            this.info = info;
            this.event = event;
            this.outcomes = outcomes;
            this.due = due;
            this.overdue = overdue;
            this.output = output;
        }
    }

    /** 抄送 */
    @Data
    static class Cc implements ProcessNode {
        private final Info info;
        private final List<AssigneeSource> to;
        private final String message;

        public Cc(Info info, List<AssigneeSource> to, String message) { to = to == null ? List.of() : List.copyOf(to);
            this.info = info;
            this.to = to;
            this.message = message;
        }
    }

    /** 接入方注册的自定义节点；{@code attributes} 是除共同字段以外的全部字段，由注册的实现解释 */
    @Data
    static class Custom implements ProcessNode {
        private final Info info;
        private final Map<String, Object> attributes;
    }
}
