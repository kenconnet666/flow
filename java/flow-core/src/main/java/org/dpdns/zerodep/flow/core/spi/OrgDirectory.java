package org.dpdns.zerodep.flow.core.spi;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 接入方的组织架构（0006 第 6 节）：引擎没有默认实现，接入方接上自己的用户、部门、角色。
 * 处理人解析成人时存名字快照（{@link #userName}）；会签的参与人写了角色、部门时展开成人（{@link #members}）；
 * 处理人为空时转给管理员（{@link #administrators}）；收件箱很久没人认领时转负责人指定（{@link #inboxManager}）；
 * 不在岗的人的待办交给代理人（{@link #standIn}）。
 */
public interface OrgDirectory {
    /** 用户的显示名字；查不到时返回编号本身 */
    String userName(Long userId);

    /** 流程管理员：处理人解析为空、定义写了 {@code emptyAssignee: "admin"} 时待办转给他们（几个人时进收件箱）；默认没有，报错 */
    default List<Long> administrators() { return List.of(); }

    /**
     * 一个收件箱（角色、部门……）的负责人：无人认领超时、节点写了 {@code unclaimed.action: "assignByManager"} 时由他指定承办人。
     * 默认没有：只标成等指定、记一条流转记录，管理员用改派处理。
     */
    default Optional<Long> inboxManager(String kind, Long id) { return Optional.empty(); }

    /**
     * 一个角色、部门（或接入方扩展的种类）下的人：会签的参与人、抄送写成角色、部门时展开成人；收件箱的到达通知也按它发给候选人。
     * 默认不支持；会签、抄送按角色、部门写时接入方实现它（部门要不要含下级部门由接入方定）。
     *
     * @param kind {@code role}、{@code department}，或扩展的种类
     * @param id   角色、部门的编号
     */
    default List<Long> members(String kind, Long id) {
        throw new UnsupportedOperationException("OrgDirectory.members is not implemented: countersign by " + kind + " needs it");
    }

    /**
     * 这个人不在岗（休假、出差）时由谁代他处理：待办要落到他（直接落到人、收件箱里点名的人、会签的参与人）时问一次，
     * 有代理人就换成代理人，待办记下代谁处理（{@code onBehalfOf}），被代理的人照样能看这个实例。代理人也不在岗时接着往下找，绕回来就停。
     * 默认没有。代理关系与生效时段由接入方维护（休假表、代理设置）；已经落到他手里的待办不会自动移走，代理生效时接入方用转办把它们转过去。
     *
     * @param processKey 哪个流程（只代理某些流程时按它判断）
     * @param at         待办生成的时间
     */
    default Optional<Long> standIn(Long userId, String processKey, Instant at) { return Optional.empty(); }
}
