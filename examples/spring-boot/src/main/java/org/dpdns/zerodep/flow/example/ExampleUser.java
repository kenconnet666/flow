package org.dpdns.zerodep.flow.example;

import lombok.Data;
import org.dpdns.zerodep.flow.core.spi.Actor;

import java.util.Set;

/** 应用自己的用户类型：实现引擎的 {@link Actor}，引擎的扩展点里拿到的就是它（{@code FlowEngine<ExampleUser>}）。 */
@Data
public class ExampleUser implements Actor {
    /** 用户编号 */
    private final Long id;
    /** 名字 */
    private final String name;
    /** 所在部门 */
    private final Set<Long> departments;
    /** 角色 */
    private final Set<Long> roles;
}
