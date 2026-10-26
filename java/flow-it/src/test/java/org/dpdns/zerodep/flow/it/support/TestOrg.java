package org.dpdns.zerodep.flow.it.support;

import org.dpdns.zerodep.flow.core.identity.OrgDirectory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 测试里的组织架构：就是这几个人。接入方实现 {@link OrgDirectory} 时接自己的用户表、部门表、角色表。
 * 约定（同 spec/scenarios 的 README）：1000 是流程管理员；有角色 9 的人是所有收件箱的负责人。
 *
 * @param users 所有的人
 */
public record TestOrg(Collection<TestUser> users) implements OrgDirectory {
    @Override
    public String userName(Long userId) {
        return users.stream().filter(u -> u.id().equals(userId)).map(TestUser::name).findFirst().orElse(String.valueOf(userId));
    }

    /** 流程管理员：1000 */
    @Override
    public List<Long> administrators() {
        return users.stream().map(TestUser::id).filter(id -> id == 1000L).toList();
    }

    /** 收件箱的负责人：有角色 9 的那个人 */
    @Override
    public Optional<Long> inboxManager(String kind, Long id) {
        return users.stream().filter(u -> u.roles().contains(9L)).map(TestUser::id).findFirst();
    }

    /** 有这个角色、在这个部门的人（会签的参与人、抄送节点的收件人写成角色、部门时展开） */
    @Override
    public List<Long> members(String kind, Long id) {
        return users.stream().filter(u -> groupsOf(u, kind).contains(id)).map(TestUser::id).toList();
    }

    private static Set<Long> groupsOf(TestUser user, String kind) {
        return switch (kind) {
            case "role" -> user.roles();
            case "department" -> user.departments();
            default -> Set.of();
        };
    }
}
