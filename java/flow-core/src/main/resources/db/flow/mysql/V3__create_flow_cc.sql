-- ============================================================================
-- 抄送（0007 的 3.4）：抄送节点、处理人手动抄送，每个收到的人一行；"抄送我的"按收到的人查，未读在前。
-- ============================================================================

CREATE TABLE flow_cc (
    tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户',
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    instance_id    BIGINT       NOT NULL COMMENT '实例',
    node           VARCHAR(64)  NULL COMMENT '抄送节点；处理人手动抄送时是他当时所在的节点，可以为空',
    node_name      VARCHAR(200) NULL COMMENT '节点名称（快照）',
    recipient_id   BIGINT       NOT NULL COMMENT '收到的人',
    recipient_name VARCHAR(200) NULL COMMENT '收到的人的名字（快照）',
    from_user_id   BIGINT       NULL COMMENT '谁抄送的；抄送节点时为空',
    from_user_name VARCHAR(200) NULL COMMENT '那个人的名字（快照）',
    message        VARCHAR(500) NULL COMMENT '附言',
    created_at     DATETIME(3)  NOT NULL COMMENT '抄送的时间',
    read_at        DATETIME(3)  NULL COMMENT '读的时间；没读时为空',
    PRIMARY KEY (id),
    KEY ix_flow_cc_recipient (tenant_id, recipient_id, read_at, created_at),
    KEY ix_flow_cc_instance (instance_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '抄送：每个收到的人一行，带已读';
