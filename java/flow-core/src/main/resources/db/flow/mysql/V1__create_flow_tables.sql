-- ============================================================================
-- 流程引擎的表（MySQL 8 / PolarDB MySQL 8.0）。设计与取舍见 docs/decisions/0007。
--
-- 怎样用：
--   引擎不自己建表。这份脚本交给 Flyway（推荐）或 DBA 执行。用 Flyway 时建议单独一套迁移：
--   位置 classpath:db/flow/mysql，历史表 flow_schema_history，和应用自己的迁移互不干扰（见 0007 第 9 节）。
--   以后的变更另写 V2__…、V3__…，已经执行过的脚本不再改。
--
-- 这一版：最小链路（发布、发起、认领、处理、撤回）用到的表。作业、外部事件的等待、抄送、工作日历、草稿、
--   命令的幂等记录、归档表随用到它们的命令加新脚本。
--
-- 约定：
--   - 主键是数据库自增的 BIGINT（引擎里是 Java 的 long，0006 的 6.1）；指向别的表的编号列也是 BIGINT。
--   - 接入方的用户、部门、角色编号与租户编号也是 BIGINT（一般就是接入方那几张表的自增主键）；名字存快照（人离职、改名后记录照样能看）。
--   - 流程标识、表单标识、节点标识、业务单据编号是人起的名字或业务上的编号，用 VARCHAR。
--   - 时间都是 UTC 的 DATETIME(3)（毫秒）；JSON 用 MySQL 的 JSON 类型。只有流程定义与字段清单例外，存成 MEDIUMTEXT 的 JSON 文字：
--     它们是人写的文档，要按发布时的原样存，而 JSON 类型会把对象的键按长度重排（字段清单的先后就是表单上的先后）。
--   - 每张表都有租户列 tenant_id（不用多租户时是 0），索引以它开头；不建外键。
--   - 会变的行带 rev（乐观锁版本），只追加的表不带。
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 定义：流程本身 + 发布的不可变版本
-- ---------------------------------------------------------------------------

