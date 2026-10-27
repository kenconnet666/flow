package org.dpdns.zerodep.flow.core.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.dpdns.zerodep.flow.core.entity.proxy.CalendarDayEntityProxy;

import java.time.LocalDate;

/** 工作日历的一个特殊日子（{@code flow_calendar_day}）：节假日不上班，调休补班要上班 */
@Getter
@Setter
@FieldNameConstants
@Table("flow_calendar_day")
@EntityProxy
public class CalendarDayEntity implements ProxyEntityAvailable<CalendarDayEntity, CalendarDayEntityProxy>, Tenanted {
    @Column(primaryKey = true, generatedKey = true)
    private Long id;
    private Long tenantId;
    private Long calendarId;
    private LocalDate day;
    /** 这天上不上班 */
    private Boolean workday;
    /** 说明（国庆、调休） */
    private String note;
}
