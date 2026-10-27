package org.dpdns.zerodep.flow.example.extension;

import org.dpdns.zerodep.flow.core.extension.Choice;
import org.dpdns.zerodep.flow.core.extension.CustomNode;
import org.dpdns.zerodep.flow.core.extension.ExtensionInfo;
import org.dpdns.zerodep.flow.core.extension.ExtensionParam;
import org.springframework.stereotype.Component;

/**
 * 示例的自定义节点 {@code x-demo-check}：到达时去外部系统核验一下，当场给出"通过""不通过"（示例：开了严格模式就不通过）。
 * 设计器的节点列表里有它，属性面板按下面声明的属性画控件，出边能选两个结果。
 *
 * <pre>{@code { "id": "check", "type": "x-demo-check", "name": "外部核验", "system": "erp", "strict": false }}</pre>
 */
@Component
public class ExampleCheckNode implements CustomNode {
    @Override
    public String type() { return "x-demo-check"; }

    @Override
    public ExtensionInfo info() {
        return ExtensionInfo.of("外部核验").describedAs("到外部系统核验单据，当场给出结果")
            .param(ExtensionParam.select("system", "核验的系统", new Choice("erp", "ERP"), new Choice("crm", "CRM")).required())
            .param(ExtensionParam.of("strict", "严格模式", "boolean").describedAs("示例：开了就不通过"))
            .outcome("pass", "通过")
            .outcome("fail", "不通过");
    }

    @Override
    public Result arrive(Context context) {
        return Result.proceed(Boolean.TRUE.equals(context.getAttributes().get("strict")) ? "fail" : "pass");
    }
}
