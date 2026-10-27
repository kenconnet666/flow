package org.dpdns.zerodep.flow.example.extension;

import org.dpdns.zerodep.flow.core.extension.ExtensionInfo;
import org.dpdns.zerodep.flow.core.extension.ExtensionParam;
import org.dpdns.zerodep.flow.core.extension.PeopleResolver;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 示例的处理人解析器 {@code demo.leader}：发起人往上第几级的负责人。示例的组织只有两级：第 1 级是许知远（1005），再往上是总经理（1004）。
 * 真实应用在这里按自己的汇报关系查。
 *
 * <pre>{@code { "resolver": "demo.leader", "args": { "level": 2 } }}</pre>
 */
@Component
public class ExampleLeaderResolver implements PeopleResolver {
    @Override
    public String name() { return "demo.leader"; }

    @Override
    public ExtensionInfo info() {
        return ExtensionInfo.of("发起人的上级").describedAs("发起人往上第几级的负责人")
            .param(ExtensionParam.of("level", "往上几级", "number").required().describedAs("1 是直接上级"));
    }

    @Override
    public List<Long> resolve(Context context) {
        Object level = context.getArgs().get("level");
        return List.of(level instanceof Number n && n.intValue() >= 2 ? 1004L : 1005L);
    }
}
