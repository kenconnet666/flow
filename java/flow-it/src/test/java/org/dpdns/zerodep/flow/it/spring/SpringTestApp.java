package org.dpdns.zerodep.flow.it.spring;

import org.dpdns.zerodep.flow.core.identity.ActorProvider;
import org.dpdns.zerodep.flow.core.identity.OrgDirectory;
import org.dpdns.zerodep.flow.it.support.TestOrg;
import org.dpdns.zerodep.flow.it.support.TestUser;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.Set;

/**
 * 测试用的 Spring Boot 应用：接入方要写的只有这些——组织架构（{@link OrgDirectory}）和当前操作人（{@link ActorProvider}）。
 * 这里的当前操作人从请求头 {@code X-User} 取，只为测试方便；真实应用从自己的登录信息取。
 */
@SpringBootApplication
public class SpringTestApp {
    /** 测试里的人：编号 → 用户 */
    static final Map<String, TestUser> USERS = Map.of(
        "alice", new TestUser("alice", "林小满", Set.of(), Set.of()),
        "bob", new TestUser("bob", "周启航", Set.of(), Set.of("finance")),
        "boss", new TestUser("boss", "总经理", Set.of(), Set.of()));

    @Bean
    OrgDirectory orgDirectory() {
        return new TestOrg(Map.of("alice", "林小满", "bob", "周启航", "boss", "总经理"));
    }

    @Bean
    ActorProvider<TestUser> actorProvider() {
        return () -> {
            var request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            TestUser user = USERS.get(request.getHeader("X-User"));
            if (user == null) throw new IllegalStateException("unknown X-User " + request.getHeader("X-User"));
            return user;
        };
    }
}
