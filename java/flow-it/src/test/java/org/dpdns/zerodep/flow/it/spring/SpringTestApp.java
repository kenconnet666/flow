package org.dpdns.zerodep.flow.it.spring;

import org.dpdns.zerodep.flow.core.spi.ActorProvider;
import org.dpdns.zerodep.flow.core.spi.OrgDirectory;
import org.dpdns.zerodep.flow.it.support.TestOrg;
import org.dpdns.zerodep.flow.it.support.TestUser;
import org.dpdns.zerodep.flow.spring.FlowTenant;
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
    /** 测试里的人（编号同 spec/scenarios 的约定）：用户编号 → 用户 */
    static final Map<Long, TestUser> USERS = Map.of(
        1001L, new TestUser(1001L, "林小满", Set.of(), Set.of()),
        1002L, new TestUser(1002L, "周启航", Set.of(), Set.of(1L)),
        1004L, new TestUser(1004L, "总经理", Set.of(), Set.of()));

    /** 多租户：请求头 X-Tenant，不带时 0（和不开多租户一样）；不在请求里时也是 0 */
    @Bean
    FlowTenant flowTenant() {
        return () -> {
            if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return 0L;
            String header = attributes.getRequest().getHeader("X-Tenant");
            return header == null ? 0L : Long.valueOf(header);
        };
    }

    @Bean
    OrgDirectory orgDirectory() {
        return new TestOrg(USERS.values());
    }

    @Bean
    ActorProvider<TestUser> actorProvider() {
        return () -> {
            var request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            String header = request.getHeader("X-User");
            TestUser user = header == null ? null : USERS.get(Long.valueOf(header));
            if (user == null) throw new IllegalStateException("unknown X-User " + request.getHeader("X-User"));
            return user;
        };
    }
}
