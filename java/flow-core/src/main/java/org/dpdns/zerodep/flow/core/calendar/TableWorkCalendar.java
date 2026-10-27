package org.dpdns.zerodep.flow.core.calendar;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import org.dpdns.zerodep.flow.core.entity.CalendarDayEntity;
import org.dpdns.zerodep.flow.core.entity.CalendarEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 按表里的工作日历算（{@code flow_calendar}、{@code flow_calendar_day}，管理端维护）：一周哪几天上班，加上节假日、调休。
 * 流程定义里写的日历名字找不到时（一份都没建），按周一到周五、默认时区算。
 */
public class TableWorkCalendar implements WorkCalendar {
    private final EasyEntityQuery db;
    private final ZoneId defaultZone;

    public TableWorkCalendar(EasyEntityQuery db, ZoneId defaultZone) {
        this.db = db;
        this.defaultZone = defaultZone;
    }

    @Override
    public ZoneId zone() { return defaultZone; }

    @Override
    public Instant plusWorkdays(Instant from, BigDecimal days, String calendar) {
        String name = calendar == null ? CalendarEntity.DEFAULT : calendar;
        CalendarEntity found = db.queryable(CalendarEntity.class).include(c -> c.days()).where(c -> c.name().eq(name)).firstOrNull();
        if (found == null) return WorkCalendar.weekdays(defaultZone).plusWorkdays(from, days, calendar);

        ZoneId zone = ZoneId.of(found.getZone());
        Set<Integer> workweek = Arrays.stream(found.getWorkweek().split(",")).map(String::trim).filter(s -> !s.isEmpty())
            .map(Integer::valueOf).collect(Collectors.toSet());
        Map<LocalDate, Boolean> special = found.getDays().stream()
            .collect(Collectors.toMap(CalendarDayEntity::getDay, CalendarDayEntity::getWorkday, (a, b) -> b));

        int whole = days.setScale(0, RoundingMode.DOWN).intValueExact();
        int step = Integer.signum(whole);
        ZonedDateTime at = from.atZone(zone);
        for (int left = Math.abs(whole); left > 0; ) {
            at = at.plusDays(step);
            LocalDate day = at.toLocalDate();
            if (special.getOrDefault(day, workweek.contains(day.getDayOfWeek().getValue()))) left--;
        }
        long fractionSeconds = days.subtract(BigDecimal.valueOf(whole)).multiply(BigDecimal.valueOf(86_400)).longValue();
        return at.toInstant().plusSeconds(fractionSeconds);
    }
}
