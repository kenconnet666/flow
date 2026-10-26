-- ============================================================================
-- 处理人撤回刚处理的一步（0006 的 3.3 withdrawStep）：每个实例记下最近一次处理之前的样子（运行状态、流程变量、表单的值），
-- 下一步还没人动时用它恢复。每个实例只留一行，下一次处理时换掉；实例结束时删掉。
-- ============================================================================

CREATE TABLE flow_step (
    tenant_id    BIGINT     NOT NULL DEFAULT 0 COMMENT '租户',
    id           BIGINT     NOT NULL AUTO_INCREMENT COMMENT '编号',
    instance_id  BIGINT     NOT NULL COMMENT '实例',
    work_item_id BIGINT     NOT NULL COMMENT '处理的是哪个待办',
    rev          INT        NOT NULL COMMENT '处理那一步时实例的版本：之后实例再被动过（版本变了）就不能撤回',
    state        JSON       NOT NULL COMMENT '处理之前的运行状态',
    variables    JSON       NOT NULL COMMENT '处理之前的流程变量',
    form         JSON       NULL COMMENT '处理之前的表单的值；流程没有表单时为空',
    PRIMARY KEY (id),
    UNIQUE KEY ux_flow_step_instance (instance_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '最近一次处理之前的快照：处理人撤回刚处理的一步时恢复';
