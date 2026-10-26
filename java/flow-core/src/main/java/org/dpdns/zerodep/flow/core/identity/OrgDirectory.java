package org.dpdns.zerodep.flow.core.identity;

import java.util.List;

/**
 * 接入方的组织架构（0006 第 6 节）：引擎没有默认实现，接入方接上自己的用户、部门、角色。
 * 处理人解析成人时存名字快照（{@link #userName}）；会签的参与人写了角色、部门时展开成人（{@link #members}）。
 * 收件箱负责人等随用到它们的命令补上。
 */
public interface OrgDirectory {
    /** 用户的显示名字；查不到时返回编号本身 */
    String userName(Long userId);

    /**
     * 一个角色、部门（或接入方扩展的种类）下的人：会签的参与人写成角色、部门时，每个人一份待办。
     * 默认不支持；会签按角色、部门写时接入方实现它（部门要不要含下级部门由接入方定）。
     *
     * @param kind {@code role}、{@code department}，或扩展的种类
     * @param id   角色、部门的编号
     */
    default List<Long> members(String kind, Long id) {
        throw new UnsupportedOperationException("OrgDirectory.members is not implemented: countersign by " + kind + " needs it");
    }
}
