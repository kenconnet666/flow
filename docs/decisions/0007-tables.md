# 0007 表

- 状态：已定（用户 2026-10-26，逐节讨论定稿）。接替 0001 的"表的草案"，按 0004（表单）、0005（定义）、0006（接口）重新设计
- 相关：并发写法见 0001 的初步方向第 3、4 条（已定）；主键策略见 0006 的 6.1；参考了 warm-flow、FlowLong 的建表脚本与 Flowable 的表（结论见 0001 的"调研结论"）

## 1. 取舍（已定，用户 2026-10-26）

| # | 决定 | 理由 | 没选的做法 |
| --- | --- | --- | --- |
| 1 | **待办一张表加状态**，不拆运行表和历史表 | 已办、实例详情不用并两张表；处理一步不用把行搬来搬去；MySQL 上按复合索引查，几百万行也不慢 | FlowLong、Flowable 拆运行与历史：查询要并表，搬行是额外的写入和出错点 |
| 2 | **冷数据靠归档表**：结束超过 N 天的实例连同待办、流转记录、表单的值整批搬进 `*_archive` | 热表一直小；归档表结构相同，查询按需带上 | 一直留在热表（越用越慢）；只靠分区（PolarDB 的分区能力随小版本，按最旧基线不依赖） |
| 3 | **候选只在待办打开时存在**：待办处理完、取消时删掉它的候选 | 候选表一直很小，"可认领"的查询快；谁曾经能处理、谁处理了，流转记录里都有 | 留着全部候选：表膨胀，索引里全是没用的行 |
| 4 | **角色、部门（以及接入方扩展的种类）作为一行候选存**（不展开成人）；解析器、`{ ref }` 取到的人在到达时展开成人 | 角色收件箱属于角色：后来拿到这个角色、调进这个部门的人也能看到、认领（收件箱的本意）；查询时用操作人的角色、部门、扩展归属去匹配（`Actor.roles()`、`departments()`、`groups(种类)`，0006 第 2 节） | 全部展开成人：换了角色、调了部门的人，收件箱跟不上 |
| 5 | **运行状态放实例上的 JSON**（`state`：并行的令牌、汇合计数、各节点轮次、依次会签的名单） | 每一步本来就先锁实例行（0001 第 3 条），状态和实例在同一行里改，一致性最简单 | Flowable 的执行令牌树表：每一步插删多行，对审批流是过度设计 |
| 6 | **等待点各归其位**：人工节点 → 待办；定时、超时、提醒、异步动作 → 作业；外部事件 → 等待表；同步子流程 → 子实例上的父实例列 | 每种等待都有自己要查的方式（按人、按到期时间、按事件名、按父实例），各用各的索引 | 一张"等待"总表：索引顾此失彼 |
| 7 | **租户列每张表都有**（不用租户时是空串），索引都以它开头 | 表结构一种，打不打开多租户不用改表；常量开头的列不影响索引的使用 | 打开多租户才加列：两套表结构、两套索引 |
| 8 | **名字存快照**：处理人、发起人、操作人的名字和编号一起存 | 历史记录不依赖组织架构：人离职、改名后记录照样能看；列表不用每行去查人名 | 只存编号、显示时查：历史会变、列表慢 |
| 9 | **主键是数据库自增的 `BIGINT`**（Java 的 `long`，0006 的 6.1，用户 2026-10-26 改定）；接入方的用户、部门、角色编号是 `VARCHAR(64)` | 引擎的编号就是数字，插入后取回；不用应用生成的雪花 | 雪花、大雪花（原定，改掉了） |
| 10 | 不建外键；会变的行带 `rev`（乐观锁），只追加的表不带；时间是 UTC 的 `DATETIME(3)`；JSON 用 MySQL 的 `JSON` 类型 | 同 0001 的通用约定 | — |
| 11 | **流程与表单都是"本身 + 版本"两张表**：`flow_process` 存启用停用、最新版本，`flow_definition` 存不可变的版本 | 停用是对整个流程的，不是对某个版本；和表单的 `flow_form` / `flow_form_version`（0004 的 5.2）对称 | 原草案只有版本表，停用要改所有版本或另找地方记 |
| 12 | 表名前缀可配（默认 `flow_`）；每组表背后是一个可替换的存储接口（0006 第 6 节） | 标准行为都能替换 | — |

