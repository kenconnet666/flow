package org.dpdns.zerodep.flow.example.extension;

import org.dpdns.zerodep.flow.core.spi.ExtensionInfo;
import org.dpdns.zerodep.flow.core.spi.ExtensionParam;
import org.dpdns.zerodep.flow.core.spi.ServiceAction;
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

    /** 设计器里：选"推送外部系统"，参数是两个控件，出边能选"对方接收""对方拒收" */
    @Override
    public ExtensionInfo info() {
        return ExtensionInfo.of("推送外部系统").describedAs("把单据推给外部系统（示例：不真的推）")
            .param(ExtensionParam.of("outcome", "给出的结果", "text").describedAs("不写时走没写结果的出边"))
            .param(ExtensionParam.of("fail", "故意失败", "boolean").describedAs("演示重试用完后的异常处理"))
            .outcome("accepted", "对方接收")
            .outcome("rejected", "对方拒收");
    }

    @Override
    public Result perform(Context context) {
        if (Boolean.TRUE.equals(context.getArgs().get("fail"))) throw new IllegalStateException("外部系统不可用（示例故意失败）");
        Object outcome = context.getArgs().get("outcome");
        return outcome == null ? Result.DONE : Result.outcome(String.valueOf(outcome));
    }
}
