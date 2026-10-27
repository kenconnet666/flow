package org.dpdns.zerodep.flow.example.extension;

import org.dpdns.zerodep.flow.core.spi.ComputedValue;
import org.dpdns.zerodep.flow.core.spi.ExtensionInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 示例的计算值 {@code demo.amountLevel}：表单的金额（{@code amount}）落在哪一档——{@code high} 或 {@code normal}。
 * 阈值在 application.yml 的 {@code demo.amount-threshold}（默认一万），调整时不用重新发布流程。
 *
 * <pre>{@code { "field": "fn.demo.amountLevel", "op": "eq", "value": "high" }}</pre>
 */
@Component
public class ExampleAmountLevel implements ComputedValue {
    private final BigDecimal threshold;

    public ExampleAmountLevel(@Value("${demo.amount-threshold:10000}") BigDecimal threshold) { this.threshold = threshold; }

    @Override
    public String name() { return "demo.amountLevel"; }

    @Override
    public ExtensionInfo info() {
        return ExtensionInfo.of("金额档位").describedAs("high：金额达到 " + threshold.toPlainString() + "；其余 normal").valueType("text");
    }

    @Override
    public Object compute(Context context) {
        return context.getRead().apply("form.amount") instanceof BigDecimal amount && amount.compareTo(threshold) >= 0 ? "high" : "normal";
    }
}