## 2. 表一览（已定，用户 2026-10-26）

| 组 | 表 | 一句话 |
| --- | --- | --- |
| 定义 | `flow_process`、`flow_definition`、`flow_draft` | 流程本身、发布的版本、草稿（流程与表单共用） |
| 表单 | `flow_form`、`flow_form_version`、`flow_form_data` | 表单本身、发布的版本、实例上表单的值 |
| 运行 | `flow_instance`、`flow_work_item`、`flow_candidate`、`flow_wait`、`flow_event` | 实例、待办、候选、外部事件的等待、流转记录 |
| 协作 | `flow_cc` | 抄送 |
| 作业与时间 | `flow_job`、`flow_calendar`、`flow_calendar_day` | 作业、工作日历、日历里的例外日 |
| 基础 | `flow_request` | 命令的幂等记录（建表的历史由接入方的 Flyway 记，见第 9 节） |
| 归档 | `flow_instance_archive`、`flow_work_item_archive`、`flow_event_archive`、`flow_form_data_archive`、`flow_cc_archive` | 结构同原表，多一列 `archived_at` |

下面的类型里，`ID` 表示编号列（`BIGINT`，主键自增），`USER` 是 `VARCHAR(64)`（接入方的用户、部门、角色编号），`NAME` 是 `VARCHAR(200)`，`KEY` 是 `VARCHAR(64)`（流程、表单、节点的标识），`TIME` 是 `DATETIME(3)`。
每张表都有 `tenant_id VARCHAR(64) NOT NULL DEFAULT ''`，下面不再列。

## 3. 各表（已定，用户 2026-10-26）

### 3.1 定义

**`flow_process`**：流程本身，每个 key 一行

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | ID | |
| `process_key` | KEY | 流程标识（`key` 是 MySQL 的保留字，不用它做列名） |
| `name`、`category` | NAME、KEY | 最新版本的名称与分类，列表用 |
| `status` | `VARCHAR(16)` | `active`、`disabled`；停用后不能新发起 |
| `latest_version` | INT | 最新发布的版本号，0 表示还没发布过 |
| `rev`、`created_at`、`updated_at`、`updated_by` | | |

索引：唯一（租户, `process_key`）；（租户, `category`）。

**`flow_definition`**：发布的版本，发布后不能改

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | ID | |
| `process_key`、`version` | KEY、INT | |
| `model` | JSON | 0005 的整份定义（含 `layout`） |
| `form_key`、`form_version` | KEY、INT | 从 `model` 抄出来的：查"哪些流程用了这张表单的哪个版本"（0004 的 5.3） |
| `note` | `VARCHAR(500)` | 发布说明 |
| `published_by`、`published_by_name`、`published_at` | USER、NAME、TIME | |

索引：唯一（租户, `process_key`, `version`）；（租户, `form_key`, `form_version`）。

**`flow_draft`**：流程与表单共用的草稿（0004 的 5.2）

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `kind`、`target_key` | `VARCHAR(16)`、KEY | `process` / `form` 加标识 |
| `name` | NAME | |
| `content` | JSON | 流程是定义；表单是前端源码与后端源码 |
| `base_version` | INT | 基于哪个已发布的版本改的 |
| `rev`、`updated_by`、`updated_by_name`、`updated_at` | | 版本列防两个人互相覆盖；最后修改人用于 0004 第 6 节"打开别人的草稿先确认" |

主键：（租户, `kind`, `target_key`）。

### 3.2 表单（0004 的 5.2）

