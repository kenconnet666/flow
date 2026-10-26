package org.dpdns.zerodep.flow.core.identity;

/**
 * 接入方的组织架构（0006 第 6 节）：引擎没有默认实现，接入方接上自己的用户、部门、角色。
 * 现在只用到按编号取名字（处理人解析成人时存名字快照）；部门、角色展开成人、收件箱负责人随用到它们的命令补上。
 */
public interface OrgDirectory {
    /** 用户的显示名字；查不到时返回编号本身 */
    String userName(Long userId);
}
