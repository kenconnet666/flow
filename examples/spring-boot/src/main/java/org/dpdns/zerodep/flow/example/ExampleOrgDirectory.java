package org.dpdns.zerodep.flow.example;

import org.dpdns.zerodep.flow.core.identity.OrgDirectory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 示例的组织架构：写死在内存里的几个人（虚构的门店采购场景）。真实应用在这里查自己的用户表、部门表、角色表。
 * 编号同 spec/scenarios 的约定：角色 1 财务、2 总经理；部门 10 门店、20 财务部、30 总经办。
 */
@Component
public class ExampleOrgDirectory implements OrgDirectory {
    /** 用户编号 → 用户 */
    static final Map<Long, ExampleUser> USERS = Map.of(
        1001L, new ExampleUser(1001L, "林小满", Set.of(10L), Set.of()),
        1002L, new ExampleUser(1002L, "周启航", Set.of(20L), Set.of(1L)),
        1004L, new ExampleUser(1004L, "总经理", Set.of(30L), Set.of(2L)));

    @Override
    public String userName(Long userId) {
        return Optional.ofNullable(USERS.get(userId)).map(ExampleUser::name).orElse(String.valueOf(userId));
    }
}
