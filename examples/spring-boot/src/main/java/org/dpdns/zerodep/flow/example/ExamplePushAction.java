package org.dpdns.zerodep.flow.example;

import org.dpdns.zerodep.flow.core.extension.ServiceAction;
import org.springframework.stereotype.Component;

/**
 * 示例的自动节点动作 {@code demo.push}：假装把单据推给外部系统。声明成 Bean 就按名字注册了。
 * 参数 {@code fail} 为 true 时总是失败（演示重试用完后的"异常处理"），否则按参数 {@code outcome} 给结果（不写时走没写结果的出边）。
 *
 * <pre>{@code { "id": "push", "type": "service", "name": "推送 ERP", "action": "demo.push", "args": { "fail": true }, "retry": { "times": 1 } }}</pre>
 */
@Component
public class ExamplePushAction implements ServiceAction {
    @Override
    public String name() { return "demo.push"; }

    @Override
    public Result perform(Context context) {
        if (Boolean.TRUE.equals(context.getArgs().get("fail"))) throw new IllegalStateException("外部系统不可用（示例故意失败）");
        Object outcome = context.getArgs().get("outcome");
        return outcome == null ? Result.DONE : Result.outcome(String.valueOf(outcome));
    }
}