**`flow_form`**：同 `flow_process`（`form_key`、`name`、`category`、`status`、`latest_version`、`rev`……）。

**`flow_form_version`**

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | ID | |
| `form_key`、`version` | KEY、INT | |
| `client_source`、`client_compiled` | `MEDIUMTEXT` | 前端源码与编译结果 |
| `compiler_version` | `VARCHAR(32)` | 编译它的编译器版本（0004 的 5.4） |
| `manifest` | JSON | 字段清单 |
| `modules` | JSON | 用到的模块 |
| `server_language`、`server_source` | `VARCHAR(8)`、`MEDIUMTEXT` | `java` / `ts` / 空，后端源码 |
| `note`、`published_by`、`published_by_name`、`reviewed_by`、`published_at` | | `reviewed_by`：双人发布打开时的审阅人（0004 的 6.2） |

索引：唯一（租户, `form_key`, `version`）。

**`flow_form_data`**：实例上表单的值，每个实例一行

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `instance_id` | ID | 主键 |
| `form_version_id` | ID | |
| `data` | JSON | 表单的值（0004 的 1.3 的形状） |
| `rev`、`updated_at` | | |

### 3.3 运行

**`flow_instance`**

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | ID | |
| `process_key`、`definition_id`、`version` | KEY、ID、INT | 按哪个版本走 |
| `business_key` | `VARCHAR(128)` | 业务单据编号；同一个业务编号可以有多个实例（重新提交） |
| `title` | `VARCHAR(500)` | 实例标题（0005 第 2 节的模板） |
| `priority` | `TINYINT` | 紧急程度，0 是常规，越大越急；发起时给（`start` 的参数），管理员可改；抄到待办上排序用 |
| `summary` | JSON | 摘要列（0005 第 2 节） |
| `status` | `VARCHAR(16)` | `draft`、`active`、`suspended`、`completed`、`terminated`、`withdrawn` |
| `result` | KEY | 结束结果 |
| `current` | JSON | 正在处理的节点 `[{ node, name }]`，列表显示用 |
| `variables` | JSON | 流程变量 |
| `state` | JSON | 运行状态：并行令牌、汇合计数、各节点轮次、依次会签的名单（1 节第 5 条）；引擎内部用，不给前端 |
| `suspension` | JSON | 暂停时：原因、恢复条件、谁、何时 |
| `parent_instance_id`、`parent_node`、`root_instance_id` | ID、KEY、ID | 调用流程的父子关系；`root` 是最外层（多层嵌套时整棵一起查、一起终止） |
| `source_instance_id`、`source_node` | ID、KEY | 由别的流程的自动节点或异步调用发起时，记来源（0002 §2） |
| `started_by`、`started_by_name`、`started_at`、`ended_at` | | |
| `rev`、`updated_at` | | 推进前先 `rev = rev + 1` 锁行 |

索引：（租户, `status`, `process_key`, `started_at`）；（租户, `started_by`, `started_at`）；（租户, `business_key`）；（`parent_instance_id`）；（`root_instance_id`）；（租户, `ended_at`）（归档用）。

