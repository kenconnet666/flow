package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.calendar.WorkCalendar;
import org.dpdns.zerodep.flow.model.definition.Due;
import org.dpdns.zerodep.flow.model.definition.Duration;
import org.dpdns.zerodep.flow.model.definition.Ref;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.function.Function;

/**
 * 时限算成时刻（0005 的 5.3 与定时节点）：{@code { "hours": 4 }} 这样的一段时长从某一刻往后加，工作日按日历跳过休息日；
 * {@code { "until": … }} 是一个时刻或日期（可以是字段引用），只写日期时算那天结束（第二天零点）。
 */
final class Deadlines {
    private final WorkCalendar calendar;
    private final String calendarName;

    /**
     * @param calendarName 流程定义里写的日历名字（{@code settings.calendar}）；没写时为 null
     */
    Deadlines(WorkCalendar calendar, String calendarName) {
        this.calendar = calendar;
        this.calendarName = calendarName;
    }

    /** 时限到期的时刻；{@code until} 的引用没有值、不是时间时为 null（没有时限） */
    Instant of(Due due, Instant from, Function<String, Object> read) {
        if (due instanceof Due.After after) return plus(after.duration(), from);
        Object value = ((Due.Until) due).value();
        if (value instanceof Ref ref) value = read.apply(ref.ref());
        return value == null ? null : moment(String.valueOf(value));
    }

    /** from 之后一段时长 */
    Instant plus(Duration duration, Instant from) {
        return switch (duration.unit()) {
            case WORKDAYS -> calendar.plusWorkdays(from, duration.amount(), calendarName);
            case DAYS -> from.plusSeconds(seconds(duration, 86_400));
            case HOURS -> from.plusSeconds(seconds(duration, 3_600));
            case MINUTES -> from.plusSeconds(seconds(duration, 60));
        };
    }

    /** to 之前一段时长（到期前的提醒） */
    Instant minus(Duration duration, Instant to) {
        return plus(new Duration(duration.unit(), duration.amount().negate()), to);
    }

    private static long seconds(Duration duration, int unit) {
        return duration.amount().multiply(BigDecimal.valueOf(unit)).longValue();
    }

    /** 时刻（带 Z 或时区）、本地时间（按日历的时区）、日期（那天结束）；都不是时为 null */
    private Instant moment(String text) {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            // 不是带时区的时刻，往下试
        }
        try {
            return LocalDateTime.parse(text).atZone(calendar.zone()).toInstant();
        } catch (DateTimeParseException ignored) {
            // 不是本地时间，往下试
        }
        try {
            return LocalDate.parse(text).plusDays(1).atStartOfDay(calendar.zone()).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
