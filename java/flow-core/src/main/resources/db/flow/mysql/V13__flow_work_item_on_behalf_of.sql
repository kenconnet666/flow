-- ============================================================================
-- 休假代理（OrgDirectory.standIn）：原来的人不在岗时待办落到代理人，记下代谁处理——列表上显示"李四（代 张三）"，
-- 被代理的人也算参与过、能看这个实例。归档表同样加上（结构同原表，搬的时候按列名对上）。
-- ============================================================================

ALTER TABLE flow_work_item
    ADD COLUMN on_behalf_of_id   BIGINT       NULL COMMENT '代谁处理：原来的人不在岗，待办落到了代理人；不是代理时为空' AFTER assignee_name,
    ADD COLUMN on_behalf_of_name VARCHAR(200) NULL COMMENT '那个人的名字（快照）' AFTER on_behalf_of_id;

ALTER TABLE flow_work_item_archive
    ADD COLUMN on_behalf_of_id   BIGINT       NULL COMMENT '代谁处理：原来的人不在岗，待办落到了代理人；不是代理时为空' AFTER assignee_name,
    ADD COLUMN on_behalf_of_name VARCHAR(200) NULL COMMENT '那个人的名字（快照）' AFTER on_behalf_of_id;