**`flow_work_item`**：待办

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | ID | |
| `kind` | `VARCHAR(16)` | `task`（普通待办）、`failure`（自动节点重试用完后给管理员的"异常处理"，0005 的 4.1） |
| `instance_id`、`process_key`、`priority` | ID、KEY、`TINYINT` | 从实例抄过来：待办按流程筛选、按紧急程度排序不用连实例表 |
| `node`、`node_name`、`round` | KEY、NAME、INT | `round`：这个节点第几轮（`sys.round.节点`） |
| `state` | `VARCHAR(16)` | `open`、`waiting`（等前加签、委派出去的人办完）、`done`、`canceled` |
| `assignment` | `VARCHAR(16)` | `pool`（收件箱里等认领）、`claimed`、`direct`、`designated`（负责人指定） |
| `arrival` | `VARCHAR(16)` | `start`、`forward`、`return`、`escalate`、`transfer`、`delegate`、`addSign`、`jump` |
| `inbox_kind`、`inbox_id` | `VARCHAR(32)`、USER | 收件箱是哪个角色、部门（或扩展的种类）的；显示用，可认领的查询走候选表 |
| `assignee_id`、`assignee_name` | USER、NAME | 当前处理人；在收件箱里等认领时为空 |
| `from_node`、`from_user_id`、`from_user_name`、`from_item_id` | | 从哪个节点、谁那里来的 |
| `sign_group`、`sign_seq`、`weight` | ID、INT、INT | 会签：同一节点同一轮的参与人共用一个 `sign_group`；依次会签的顺序；票签的权重 |
| `parent_item_id`、`add_mode` | ID、`VARCHAR(8)` | 委派与加签都生成**子待办**，`parent_item_id` 指向原来的待办；`add_mode` 是加签的方式 `before` / `after` / `parallel`。前加签、委派时原待办进 `waiting`，子待办都办完回到 `open`（委派就回到原处理人确认） |
| `due_at`、`claimed_at`、`created_at`、`read_at` | TIME | `read_at`：处理人第一次打开的时间，列表里标"新" |
| `remind_count`、`last_reminded_at` | INT、TIME | 催办的次数与上一次的时间（0002 §5 的频率限制） |
| `needs_designation` | `BOOLEAN` | 无人认领超时，等负责人指定 |
| `designator_id` | USER | 等谁来指定：无人认领的作业到期时解析出的收件箱负责人（随第 4 节补） |
| `reassign_request` | JSON | 申请改派：谁、原因、何时 |
| `draft` | JSON | 暂存：表单的值与意见（0004 的 5.5） |
| `outcome`、`comment`、`attachments` | KEY、`TEXT`、JSON | 处理结果 |
| `completed_by`、`completed_by_name`、`completed_at` | USER、NAME、TIME | |
| `rev` | INT | |

索引：（租户, `assignee_id`, `state`, `created_at`）；（租户, `completed_by`, `completed_at`）；（`instance_id`, `state`）；（`sign_group`）；（`parent_item_id`）；（租户, `designator_id`, `needs_designation`）（随第 4 节补）。

**`flow_candidate`**：打开着的待办谁能处理、谁被排除（1 节第 3、4 条）

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | ID | |
| `work_item_id` | ID | |
| `kind` | `VARCHAR(32)` | `user`、`role`、`department`、接入方扩展的种类（如 `position`）、`excluded`（退回过收件箱，不能再认领） |
| `value` | USER | |

索引：（租户, `kind`, `value`, `work_item_id`）（查可认领，覆盖索引）；唯一（`work_item_id`, `kind`, `value`）（查排除、删除）。

**`flow_wait`**：等外部事件（0005 的 4.3）

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | ID | |
| `instance_id`、`node`、`token` | ID、KEY、`VARCHAR(64)` | `token`：并行里是哪条分支在等 |
| `event`、`business_key` | `VARCHAR(128)` | 按事件名加实例或业务编号找到它 |
| `created_at` | TIME | 等多久超时由作业负责 |

索引：（租户, `event`, `business_key`）；（`instance_id`）。事件到了删掉这一行（带条件的 DELETE，改到 0 行就是已经被处理过）。

**`flow_event`**：流转记录，只追加

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | ID | |
| `instance_id`、`work_item_id` | ID | |
| `type` | `VARCHAR(48)` | 0006 第 5 节的事件种类，另有 `comment`（留言）、`data.changed`（管理员改数据）等 |
| `node`、`node_name`、`edge`、`outcome` | KEY、NAME、KEY、KEY | 名字存快照，看记录不用再查定义 |
| `instance_rev` | INT | 写这条记录时实例的 `rev`：同一个实例的记录按它排出准确的先后（同一毫秒、不同机器发的号也不会乱），同一步写的几条它相同 |
| `actor_id`、`actor_name` | USER、NAME | |
| `at` | TIME | |
| `comment`、`attachments` | `TEXT`、JSON | |
| `data` | JSON | 附加：退回的目标、改了哪些表单字段（旧值、新值，0004 的 4.5）、事件带来的数据 |

