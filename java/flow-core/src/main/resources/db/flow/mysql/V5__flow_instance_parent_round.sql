-- ============================================================================
-- 调用流程（0005 的 4.2）：子实例记下父实例里调用它的节点是第几轮。同一个调用节点被退回、循环再到时，
-- 父实例只等这一轮的子实例，收 output 时也只收这一轮的。
-- ============================================================================

ALTER TABLE flow_instance
    ADD COLUMN parent_round INT NULL COMMENT '父实例里调用它的节点是第几轮' AFTER parent_node,
    ADD KEY ix_flow_instance_parent_node (parent_instance_id, parent_node, parent_round);
