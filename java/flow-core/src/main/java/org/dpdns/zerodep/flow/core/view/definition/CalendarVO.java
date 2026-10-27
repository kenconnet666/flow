package org.dpdns.zerodep.flow.core.view.definition;

import com.easy.query.core.annotation.Navigate;
import com.easy.query.core.enums.RelationTypeEnum;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 一份工作日历，连同节假日与调休：按 {@code selectAutoInclude(CalendarVO.class)} 读出 */
@Data
public class CalendarVO {
    private String name;
    private String zone;
    /** 一周哪几天上班：1 是周一，7 是周日，逗号分隔 */
    private String workweek;
    /** 节假日与调休，按日子排 */
    @Navigate(value = RelationTypeEnum.OneToMany)
    private List<Day> days;

    /** 一个特殊日子 */
    @Data
    public static class Day {
        private LocalDate day;
        /** 这天上不上班 */
        private Boolean workday;
        private String note;
    }
}
