package org.dpdns.zerodep.flow.example;

import org.dpdns.zerodep.flow.core.identity.OrgDirectory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 示例的组织架构：写死在内存里的几个人（虚构的门店采购场景）。真实应用在这里查自己的用户表、部门表、角色表。
 */
@Component
public class ExampleOrgDirectory implements OrgDirectory {
    /** 用户编号 → 用户 */
    static final Map<String, ExampleUser> USERS = Map.of(
        "alice", new ExampleUser("alice", "林小满", Set.of("store-1"), Set.of()),
        "bob", new ExampleUser("bob", "周启航", Set.of("finance"), Set.of("finance")),
        "boss", new ExampleUser("boss", "总经理", Set.of("office"), Set.of("manager")));

    @Override
    public String userName(String userId) {
        return Optional.ofNullable(USERS.get(userId)).map(ExampleUser::name).orElse(userId);
    }
}
