package org.dpdns.zerodep.flow.example;

import org.dpdns.zerodep.flow.core.identity.Actor;

import java.util.Set;

/**
 * 应用自己的用户类型：实现引擎的 {@link Actor}，引擎的扩展点里拿到的就是它（{@code FlowEngine<ExampleUser>}）。
 *
 * @param id          用户编号
 * @param name        名字
 * @param departments 所在部门
 * @param roles       角色
 */
public record ExampleUser(Long id, String name, Set<Long> departments, Set<Long> roles) implements Actor { }
