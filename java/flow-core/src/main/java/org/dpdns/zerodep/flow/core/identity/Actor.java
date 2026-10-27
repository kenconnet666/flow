package org.dpdns.zerodep.flow.core.identity;

import java.util.Map;
import java.util.Set;

/**
 * 操作人（docs/decisions/0006 第 2 节）：接入方自己的用户类型实现它，引擎的门面与扩展点里拿到的就是那个类型（{@code FlowEngine<U>}）。
 * 角色收件箱、部门收件箱按 {@link #getRoles()}、{@link #getDepartments()} 匹配候选（0007 的 1 节第 4 条），所以换了角色、调了部门的人马上看得到。
 */
public interface Actor {
    /** 用户编号（接入方用户表的主键） */
    Long getId();

    /** 显示的名字，存进待办、流转记录的快照（0007 的 1 节第 8 条）；默认是编号 */
    default String getName() { return String.valueOf(getId()); }

    /** 所在部门：要让上级部门的待办也能被认领，就把上级部门一起返回 */
    Set<Long> getDepartments();

    /** 拥有的角色 */
    Set<Long> getRoles();

    /** 接入方扩展的候选种类（岗位、项目组……）里的归属：种类 → 编号；收件箱按它匹配 {@code { "group": 种类, "id": … }} 的待办 */
    default Map<String, Set<Long>> getGroups() { return Map.of(); }

    /** 某一种扩展种类里的归属 */
    default Set<Long> groups(String kind) { return getGroups().getOrDefault(kind, Set.of()); }
}
