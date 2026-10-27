package org.dpdns.zerodep.flow.testkit;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import lombok.Builder;
import lombok.Getter;
import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.identity.OrgDirectory;

/**
 * 契约核对要的东西：接入方组装好的引擎（连的是测试库），同一个 easy-query 与组织架构，加上几个真实存在的样例账号。
 * <pre>{@code
 * FlowContract.<AppUser>builder()
 *     .engine(engine).db(easyQuery).org(orgDirectory)
 *     .admin(users.byLogin("admin"))       // 能发布流程（PermissionPolicy 放行 definitions.publish）
 *     .starter(users.byLogin("zhang"))     // 能发起
 *     .role(11L)                           // 一个角色……
 *     .member(users.byLogin("li"))         // ……和它的一个成员（Actor.getRoles() 里有这个角色）
 *     .build();
 * }</pre>
 */
@Getter
@Builder
public class FlowContract<U extends Actor> {
    /** 接入方组装好的引擎 */
    private final FlowEngine<U> engine;
    /** 引擎用的那个 easy-query（查表在不在；引擎组装时给它装了转换器，换一个实例读不了） */
    private final EasyEntityQuery db;
    /** 接入方的组织架构 */
    private final OrgDirectory org;
    /** 能设计、发布流程、做管理员操作的人 */
    private final U admin;
    /** 能发起流程的人 */
    private final U starter;
    /** 一个角色的编号：冒烟流程的待办进它的收件箱 */
    private final Long role;
    /** 这个角色的一个成员 */
    private final U member;
    /** 冒烟流程的标识；同一个库里重复跑时发布成新版本，跑完停用 */
    @Builder.Default
    private final String smokeProcess = "flow-contract-smoke";
}
