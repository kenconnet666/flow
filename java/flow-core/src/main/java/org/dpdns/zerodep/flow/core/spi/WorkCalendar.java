package org.dpdns.zerodep.flow.core.spi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 工作日历（0005 第 2 节的 {@code calendar}、0007 的 {@code flow_calendar}）：时限写成 {@code { "workdays": 2 }} 时按它算。
 * 默认 {@link #weekdays} 只跳过周六周日；有节假日、调休的接入方实现这个接口（读自己的节假日表），在 {@code FlowEngineBuilder}
 * 里换掉。时间都按 UTC 存，"哪一天"按日历的时区算。
 */
public interface WorkCalendar {
    /** 日历的时区：只写日期的时限（{@code until} 一个日期字段）按它取那天的结束 */
    ZoneId zone();

    /**
     * 从 from 起往后数 days 个工作日（同一时刻），days 是负数时往前数；小数部分按 24 小时折算。
     *
     * @param calendar 流程定义里写的日历名字；默认实现不区分
     */
    Instant plusWorkdays(Instant from, BigDecimal days, String calendar);

    /** 周一到周五上班，没有节假日：在 zone 的日期上跳过周六周日 */
    static WorkCalendar weekdays(ZoneId zone) {
        return new WorkCalendar() {
            @Override
            public ZoneId zone() { return zone; }

            @Override
            public Instant plusWorkdays(Instant from, BigDecimal days, String calendar) {
                int whole = days.setScale(0, RoundingMode.DOWN).intValueExact();
                int step = Integer.signum(whole);
                ZonedDateTime at = from.atZone(zone);
                for (int left = Math.abs(whole); left > 0; ) {
                    at = at.plusDays(step);
                    if (at.getDayOfWeek() != DayOfWeek.SATURDAY && at.getDayOfWeek() != DayOfWeek.SUNDAY) left--;
                }
                long fractionSeconds = days.subtract(BigDecimal.valueOf(whole)).multiply(BigDecimal.valueOf(86_400)).longValue();
                return at.toInstant().plusSeconds(fractionSeconds);
            }
        };
    }
}
