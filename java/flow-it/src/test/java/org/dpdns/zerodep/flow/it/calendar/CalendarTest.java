package org.dpdns.zerodep.flow.it.calendar;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.calendar.TableWorkCalendar;
import org.dpdns.zerodep.flow.core.view.definition.CalendarVO;
import org.dpdns.zerodep.flow.it.support.LiveMysql;
import org.dpdns.zerodep.flow.it.support.TestOrg;
import org.dpdns.zerodep.flow.it.support.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 工作日历（flow_calendar）：一周哪几天上班，节假日不算、调休补班算；没建日历时周一到周五 */
@DisplayName("工作日历")
class CalendarTest {
    private static final TestUser ADMIN = TestUser.of(1000L, "管理员");

    private EasyEntityQuery db;
    private FlowEngine<TestUser> engine;

    @BeforeEach
    void setUp() {
        LiveMysql.recreateTables();
        db = LiveMysql.easyQuery();
        engine = FlowEngine.<TestUser>builder(db).orgDirectory(new TestOrg(List.of(ADMIN))).build();
    }

    private static CalendarVO.Day day(String date, boolean workday, String note) {
        CalendarVO.Day day = new CalendarVO.Day();
        day.setDay(LocalDate.parse(date));
        day.setWorkday(workday);
        day.setNote(note);
        return day;
    }

    @Test
    void 跳过节假日_算上调休() {
        TableWorkCalendar calendar = new TableWorkCalendar(db, ZoneOffset.UTC);
        // 2026-10-02 是周五：没建日历时下一个工作日是周一 10-05
        Instant friday = Instant.parse("2026-10-02T02:00:00Z");
        assertEquals(Instant.parse("2026-10-05T02:00:00Z"), calendar.plusWorkdays(friday, BigDecimal.ONE, null));

        CalendarVO saved = new CalendarVO();
        saved.setName("default");
        saved.setZone("Asia/Shanghai");
        saved.setWorkweek("1,2,3,4,5");
        // 周一 10-05 放假，周六 10-03 调休补班
        saved.setDays(List.of(day("2026-10-05", false, "国庆"), day("2026-10-03", true, "调休")));
        CalendarVO back = engine.saveCalendar(ADMIN, saved);
        assertEquals(2, back.getDays().size());
        assertEquals("2026-10-03", back.getDays().get(0).getDay().toString());

        assertEquals(Instant.parse("2026-10-03T02:00:00Z"), calendar.plusWorkdays(friday, BigDecimal.ONE, null));
        assertEquals(Instant.parse("2026-10-06T02:00:00Z"), calendar.plusWorkdays(friday, BigDecimal.valueOf(2), null));
        assertEquals(1, engine.calendars(ADMIN).size());

        engine.deleteCalendar(ADMIN, "default");
        assertEquals(Instant.parse("2026-10-05T02:00:00Z"), calendar.plusWorkdays(friday, BigDecimal.ONE, null));
    }
}
