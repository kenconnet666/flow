package org.dpdns.zerodep.flow.it.support;

import org.dpdns.zerodep.flow.core.identity.Actor;

import java.util.Set;

/**
 * 测试里的操作人：接入方自己的用户类型就是这样实现 {@link Actor} 的。
 *
 * @param id          用户编号
 * @param name        名字
 * @param departments 所在部门
 * @param roles       角色
 */
public record TestUser(String id, String name, Set<String> departments, Set<String> roles) implements Actor {
    /** 只有编号与角色 */
    public static TestUser of(String id, String name, String... roles) { return new TestUser(id, name, Set.of(), Set.of(roles)); }
}
