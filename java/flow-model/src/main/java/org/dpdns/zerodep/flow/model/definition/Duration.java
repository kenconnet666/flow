package org.dpdns.zerodep.flow.model.definition;

import lombok.Data;

import java.math.BigDecimal;

/** 时长（0005 第 4 节）：{@code { "workdays": 2 }} 这类，工作日按流程的 {@code calendar} 算。 */
@Data
public class Duration {
    /** 单位 */
    private final Unit unit;
    /** 数量 */
    private final BigDecimal amount;

    /** 单位；{@link #json()} 是 JSON 里的键 */
    public enum Unit {
        WORKDAYS("workdays"), DAYS("days"), HOURS("hours"), MINUTES("minutes");

        private final String json;

        Unit(String json) { this.json = json; }

        /** JSON 里的键 */
        public String json() { return json; }
    }
}
