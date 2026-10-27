package org.dpdns.zerodep.flow.model.definition;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 处理人的来源（0005 第 3 节）；{@code assignees} 里的几个取并集。值写成 {@code Object} 的地方是直接写的编号或一个 {@link Ref}。
 * 标准的组织模型是用户、部门、角色；岗位这类由接入方用 {@link Group} 扩展。
 */
public interface AssigneeSource {
    /** 指定的人；{@code people} 字段里有几个人就是几个候选 */
    @Data
    static class User implements AssigneeSource {
        private final Object user;
        private final Integer weight;
    }

    /** 角色收件箱：有这个角色的人认领 */
    @Data
    static class Role implements AssigneeSource {
        private final Object role;
    }

    /** 部门下面的人认领 */
    @Data
    static class Department implements AssigneeSource {
        private final Object department;
    }

    /** 发起人 */
    @Data
    static class Initiator implements AssigneeSource {
    }

    /** 与某个人工节点最后的处理人相同 */
    @Data
    static class SameAs implements AssigneeSource {
        private final String node;
    }

    /** 接入方注册的解析器 */
    @Data
    static class Resolver implements AssigneeSource {
        private final String name;
        private final Map<String, Object> args;

        public Resolver(String name, Map<String, Object> args) { args = args == null ? Map.of() : args;
            this.name = name;
            this.args = args;
        }
    }

    /** 接入方扩展的候选种类（岗位、项目组……）。 */
    @Data
    static class Group implements AssigneeSource {
        /** 种类 */
        private final String kind;
        /** 直接写的编号或 {@link Ref} */
        private final Object id;
    }

    /** 上一步的处理人提交时选（0005 的 5.6） */
    @Data
    static class Pick implements AssigneeSource {
        private final List<AssigneeSource> from;
        private final Integer max;

        public Pick(List<AssigneeSource> from, Integer max) { from = from == null ? List.of() : List.copyOf(from);
            this.from = from;
            this.max = max;
        }
    }

    /** 能当收件箱的来源：角色、部门、扩展的种类 */
    default boolean isInbox() {
        return this instanceof Role || this instanceof Department || this instanceof Group;
    }
}
