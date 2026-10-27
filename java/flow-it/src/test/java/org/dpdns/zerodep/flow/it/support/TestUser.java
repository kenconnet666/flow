package org.dpdns.zerodep.flow.it.support;

import lombok.Data;
import org.dpdns.zerodep.flow.core.identity.Actor;

import java.util.Set;

/** 测试里的操作人：接入方自己的用户类型就是这样实现 {@link Actor} 的。 */
@Data
public class TestUser implements Actor {
    /** 用户编号 */
    private final Long id;
    /** 名字 */
    private final String name;
    /** 所在部门 */
    private final Set<Long> departments;
    /** 角色 */
    private final Set<Long> roles;

    /** 只有编号与角色 */
    public static TestUser of(Long id, String name, Long... roles) { return new TestUser(id, name, Set.of(), Set.of(roles)); }
}
