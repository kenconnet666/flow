package org.dpdns.zerodep.flow.example;

import org.dpdns.zerodep.flow.core.identity.OrgDirectory;
import org.springframework.stereotype.Component;

import java.util.List;
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
        1003L, new ExampleUser(1003L, "沈若水", Set.of(20L), Set.of(1L)),
        1004L, new ExampleUser(1004L, "总经理", Set.of(30L), Set.of(2L)),
        1005L, new ExampleUser(1005L, "许知远", Set.of(30L), Set.of()));

    @Override
    public String userName(Long userId) {
        return Optional.ofNullable(USERS.get(userId)).map(ExampleUser::name).orElse(String.valueOf(userId));
    }

    /** 有这个角色、在这个部门的人：会签的参与人、抄送节点的收件人写成角色、部门时展开 */
    @Override
    public List<Long> members(String kind, Long id) {
        return USERS.values().stream()
            .filter(user -> ("role".equals(kind) ? user.roles() : "department".equals(kind) ? user.departments() : Set.<Long>of()).contains(id))
            .map(ExampleUser::id).sorted().toList();
    }
}
