package org.dpdns.zerodep.flow.example;

import org.dpdns.zerodep.flow.core.identity.ActorProvider;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * 示例的当前操作人：从请求头 {@code X-User} 取用户编号。<b>只为演示方便，不能用在生产</b>——
 * 真实应用从登录信息取（Spring Security 的 principal、会话、令牌），登录与接口的保护由应用自己的安全框架负责。
 */
@Component
public class ExampleActors implements ActorProvider<ExampleUser> {
    @Override
    public ExampleUser current() {
        var request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        ExampleUser user = ExampleOrgDirectory.USERS.get(request.getHeader("X-User"));
        if (user == null) throw new FlowException(FlowErrorCode.FORBIDDEN, Map.of("reason", "not-signed-in"));
        return user;
    }
}
