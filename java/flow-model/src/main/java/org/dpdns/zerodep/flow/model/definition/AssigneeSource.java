package org.dpdns.zerodep.flow.model.definition;

import java.util.List;
import java.util.Map;

/**
 * 处理人的来源（0005 第 3 节）；{@code assignees} 里的几个取并集。值写成 {@code Object} 的地方是直接写的编号或一个 {@link Ref}。
 * 标准的组织模型是用户、部门、角色；岗位这类由接入方用 {@link Group} 扩展。
 */
public sealed interface AssigneeSource {
    /** 指定的人；{@code people} 字段里有几个人就是几个候选 */
    record User(Object user, Integer weight) implements AssigneeSource { }

    /** 角色收件箱：有这个角色的人认领 */
    record Role(Object role) implements AssigneeSource { }

    /** 部门下面的人认领 */
    record Department(Object department) implements AssigneeSource { }

    /** 发起人 */
    record Initiator() implements AssigneeSource { }

    /** 与某个人工节点最后的处理人相同 */
    record SameAs(String node) implements AssigneeSource { }

    /** 接入方注册的解析器 */
    record Resolver(String name, Map<String, Object> args) implements AssigneeSource {
        public Resolver { args = args == null ? Map.of() : args; }
    }

    /**
     * 接入方扩展的候选种类（岗位、项目组……）。
     *
     * @param kind 种类
     * @param id   直接写的编号或 {@link Ref}
     */
    record Group(String kind, Object id) implements AssigneeSource { }

    /** 上一步的处理人提交时选（0005 的 5.6） */
    record Pick(List<AssigneeSource> from, Integer max) implements AssigneeSource {
        public Pick { from = from == null ? List.of() : List.copyOf(from); }
    }

    /** 能当收件箱的来源：角色、部门、扩展的种类 */
    default boolean isInbox() {
        return this instanceof Role || this instanceof Department || this instanceof Group;
    }
}