索引：（`instance_id`, `instance_rev`, `id`）；（租户, `actor_id`, `type`, `at`）（我参与过的、工作量统计）；（租户, `type`, `at`）（按节点、按流程统计，随第 4 节补）。量大时可以用 query 的按月分表（query 的 0008），见第 6 节。

### 3.4 协作

**`flow_cc`**：抄送

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id`、`instance_id`、`node` | | |
| `recipient_id`、`recipient_name` | USER、NAME | |
| `from_user_id`、`from_user_name`、`message` | USER、NAME、`VARCHAR(500)` | 谁抄送的（抄送节点时为空）、附言 |
| `created_at`、`read_at` | TIME | |

索引：（租户, `recipient_id`, `read_at`, `created_at`）；（`instance_id`）。

### 3.5 作业与时间

**`flow_job`**

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | ID | |
| `kind` | `VARCHAR(32)` | `overdue`、`remind`、`unclaimed`、`timer`、`action`（异步动作）、`archive`……接入方可以加（0006 的 `JobHandler`） |
| `instance_id`、`work_item_id` | ID | 实例结束、待办处理完时按它们取消 |
| `due_at` | TIME | |
| `payload` | JSON | |
| `state` | `VARCHAR(16)` | `pending`、`failed`（重试用完）；成功的作业直接删掉 |
| `attempts`、`max_attempts` | INT | |
| `locked_by`、`locked_until` | `VARCHAR(128)`、TIME | 抢占（0001 第 4 条） |
| `last_error` | `TEXT` | |
| `created_at` | TIME | |

索引：（`state`, `due_at`）（领取：**不带租户开头**，工作进程替所有租户干活）；（`instance_id`）；（`work_item_id`）。

**`flow_calendar`**：一份工作日历

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `name` | KEY | 主键（租户, `name`） |
| `timezone` | `VARCHAR(64)` | 如 `Asia/Shanghai`：工作日按当地日期算，时间仍按 UTC 存 |
| `work_week` | `CHAR(7)` | 周一到周日哪几天上班，如 `1111100` |

**`flow_calendar_day`**：只存例外（节假日、调休上班）

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `calendar`、`day` | KEY、`DATE` | 主键（租户, `calendar`, `day`） |
| `workday` | `BOOLEAN` | 这一天上不上班 |
| `note` | NAME | 国庆、调休…… |

### 3.6 基础

**`flow_request`**：命令的幂等记录（0006 第 1 节）

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `request_id` | `VARCHAR(128)` | 主键（租户, `request_id`） |
| `command`、`actor_id` | `VARCHAR(48)`、USER | |
| `result` | JSON | 第一次执行的结果，重复的请求原样返回 |
| `created_at` | TIME | 作业定期删掉 N 天前的 |

和命令在同一个事务里插入：同一个 `request_id` 同时来两次，第二个在唯一键上等第一个提交，然后读到它的结果。

建表的历史不是引擎的表：用推荐的 Flyway 方式时是 Flyway 的 `flow_schema_history`（第 9 节）。

## 4. 主要查询与用到的索引（已定，用户 2026-10-26）

| 查询 | 怎样查 | 索引 |
| --- | --- | --- |
| 我的待办：落到我的 | `assignee_id = 我 AND state = 'open'` | `flow_work_item`（租户, `assignee_id`, `state`, `created_at`） |
| 我的待办：可认领的 | 候选表里 `(kind, value)` 属于我的（`user` = 我、`role` 在我的角色里、`department` 在我的部门里、扩展种类在我的归属里）→ 待办 `state = 'open' AND assignee_id IS NULL` → 去掉我被排除的 | `flow_candidate`（租户, `kind`, `value`, `work_item_id`）+ 主键 + 唯一（`work_item_id`, `kind`, `value`） |
| 两种合在一页 | 两段各取前"偏移 + 页长"条再合并排序；总数是两段之和（第二段要求没有处理人，两段不重叠）。能排序的只有到达时间、截止时间、紧急程度：一个人的待办不多，两段各自在库里排好再合并 | 同上 |
| 待办数（角标） | 两段各 `COUNT(*)` 相加 | 同上 |
| 需要我指定的 | 无人认领的作业到期时就解析出负责人，写进待办的 `designator_id`（第 3 节随第 4 节补的一列）；查 `designator_id = 我 AND needs_designation` | `flow_work_item`（租户, `designator_id`, `needs_designation`） |
| 按摘要筛选、排序待办 | 连实例表取 `summary`；只在"我的待办"这个小集合里筛 | 实例主键 |
| 已办 | `completed_by = 我`，默认只查最近 90 天（可改）；翻到很后面时改用"按上一页最后一条的时间往后取"，不用越来越大的偏移 | （租户, `completed_by`, `completed_at`） |
| 我发起的 | `started_by = 我` | `flow_instance`（租户, `started_by`, `started_at`） |
| 抄送我的 | `recipient_id = 我`，未读在前 | `flow_cc`（租户, `recipient_id`, `read_at`, `created_at`） |
| 实例详情、进度 | 实例主键 + 它的待办 + 它的流转记录 | （`instance_id`, `state`）、（`instance_id`, `instance_rev`, `id`） |
| 领取到期作业 | `state = 'pending' AND due_at <= 现在 AND (locked_until IS NULL OR locked_until < 现在)` 按 `due_at` 取一批，再逐个带条件 UPDATE 抢 | `flow_job`（`state`, `due_at`） |
| 外部事件 | `event = ? AND business_key = ?` | `flow_wait`（租户, `event`, `business_key`） |
| 统计 | 待办办完、节点离开时，流转记录的 `data` 里直接记下停留时长与是否超时，统计只对一种记录分组求和，不用把到达与离开配对 | `flow_event`（租户, `type`, `at`）（第 3 节随第 4 节补的索引）；大的统计建议在只读库或数仓里做 |
| 归档 | `ended_at < 现在 - N 天` 分批 | `flow_instance`（租户, `ended_at`） |

## 5. 写入（已定，用户 2026-10-26）

| 操作 | 语句 |
| --- | --- |
| 发起 | 插入实例、表单的值、第一批待办与候选、流转记录、作业（时限、提醒），一个事务 |
| 推进（处理、退回、跳转……） | 第一条 `UPDATE flow_instance SET rev = rev + 1 WHERE id = ?` 锁行 → `UPDATE flow_work_item SET state = 'done' … WHERE id = ? AND state = 'open'`（改到 0 行就是重复提交）→ 删掉它的候选与作业 → 按定义生成下一批 → 改实例的 `state`、`current`、`variables`、`summary` → 写流转记录 |
| 认领 | `UPDATE flow_work_item SET assignee_id = ?, assignment = 'claimed' … WHERE id = ? AND state = 'open' AND assignee_id IS NULL` |
| 退回收件箱 | 清空处理人，插入一行 `excluded` 候选 |
| 领取作业 | 同 0001 第 4 条 |
| 外部事件 | 带条件删掉 `flow_wait` 的那一行，删到了再推进 |
| 幂等 | 命令的事务里先插 `flow_request` |

## 6. 数据量与归档（已定，用户 2026-10-26）

- 估算：一个实例 5–20 个待办、20–60 条流转记录。每天 1000 个实例时，一年约 36 万实例、700 万待办、2000 万流转记录。
- **归档**（默认关，打开后配 N 天）：作业按 `ended_at` 分批把结束的实例连同待办、流转记录、表单的值、抄送搬进 `*_archive`，同一个事务里删掉原行。查实例详情、已办、我发起的时可以带上归档表（参数选）。
- **流转记录按月分表**（可选）：用 query 的按月分表（query 的 0008，`shard(Event, monthly(...))`）。**分表键是实例的发起月份**，不是记录自己的时间：
  流转记录上加一列 `instance_started_on`（`DATE`，实例的发起日期，打开分表时才有；query 的按月规则按日期列分），一个实例的记录都在同一张表里；查一个实例的记录时引擎从实例行带上这个月份，只查一张表。
  按记录时间分表的话，"查一个实例的流转记录"不带时间条件，会把所有月份的表都扫一遍。"我参与过的"这类跨实例的查询要带时间范围，按范围选表。
- **按需打开**：量不大时都不开；量大时先开归档（热表一直小），流转记录还嫌大再分表。
- **定期清理**：`flow_request` 默认保留 7 天、失败的作业默认保留 30 天，由作业删掉（天数可配）。
- 候选表与作业表只存"现在还有用的行"，不会随时间增长。

## 7. 可替换的部分（已定，用户 2026-10-26）

| 标准行为 | 怎样替换 |
| --- | --- |
| 每组表的读写 | 0006 第 6 节的存储接口：`DefinitionStore`（`flow_process`、`flow_definition`、`flow_draft`）、`FormStore`、`FormDataStore`、`InstanceStore`、`WorkItemStore`（含候选）、`EventStore`、`JobStore`、`WorkerStore`、`RequestStore`，每个都能单独换 |
| 表名 | 前缀可配；单张表也可以改名（需要 query 的表名换名规则，0001 的"对 query 的要求"） |
| 归档 | 替换"归档策略"：搬到别的库、导出成文件、不归档 |
| 待办查询 | `TodoQueryCustomizer` 连接入方自己的表（0006 第 4 节） |

## 8. 和 0001 原草案相比（已定，用户 2026-10-26）

- 新增 `flow_process`（流程本身）、`flow_form`、`flow_form_version`、`flow_form_data`、`flow_wait`、`flow_calendar`、`flow_request` 与归档表；`flow_draft` 改为流程与表单共用。
- 实例上新增 `state`（运行状态）、`summary`、`priority`、`root_instance_id`、`suspension`，`current` 改成带名字的 JSON。
- 待办上新增 `kind`（普通待办、异常处理）、`waiting` 状态、会签的几列、子待办（委派与加签都生成子待办，不用 `owner` 列）、`draft`（暂存）、`read_at`、催办次数、`priority`、名字快照；`flow_candidate` 改成只存打开着的待办。
- 工作日历拆成日历（带时区、一周的工作日）与例外日两张。
- 全部表加租户列，索引以租户开头（作业表除外）。

## 9. 建表（已定，用户 2026-10-26）

- **引擎不自己建表，也不用 query 的迁移器**：flow-core 的 jar 里带建表脚本 `db/flow/mysql/V1__create_flow_tables.sql`（以后的变更是 `V2__…`、`V3__…`，执行过的脚本不再改），每张表、每列都有中文注释。
- **推荐用 Flyway 执行**，单独一套、不和应用自己的迁移混在一起：位置 `classpath:db/flow/mysql`，历史表 `flow_schema_history`。不用 Flyway 的交给 DBA 照脚本执行。
- flow-spring 可以按配置帮忙跑这一套 Flyway（默认关，第 ③c 步做）；引擎的实库测试也是这样建表，等于一直在验证推荐的做法。
- 主键列是 `BIGINT AUTO_INCREMENT`（0006 的 6.1）。
- 理由：接入方多半已经有 Flyway / Liquibase 和 DBA 的审批流程，库自己在启动时改表结构不受欢迎；纯 SQL 脚本一眼能审。
