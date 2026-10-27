package org.dpdns.zerodep.flow.core.engine.handler;

import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.FlowErrors;
import org.dpdns.zerodep.flow.core.entity.CalendarDayEntity;
import org.dpdns.zerodep.flow.core.entity.CalendarEntity;
import org.dpdns.zerodep.flow.core.view.definition.CalendarVO;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;

/** 工作日历的维护（管理端）：列出、整份保存（特殊日子整份换掉）、删除 */
public class CalendarHandler {
    private final EngineContext context;

    public CalendarHandler(EngineContext context) {
        this.context = context;
    }

    /** 全部日历，按名字排 */
    public List<CalendarVO> list() {
        return context.getDb().queryable(CalendarEntity.class).orderBy(c -> c.name().asc()).selectAutoInclude(CalendarVO.class).toList();
    }

    /** 保存一份日历：没有时建上，有时换掉时区、工作周与全部特殊日子 */
    public CalendarVO save(CalendarVO calendar) {
        if (calendar.getName() == null || calendar.getName().isBlank()) throw FlowErrors.invalid("name", "required");
        try {
            ZoneId.of(calendar.getZone());
        } catch (DateTimeException | NullPointerException e) {
            throw FlowErrors.invalid("zone", "unknown-zone");
        }
        if (calendar.getWorkweek() == null || !calendar.getWorkweek().matches("\\s*([1-7]\\s*(,\\s*[1-7]\\s*)*)?")) throw FlowErrors.invalid("workweek", "invalid");
        return context.transaction(() -> {
            CalendarEntity row = context.getDb().queryable(CalendarEntity.class).where(c -> c.name().eq(calendar.getName())).firstOrNull();
            if (row == null) {
                row = new CalendarEntity();
                row.setName(calendar.getName());
            }
            row.setZone(calendar.getZone());
            row.setWorkweek(calendar.getWorkweek().replace(" ", ""));
            if (row.getId() == null) context.getDb().insertable(row).executeRows(true);
            else context.getDb().updatable(row).executeRows();
            Long id = row.getId();
            context.getDb().deletable(CalendarDayEntity.class).allowDeleteStatement(true).where(d -> d.calendarId().eq(id)).executeRows();
            List<CalendarDayEntity> days = (calendar.getDays() == null ? List.<CalendarVO.Day>of() : calendar.getDays()).stream().map(d -> {
                CalendarDayEntity day = new CalendarDayEntity();
                day.setCalendarId(id);
                day.setDay(d.getDay());
                day.setWorkday(Boolean.TRUE.equals(d.getWorkday()));
                day.setNote(d.getNote());
                return day;
            }).toList();
            if (!days.isEmpty()) context.getDb().insertable(days).executeRows();
            return context.getDb().queryable(CalendarEntity.class).whereById(id).selectAutoInclude(CalendarVO.class).singleNotNull();
        });
    }

    /** 删掉一份日历（连同特殊日子）；用它的流程之后按周一到周五算 */
    public void delete(String name) {
        context.transaction(() -> {
            CalendarEntity row = context.getDb().queryable(CalendarEntity.class).where(c -> c.name().eq(name)).firstOrNull();
            if (row == null) return null;
            Long id = row.getId();
            context.getDb().deletable(CalendarDayEntity.class).allowDeleteStatement(true).where(d -> d.calendarId().eq(id)).executeRows();
            context.getDb().deletable(CalendarEntity.class).allowDeleteStatement(true).where(c -> c.id().eq(id)).executeRows();
            return null;
        });
    }
}
