-- ============================================================================
-- 归档表（0007 第 6 节，默认不用）：结束超过一段时间的实例连同待办、流转记录、表单的值、抄送整批搬过来，热表一直小。
-- 结构同原表（CREATE TABLE ... LIKE，连索引一起），多一列 archived_at。搬的时候按列名对上（Archiver 读 information_schema），
-- 以后原表加列时归档表同样加上即可，列的先后不要紧。
-- ============================================================================

CREATE TABLE flow_instance_archive LIKE flow_instance;
ALTER TABLE flow_instance_archive
    ADD COLUMN archived_at DATETIME(3) NOT NULL COMMENT '搬进归档表的时间',
    COMMENT = '归档的实例：结构同 flow_instance';

CREATE TABLE flow_work_item_archive LIKE flow_work_item;
ALTER TABLE flow_work_item_archive
    ADD COLUMN archived_at DATETIME(3) NOT NULL COMMENT '搬进归档表的时间',
    COMMENT = '归档的待办：结构同 flow_work_item';

CREATE TABLE flow_event_archive LIKE flow_event;
ALTER TABLE flow_event_archive
    ADD COLUMN archived_at DATETIME(3) NOT NULL COMMENT '搬进归档表的时间',
    COMMENT = '归档的流转记录：结构同 flow_event';

CREATE TABLE flow_form_data_archive LIKE flow_form_data;
ALTER TABLE flow_form_data_archive
    ADD COLUMN archived_at DATETIME(3) NOT NULL COMMENT '搬进归档表的时间',
    COMMENT = '归档的表单的值：结构同 flow_form_data';

CREATE TABLE flow_cc_archive LIKE flow_cc;
ALTER TABLE flow_cc_archive
    ADD COLUMN archived_at DATETIME(3) NOT NULL COMMENT '搬进归档表的时间',
    COMMENT = '归档的抄送：结构同 flow_cc';
