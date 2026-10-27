package org.dpdns.zerodep.flow.example.extension;

import org.dpdns.zerodep.flow.core.extension.ExtensionInfo;
import org.dpdns.zerodep.flow.core.extension.OutcomeGuard;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 示例的提交前检查 {@code demo.amount-cap}：表单的金额（{@code amount}）超过一百万时不让按这个结果提交。
 * 设计器里在处理结果上选它（定义里写成结果的 {@code guards}）。
 */
@Component
public class ExampleAmountCapGuard implements OutcomeGuard {
    private static final BigDecimal CAP = new BigDecimal("1000000");

    @Override
    public String name() { return "demo.amount-cap"; }

    @Override
    public ExtensionInfo info() { return ExtensionInfo.of("金额不超过一百万").describedAs("表单的金额超过一百万时不能提交"); }

    @Override
    public String check(Context context) {
        return context.getForm().get("amount") instanceof BigDecimal amount && amount.compareTo(CAP) > 0 ? "金额超过一百万，不能按这个结果提交" : null;
    }
}
