-- ============================================================================
-- 命令的幂等（0006 第 1 节、0007 的 flow_request）：带 requestId（HTTP 的 Idempotency-Key）的命令做成后记下响应，
-- 同一个 requestId 再来时直接返回第一次的响应，不再做一遍。与命令在同一个事务里写，命令失败时不留。
-- ============================================================================

CREATE TABLE flow_request (
    tenant_id  BIGINT       NOT NULL DEFAULT 0 COMMENT '租户',
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    request_id VARCHAR(128) NOT NULL COMMENT '调用方给的请求编号（Idempotency-Key）',
    command    VARCHAR(64)  NOT NULL COMMENT '哪个命令：同一个编号用在别的命令上时报错',
    actor_id   BIGINT       NULL COMMENT '谁做的：同一个编号换了人时报错',
    response   MEDIUMTEXT   NOT NULL COMMENT '第一次的响应（原样返回）',
    created_at DATETIME(3)  NOT NULL COMMENT '时间（过期的记录可以定期删掉）',
    PRIMARY KEY (id),
    UNIQUE KEY ux_flow_request (tenant_id, request_id),
    KEY ix_flow_request_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '命令的幂等记录：同一个请求编号只做一次';
