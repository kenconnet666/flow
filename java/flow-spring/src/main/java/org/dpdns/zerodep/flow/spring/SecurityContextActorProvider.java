package org.dpdns.zerodep.flow.spring;

import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.identity.ActorProvider;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

/**
 * 当前操作人从 Spring Security 取（0006 第 2 节的默认做法）：登录后的 principal 就是实现了 {@link Actor} 的用户对象。
 * principal 不是 Actor（例如只是用户名）时，接入方声明一个自己的 {@link ActorProvider} Bean，按用户名查出自己的用户类型。
 * 没有登录时报 {@code flow.forbidden}。
 */
public final class SecurityContextActorProvider implements ActorProvider<Actor> {
    @Override
    public Actor current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new FlowException(FlowErrorCode.FORBIDDEN, Map.of("reason", "not-signed-in"));
        }
        if (authentication.getPrincipal() instanceof Actor actor) return actor;
        throw new IllegalStateException("the Spring Security principal is a " + authentication.getPrincipal().getClass().getName()
            + ", not a flow Actor; declare an ActorProvider bean that turns it into your user type");
    }
}