CREATE TABLE flow_process (
    tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户；不用多租户时是 0',
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    process_key    VARCHAR(64)  NOT NULL COMMENT '流程标识：同一流程的各版本相同（key 是 MySQL 的保留字，所以不用它做列名）',
    name           VARCHAR(200) NOT NULL COMMENT '最新版本的名称，列表用',
    category       VARCHAR(64)  NULL COMMENT '分类，列表筛选用',
    status         VARCHAR(16)  NOT NULL COMMENT '状态：active 启用 / disabled 停用（停用后不能新发起，进行中的照常走完）',
    latest_version INT          NOT NULL COMMENT '最新发布的版本号',
    rev            INT          NOT NULL COMMENT '乐观锁版本：两个人同时发布时后到的失败',
    created_at     DATETIME(3)  NOT NULL COMMENT '第一次发布的时间',
    updated_at     DATETIME(3)  NOT NULL COMMENT '最后一次发布或启停的时间',
    updated_by     BIGINT       NULL COMMENT '最后一次发布或启停的人',
    PRIMARY KEY (id),
    UNIQUE KEY ux_flow_process_key (tenant_id, process_key),
    KEY ix_flow_process_category (tenant_id, category)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '流程本身：每个流程标识一行，记启用停用与最新版本';

CREATE TABLE flow_definition (
    tenant_id         BIGINT       NOT NULL DEFAULT 0 COMMENT '租户',
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号：实例上的 definition_id 指向它',
    process_key       VARCHAR(64)  NOT NULL COMMENT '流程标识',
    version           INT          NOT NULL COMMENT '版本号，从 1 开始，由后端给',
    model             MEDIUMTEXT   NOT NULL COMMENT '整份定义（docs/decisions/0005 的 JSON 文字，含流程图布局），按发布时的原样存',
    form_key          VARCHAR(64)  NULL COMMENT '用的表单标识（从 model 抄出来，查"哪些流程用了这张表单"用）；流程没有表单时为空',
    form_version      INT          NULL COMMENT '用的表单版本：发布时定下，之后表单再发布不影响这个流程版本',
    note              VARCHAR(500) NULL COMMENT '发布说明',
    published_by      BIGINT       NULL COMMENT '发布人',
    published_by_name VARCHAR(200) NULL COMMENT '发布人的名字（快照）',
    published_at      DATETIME(3)  NOT NULL COMMENT '发布时间',
    PRIMARY KEY (id),
    UNIQUE KEY ux_flow_definition_version (tenant_id, process_key, version),
    KEY ix_flow_definition_form (tenant_id, form_key, form_version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '流程发布的版本：发布后不能改，进行中的实例一直按自己的版本走';


-- ---------------------------------------------------------------------------
-- 表单：表单本身 + 发布的不可变版本 + 实例上表单的值（0004 的 5.2）
-- ---------------------------------------------------------------------------

CREATE TABLE flow_form (
    tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户',
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    form_key       VARCHAR(64)  NOT NULL COMMENT '表单标识',
    name           VARCHAR(200) NOT NULL COMMENT '最新版本的名称',
    category       VARCHAR(64)  NULL COMMENT '分类',
    status         VARCHAR(16)  NOT NULL COMMENT '状态：active 启用 / disabled 停用',
    latest_version INT          NOT NULL COMMENT '最新发布的版本号',
    rev            INT          NOT NULL COMMENT '乐观锁版本',
    created_at     DATETIME(3)  NOT NULL COMMENT '第一次发布的时间',
    updated_at     DATETIME(3)  NOT NULL COMMENT '最后一次发布或启停的时间',
    updated_by     BIGINT       NULL COMMENT '最后一次发布或启停的人',
    PRIMARY KEY (id),
    UNIQUE KEY ux_flow_form_key (tenant_id, form_key),
    KEY ix_flow_form_category (tenant_id, category)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '表单本身：每个表单标识一行';

CREATE TABLE flow_form_version (
    tenant_id         BIGINT       NOT NULL DEFAULT 0 COMMENT '租户',
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号：表单的值上的 form_version_id 指向它',
    form_key          VARCHAR(64)  NOT NULL COMMENT '表单标识',
    version           INT          NOT NULL COMMENT '版本号，从 1 开始',
    client_source     MEDIUMTEXT   NULL COMMENT '前端源码（TSX，0004 的 1.1）',
    client_compiled   MEDIUMTEXT   NULL COMMENT '前端源码的编译结果：浏览器直接加载它',
    compiler_version  VARCHAR(32)  NULL COMMENT '编译它的编译器版本（编译器升级后按需重新编译，0004 的 5.4）',
    manifest          MEDIUMTEXT   NOT NULL COMMENT '字段清单的 JSON 文字：字段名、类型、必填、显示条件（引擎按它校验与合并提交的值；字段的先后就是表单上的先后）',
    modules           JSON         NULL COMMENT '前端源码用到的模块',
    server_language   VARCHAR(8)   NULL COMMENT '后端代码的语言：java / ts；没有后端代码时为空',
    server_source     MEDIUMTEXT   NULL COMMENT '后端源码（0004 的 1.5，启动时加载）',
    note              VARCHAR(500) NULL COMMENT '发布说明',
    published_by      BIGINT       NULL COMMENT '发布人',
    published_by_name VARCHAR(200) NULL COMMENT '发布人的名字（快照）',
    reviewed_by       BIGINT       NULL COMMENT '审阅人：打开双人发布时才有（0004 的 6.2）',
    published_at      DATETIME(3)  NOT NULL COMMENT '发布时间',
    PRIMARY KEY (id),
    UNIQUE KEY ux_flow_form_version (tenant_id, form_key, version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '表单发布的版本：发布后不能改';

CREATE TABLE flow_form_data (
    tenant_id       BIGINT      NOT NULL DEFAULT 0 COMMENT '租户',
    instance_id     BIGINT      NOT NULL COMMENT '实例：每个实例一行',
    form_version_id BIGINT      NOT NULL COMMENT '填的是哪个表单版本',
    data            JSON        NOT NULL COMMENT '表单的值：字段名 → 值',
    rev             INT         NOT NULL COMMENT '乐观锁版本：每次写回加一',
    updated_at      DATETIME(3) NOT NULL COMMENT '最后一次写回的时间',
    PRIMARY KEY (instance_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '实例上表单的值：发起时写第一份，每次处理按节点上的字段权限合并后写回';


-- ---------------------------------------------------------------------------
-- 运行：实例、待办、候选、流转记录
-- ---------------------------------------------------------------------------

CREATE TABLE flow_instance (
    tenant_id          BIGINT       NOT NULL DEFAULT 0 COMMENT '租户',
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    process_key        VARCHAR(64)  NOT NULL COMMENT '流程标识',
    definition_id      BIGINT       NOT NULL COMMENT '按哪个发布版本走（flow_definition.id）',
    version            INT          NOT NULL COMMENT '那个版本的版本号',
    business_key       VARCHAR(128) NULL COMMENT '业务单据编号；同一个编号可以有多个实例（重新提交）',
    title              VARCHAR(500) NULL COMMENT '实例标题：按定义的模板生成，每次提交重算',
    priority           TINYINT      NOT NULL DEFAULT 0 COMMENT '紧急程度：0 是常规，越大越急；抄到待办上排序用',
    summary            JSON         NULL COMMENT '待办列表的摘要列：{ 引用名: 值 }，每次提交重算',
    status             VARCHAR(16)  NOT NULL COMMENT '状态：active 进行中 / suspended 暂停 / completed 走到结束 / terminated 终止 / withdrawn 撤回',
    result             VARCHAR(64)  NULL COMMENT '结束结果：结束节点或终止时给的，如 approved、rejected、withdrawn',
    current            JSON         NULL COMMENT '正在处理的节点：[{ node, name }]，列表显示用；结束后是空数组',
    variables          JSON         NULL COMMENT '流程变量：{ 名字: 值 }，连线上的副作用改它',
    state              JSON         NULL COMMENT '运行状态（引擎内部用，不给前端）：各节点轮次，以后还有并行令牌、汇合计数',
    suspension         JSON         NULL COMMENT '暂停时：原因、恢复条件、谁、何时',
    parent_instance_id BIGINT       NULL COMMENT '调用流程时的父实例',
    parent_node        VARCHAR(64)  NULL COMMENT '父实例里调用它的节点',
    root_instance_id   BIGINT       NULL COMMENT '最外层的实例：多层嵌套时整棵一起查、一起终止',
    source_instance_id BIGINT       NULL COMMENT '由别的流程发起时，来源实例（追溯用）',
    source_node        VARCHAR(64)  NULL COMMENT '来源实例里发起它的节点',
    started_by         BIGINT       NULL COMMENT '发起人',
    started_by_name    VARCHAR(200) NULL COMMENT '发起人的名字（快照）',
    started_at         DATETIME(3)  NULL COMMENT '发起时间',
    ended_at           DATETIME(3)  NULL COMMENT '结束时间；进行中时为空',
    rev                INT          NOT NULL COMMENT '版本：每次推进先把它加一，等于锁住这一行，并发的推进排队',
    updated_at         DATETIME(3)  NOT NULL COMMENT '最后一次推进的时间',
    PRIMARY KEY (id),
    KEY ix_flow_instance_status (tenant_id, status, process_key, started_at),
    KEY ix_flow_instance_starter (tenant_id, started_by, started_at),
    KEY ix_flow_instance_business (tenant_id, business_key),
    KEY ix_flow_instance_parent (parent_instance_id),
    KEY ix_flow_instance_root (root_instance_id),
    KEY ix_flow_instance_ended (tenant_id, ended_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '流程实例：发起一次一行';

CREATE TABLE flow_work_item (
    tenant_id         BIGINT       NOT NULL DEFAULT 0 COMMENT '租户',
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    kind              VARCHAR(16)  NOT NULL DEFAULT 'task' COMMENT '种类：task 普通待办 / failure 自动节点重试用完后给管理员的异常处理',
    instance_id       BIGINT       NOT NULL COMMENT '实例',
    process_key       VARCHAR(64)  NOT NULL COMMENT '流程标识（从实例抄来：按流程筛待办不用连实例表）',
    priority          TINYINT      NOT NULL DEFAULT 0 COMMENT '紧急程度（从实例抄来，排序用）',
    node              VARCHAR(64)  NOT NULL COMMENT '节点标识',
    node_name         VARCHAR(200) NULL COMMENT '节点名称（快照）',
    round             INT          NOT NULL COMMENT '这个节点第几轮：退回、循环时会再到同一个节点',
    state             VARCHAR(16)  NOT NULL COMMENT '状态：open 等着处理 / waiting 等加签、委派的人办完 / done 处理完 / canceled 取消',
    assignment        VARCHAR(16)  NOT NULL COMMENT '落到谁：pool 收件箱里等认领 / claimed 认领了 / direct 直接落到人 / designated 负责人指定',
    arrival           VARCHAR(16)  NOT NULL COMMENT '怎样来的：start / forward / return / escalate / transfer / delegate / addSign / jump',
    inbox_kind        VARCHAR(32)  NULL COMMENT '收件箱的种类：role / department / 接入方扩展的种类；不在收件箱里时为空（显示用，认领按候选表）',
    inbox_id          BIGINT       NULL COMMENT '收件箱是哪个角色、部门',
    assignee_id       BIGINT       NULL COMMENT '当前处理人；在收件箱里等认领时为空',
    assignee_name     VARCHAR(200) NULL COMMENT '处理人的名字（快照）',
    from_node         VARCHAR(64)  NULL COMMENT '从哪个节点来',
    from_user_id      BIGINT       NULL COMMENT '从谁那里来（上一步的处理人、转办人）',
    from_user_name    VARCHAR(200) NULL COMMENT '那个人的名字（快照）',
    from_item_id      BIGINT       NULL COMMENT '从哪个待办来',
    sign_group        BIGINT       NULL COMMENT '会签：同一节点同一轮的参与人共用一个编号',
    sign_seq          INT          NULL COMMENT '依次会签的顺序',
    weight            INT          NULL COMMENT '票签的权重',
    parent_item_id    BIGINT       NULL COMMENT '委派、加签生成的子待办指向原来的待办',
    add_mode          VARCHAR(8)   NULL COMMENT '加签的方式：before 前加签 / after 后加签 / parallel 并加',
    due_at            DATETIME(3)  NULL COMMENT '截止时间',
    claimed_at        DATETIME(3)  NULL COMMENT '认领时间',
    created_at        DATETIME(3)  NOT NULL COMMENT '到达时间',
    read_at           DATETIME(3)  NULL COMMENT '处理人第一次打开的时间：列表里标"新"',
    remind_count      INT          NOT NULL DEFAULT 0 COMMENT '被催办的次数',
    last_reminded_at  DATETIME(3)  NULL COMMENT '上一次催办的时间（催办的频率限制用）',
    needs_designation BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '无人认领超时，等收件箱负责人指定',
    designator_id     BIGINT       NULL COMMENT '等谁来指定',
    reassign_request  JSON         NULL COMMENT '申请改派：谁、原因、何时',
    draft             JSON         NULL COMMENT '暂存：表单的值与意见，还没提交',
    outcome           VARCHAR(64)  NULL COMMENT '处理结果（节点上的一个结果）',
    comment           TEXT         NULL COMMENT '意见',
    attachments       JSON         NULL COMMENT '附件',
    completed_by      BIGINT       NULL COMMENT '谁处理的',
    completed_by_name VARCHAR(200) NULL COMMENT '处理人的名字（快照）',
    completed_at      DATETIME(3)  NULL COMMENT '处理时间；取消的待办是取消的时间',
    rev               INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本：认领、处理都是带条件的更新',
    PRIMARY KEY (id),
    KEY ix_flow_work_item_assignee (tenant_id, assignee_id, state, created_at),
    KEY ix_flow_work_item_completed (tenant_id, completed_by, completed_at),
    KEY ix_flow_work_item_instance (instance_id, state),
    KEY ix_flow_work_item_sign (sign_group),
    KEY ix_flow_work_item_parent (parent_item_id),
    KEY ix_flow_work_item_designator (tenant_id, designator_id, needs_designation)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '待办：一张表加状态（不拆运行表和历史表），已办也查这张表';

CREATE TABLE flow_candidate (
    tenant_id    BIGINT      NOT NULL DEFAULT 0 COMMENT '租户',
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '编号',
    work_item_id BIGINT      NOT NULL COMMENT '待办',
    kind         VARCHAR(32) NOT NULL COMMENT '种类：user 指定的人 / role 角色 / department 部门 / 接入方扩展的种类 / excluded 退回过收件箱、不能再认领的人',
    target_id    BIGINT      NOT NULL COMMENT '用户、角色、部门的编号（按 kind 区分是哪一种）',
    PRIMARY KEY (id),
    KEY ix_flow_candidate_match (tenant_id, kind, target_id, work_item_id),
    UNIQUE KEY ux_flow_candidate_item (work_item_id, kind, target_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '打开着的待办谁能处理、谁被排除：角色、部门存一行（不展开成人），待办结束时删掉';

CREATE TABLE flow_event (
    tenant_id    BIGINT       NOT NULL DEFAULT 0 COMMENT '租户',
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    instance_id  BIGINT       NOT NULL COMMENT '实例',
    work_item_id BIGINT       NULL COMMENT '相关的待办',
    type         VARCHAR(48)  NOT NULL COMMENT '事件种类：instance.started、workItem.completed、node.entered……（0006 第 5 节）',
    node         VARCHAR(64)  NULL COMMENT '节点标识',
    node_name    VARCHAR(200) NULL COMMENT '节点名称（快照：看记录不用再查定义）',
    edge         VARCHAR(64)  NULL COMMENT '走的连线',
    outcome      VARCHAR(64)  NULL COMMENT '处理结果、结束结果',
    instance_rev INT          NOT NULL COMMENT '写这条记录时实例的版本：同一实例的记录按它排出准确的先后，同一步写的几条相同',
    actor_id     BIGINT       NULL COMMENT '操作人；引擎自己推进时为空',
    actor_name   VARCHAR(200) NULL COMMENT '操作人的名字（快照）',
    at           DATETIME(3)  NOT NULL COMMENT '时间',
    comment      TEXT         NULL COMMENT '意见、原因',
    attachments  JSON         NULL COMMENT '附件',
    data         JSON         NULL COMMENT '附加的数据：退回的目标、改了哪些字段（旧值、新值）、事件带来的数据',
    PRIMARY KEY (id),
    KEY ix_flow_event_instance (instance_id, instance_rev, id),
    KEY ix_flow_event_actor (tenant_id, actor_id, type, at),
    KEY ix_flow_event_type (tenant_id, type, at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '流转记录：只追加不修改；每个事件一条';
