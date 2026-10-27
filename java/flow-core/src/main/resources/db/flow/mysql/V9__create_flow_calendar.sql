-- ============================================================================
-- 工作日历（0002 §7、0005 的 2.1 calendar）：每份日历一行（名字、时区、一周哪几天上班），节假日与调休各一行特殊日子。
-- 流程定义里 settings.calendar 写日历的名字，不写时用 default；一份都没有时按周一到周五算。
-- ============================================================================

CREATE TABLE flow_calendar (
    tenant_id  BIGINT       NOT NULL DEFAULT 0 COMMENT '租户',
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    name       VARCHAR(64)  NOT NULL COMMENT '日历的名字：流程定义里 settings.calendar 写它，默认 default',
    zone       VARCHAR(64)  NOT NULL COMMENT '时区：哪一天按它算（Asia/Shanghai）',
    workweek   VARCHAR(16)  NOT NULL DEFAULT '1,2,3,4,5' COMMENT '一周哪几天上班：1 是周一，7 是周日',
    updated_at DATETIME(3)  NOT NULL COMMENT '最后修改的时间',
    PRIMARY KEY (id),
    UNIQUE KEY ux_flow_calendar_name (tenant_id, name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '工作日历';

CREATE TABLE flow_calendar_day (
    tenant_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户',
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    calendar_id BIGINT       NOT NULL COMMENT '哪份日历',
    day         DATE         NOT NULL COMMENT '日子',
    workday     BOOLEAN      NOT NULL COMMENT '这天上不上班：节假日为假，调休补班为真',
    note        VARCHAR(200) NULL COMMENT '说明（国庆、调休）',
    PRIMARY KEY (id),
    UNIQUE KEY ux_flow_calendar_day (calendar_id, day)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '工作日历的特殊日子：节假日、调休';
