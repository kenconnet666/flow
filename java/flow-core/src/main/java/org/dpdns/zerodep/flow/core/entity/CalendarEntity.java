package org.dpdns.zerodep.flow.core.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Navigate;
import com.easy.query.core.annotation.OrderByProperty;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.enums.RelationTypeEnum;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.dpdns.zerodep.flow.core.entity.proxy.CalendarEntityProxy;

import java.time.Instant;
import java.util.List;

/** 工作日历（{@code flow_calendar}）：名字、时区、一周哪几天上班，连同节假日与调休（导航 {@code days}） */
@Getter
@Setter
@FieldNameConstants
@Table("flow_calendar")
@EntityProxy
public class CalendarEntity implements ProxyEntityAvailable<CalendarEntity, CalendarEntityProxy>, Tenanted, Touched {
    /** 流程定义没写 settings.calendar 时用的日历 */
    public static final String DEFAULT = "default";

    @Column(primaryKey = true, generatedKey = true)
    private Long id;
    private Long tenantId;
    private String name;
    /** 时区（Asia/Shanghai） */
    private String zone;
    /** 一周哪几天上班：1 是周一，7 是周日，逗号分隔 */
    private String workweek;
    private Instant updatedAt;

    // ───────── 关系 ─────────

    /** 节假日与调休，按日子排 */
    @Navigate(value = RelationTypeEnum.OneToMany, selfProperty = Fields.id, targetProperty = CalendarDayEntity.Fields.calendarId,
        orderByProps = {@OrderByProperty(property = CalendarDayEntity.Fields.day)})
    private List<CalendarDayEntity> days;
}
