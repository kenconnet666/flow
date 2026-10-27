package org.dpdns.zerodep.flow.core.spi;

/**
 * 当前操作人（0006 第 2 节）：命令、查询没有显式带操作人时从这里取。Spring 里默认从 SecurityContext 取（flow-spring），
 * 不用 Spring 时由接入方给出。
 */
@FunctionalInterface
public interface ActorProvider<U extends Actor> {
    /** 当前的操作人；没有登录时报错 */
    U current();
}
