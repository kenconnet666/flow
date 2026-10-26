-- ============================================================================
-- 作业（0007 的 3.5）：定时节点、待办的超时、提醒、无人认领，以后的异步动作也放这里。
-- 工作进程替所有租户干活：领取的索引不以租户开头；成功的作业直接删掉，表里只留等着的和失败的。
-- ============================================================================

CREATE TABLE flow_job (
    tenant_id    BIGINT       NOT NULL DEFAULT 0 COMMENT '租户（做作业时按它切换租户）',
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    kind         VARCHAR(32)  NOT NULL COMMENT '种类：timer 定时节点 / overdue 超时 / remind 提醒 / unclaimed 无人认领 / 接入方注册的种类',
    instance_id  BIGINT       NOT NULL COMMENT '实例：实例结束时它的作业一起删掉',
    work_item_id BIGINT       NULL COMMENT '相关的待办；到时候待办已经办完的作业直接跳过',
    due_at       DATETIME(3)  NOT NULL COMMENT '什么时候做',
    payload      JSON         NULL COMMENT '作业自己的数据：定时节点是哪个、第几个提醒、提醒过几次',
    state        VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT '状态：pending 等着做 / failed 重试用完（成功的直接删掉）',
    attempts     INT          NOT NULL DEFAULT 0 COMMENT '失败过几次',
    max_attempts INT          NOT NULL DEFAULT 3 COMMENT '最多试几次',
    locked_by    VARCHAR(128) NULL COMMENT '哪个工作进程领了它',
    locked_until DATETIME(3)  NULL COMMENT '领到什么时候：过了还没做完（进程挂了）别的进程可以再领',
    last_error   TEXT         NULL COMMENT '上一次失败的原因',
    created_at   DATETIME(3)  NOT NULL COMMENT '建的时间',
    PRIMARY KEY (id),
    KEY ix_flow_job_due (state, due_at),
    KEY ix_flow_job_instance (instance_id),
    KEY ix_flow_job_work_item (work_item_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '作业：到时候要做的事，工作进程用普通读加带条件的更新领取（0001 第 4 条）';
