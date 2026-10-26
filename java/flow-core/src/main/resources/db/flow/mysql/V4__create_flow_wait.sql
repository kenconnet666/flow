-- ============================================================================
-- 等外部事件（0007 的 flow_wait，0005 的 4.3）：实例停在等待节点上时一行，按事件名加实例或业务编号找到它；
-- 事件到了带条件地删掉这一行（删到 0 行就是已经处理过），再推进。
-- ============================================================================

CREATE TABLE flow_wait (
    tenant_id    BIGINT       NOT NULL DEFAULT 0 COMMENT '租户',
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    instance_id  BIGINT       NOT NULL COMMENT '实例',
    node         VARCHAR(64)  NOT NULL COMMENT '等待节点',
    event        VARCHAR(128) NOT NULL COMMENT '等的事件名',
    business_key VARCHAR(128) NULL COMMENT '实例的业务单据编号（从实例抄来：业务系统按单号送事件）',
    created_at   DATETIME(3)  NOT NULL COMMENT '开始等的时间',
    PRIMARY KEY (id),
    KEY ix_flow_wait_event (tenant_id, event, business_key),
    KEY ix_flow_wait_instance (instance_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '等外部事件：实例停在等待节点上时一行';
