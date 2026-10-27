-- ============================================================================
-- 设计中的草稿（0004 的 5.2、0007 的 3.1）：流程定义与表单共用，每个流程、表单一行。设计器自动保存到这里，
-- 版本列防两个人互相覆盖；发布成功后删掉。内容是设计器自己的状态（流程是定义的 JSON，表单是源码等），引擎不解释。
-- ============================================================================

CREATE TABLE flow_draft (
    tenant_id       BIGINT       NOT NULL DEFAULT 0 COMMENT '租户',
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    kind            VARCHAR(16)  NOT NULL COMMENT 'process 流程定义 / form 表单',
    target_key      VARCHAR(64)  NOT NULL COMMENT '流程或表单的标识',
    name            VARCHAR(200) NULL COMMENT '名称',
    content         MEDIUMTEXT   NOT NULL COMMENT '设计器的内容：流程是定义的 JSON，表单是源码与清单',
    base_version    INT          NULL COMMENT '基于哪个已发布的版本改的；新建的为空',
    rev             INT          NOT NULL DEFAULT 0 COMMENT '版本列：保存时带上读到的版本，别人先存了就报冲突',
    updated_by      BIGINT       NULL COMMENT '最后修改人',
    updated_by_name VARCHAR(200) NULL COMMENT '最后修改人的名字（快照）：打开别人的草稿时提示',
    updated_at      DATETIME(3)  NOT NULL COMMENT '最后修改的时间',
    PRIMARY KEY (id),
    UNIQUE KEY ux_flow_draft_target (tenant_id, kind, target_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '设计中的草稿：流程定义与表单共用';
