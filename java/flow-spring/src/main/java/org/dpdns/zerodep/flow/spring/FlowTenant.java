package org.dpdns.zerodep.flow.spring;

/**
 * 多租户时的当前租户：声明成 Bean，自动配置组装引擎时交给 {@code FlowEngine.builder(…).tenant(…)}。
 * 一般从登录信息或请求头取；不在请求里（启动、后台线程）时返回 0 或抛错由接入方定。作业由引擎按作业所属的租户做，不经这里。
 * 不用多租户时不要声明（租户列都是 0）。
 */
@FunctionalInterface
public interface FlowTenant {
    /** 当前租户的编号 */
    Long current();
}
