/**
 * 默认存储读写的表（建表脚本在 sql/mysql，同 Java flow-core 的 db/flow/mysql；列的含义见 docs/decisions/0007）。只声明引擎读写到的列。
 * 主键是数据库自增的 BIGINT（@generated，插入时不写）。时刻列经 utc 转换成 ISO 文字，JSON 列是对象，所以读出来的行直接是引擎的记录。
 * 两个基类由 flowDatabase 统一处理：Tenanted 的租户列在查询里自动加条件、插入时自动填；Touched 的修改时间在插入、更新时自动填。
 */
import type { CalendarDateTime } from '@internationalized/date';
import { bigint, boolean, column, generated, id, integer, many, table, type Many, type Stored } from '@zerodep-dev/query-core';
import type { RunState } from '../../run-state.js';
import type { Instant } from '../../time.js';
import { currentNodesJson, objectJson, runStateJson, utc } from './columns.js';

/** 时刻列的字段类型：引擎里是文字，库里是时间戳 */
export type At = Stored<Instant, CalendarDateTime>;

/** 带租户列的表 */
export abstract class Tenanted {
  @column('tenant_id', bigint) tenantId!: bigint;
}

/** 带租户列与修改时间的表 */
export abstract class Touched extends Tenanted {
  @column('updated_at', utc) updatedAt!: At;
}

/** 流程本身：启用停用与最新版本 */
@table('flow_process')
export class ProcessRow extends Touched {
  @id @generated @column('id', bigint) id!: bigint;
  @column('process_key') processKey!: string;
  @column('name') name!: string;
  @column('category') category!: string | null;
  @column('status') status!: string;
  @column('latest_version', integer) latestVersion!: number;
  @column('rev', integer) rev!: number;
  @column('created_at', utc) createdAt!: At;
  @column('updated_by', bigint) updatedBy!: bigint | null;
}

/** 流程发布的一个版本 */
@table('flow_definition')
export class DefinitionRow extends Tenanted {
  @id @generated @column('id', bigint) id!: bigint;
  @column('process_key') processKey!: string;
  @column('version', integer) version!: number;
  @column('model') model!: string;
  @column('form_key') formKey!: string | null;
  @column('form_version', integer) formVersion!: number | null;
  @column('note') note!: string | null;
  @column('published_by', bigint) publishedBy!: bigint | null;
  @column('published_by_name') publishedByName!: string | null;
  @column('published_at', utc) publishedAt!: At;
}

/** 表单本身 */
@table('flow_form')
export class FormRow extends Touched {
  @id @generated @column('id', bigint) id!: bigint;
  @column('form_key') formKey!: string;
  @column('name') name!: string;
  @column('category') category!: string | null;
  @column('status') status!: string;
  @column('latest_version', integer) latestVersion!: number;
  @column('rev', integer) rev!: number;
  @column('created_at', utc) createdAt!: At;
  @column('updated_by', bigint) updatedBy!: bigint | null;
}

/** 表单发布的一个版本 */
@table('flow_form_version')
export class FormVersionRow extends Tenanted {
  @id @generated @column('id', bigint) id!: bigint;
  @column('form_key') formKey!: string;
  @column('version', integer) version!: number;
  @column('client_source') clientSource!: string | null;
  @column('client_compiled') clientCompiled!: string | null;
  @column('compiler_version') compilerVersion!: string | null;
  @column('manifest') manifest!: string;
  @column('server_language') serverLanguage!: string | null;
  @column('server_source') serverSource!: string | null;
  @column('note') note!: string | null;
  @column('published_by', bigint) publishedBy!: bigint | null;
  @column('published_by_name') publishedByName!: string | null;
  @column('published_at', utc) publishedAt!: At;
}

/** 一个实例填的表单的值 */
@table('flow_form_data')
export class FormDataRow extends Touched {
  @id @column('instance_id', bigint) instanceId!: bigint;
  @column('form_version_id', bigint) formVersionId!: bigint;
  @column('data', objectJson) values!: Record<string, unknown>;
  @column('rev', integer) rev!: number;
}

/** 流程实例 */
@table('flow_instance')
export class InstanceRow extends Touched {
  @id @generated @column('id', bigint) id!: bigint;
  @column('process_key') processKey!: string;
  @column('definition_id', bigint) definitionId!: bigint;
  @column('version', integer) version!: number;
  @column('business_key') businessKey!: string | null;
  @column('title') title!: string | null;
  @column('priority', integer) priority!: number;
  @column('summary', objectJson) summary!: Record<string, unknown> | null;
  @column('status') status!: string;
  @column('result') result!: string | null;
  @column('current', currentNodesJson) current!: { node: string; name: string | null }[] | null;
  @column('variables', objectJson) variables!: Record<string, unknown> | null;
  @column('state', runStateJson) state!: RunState | null;
  @column('started_by', bigint) startedBy!: bigint | null;
  @column('started_by_name') startedByName!: string | null;
  @column('started_at', utc) startedAt!: At;
  @column('ended_at', utc) endedAt!: At | null;
  @column('parent_instance_id', bigint) parentInstanceId!: bigint | null;
  @column('parent_node') parentNode!: string | null;
  @column('parent_round', integer) parentRound!: number | null;
  @column('root_instance_id', bigint) rootInstanceId!: bigint | null;
  @column('rev', integer) rev!: number;
}

/** 打开着的待办谁能认领、谁被排除 */
@table('flow_candidate')
export class CandidateRow extends Tenanted {
  @id @generated @column('id', bigint) id!: bigint;
  @column('work_item_id', bigint) workItemId!: bigint;
  @column('kind') kind!: string;
  @column('target_id', bigint) targetId!: bigint;
}

/** 待办 */
@table('flow_work_item')
export class WorkItemRow extends Tenanted {
  @id @generated @column('id', bigint) id!: bigint;
  @column('kind') kind!: string;
  @column('instance_id', bigint) instanceId!: bigint;
  @column('process_key') processKey!: string;
  @column('priority', integer) priority!: number;
  @column('node') node!: string;
  @column('node_name') nodeName!: string | null;
  @column('round', integer) round!: number;
  @column('sign_seq', integer) signSeq!: number | null;
  @column('weight', integer) weight!: number | null;
  @column('state') state!: string;
  @column('assignment') assignment!: string;
  @column('arrival') arrival!: string;
  @column('inbox_kind') inboxKind!: string | null;
  @column('inbox_id', bigint) inboxId!: bigint | null;
  @column('assignee_id', bigint) assigneeId!: bigint | null;
  @column('assignee_name') assigneeName!: string | null;
  @column('from_user_id', bigint) fromUserId!: bigint | null;
  @column('from_user_name') fromUserName!: string | null;
  @column('parent_item_id', bigint) parentItemId!: bigint | null;
  @column('add_mode') addMode!: string | null;
  @column('created_at', utc) createdAt!: At;
  @column('claimed_at', utc) claimedAt!: At | null;
  @column('due_at', utc) dueAt!: At | null;
  @column('remind_count', integer) remindCount!: number;
  @column('last_reminded_at', utc) lastRemindedAt!: At | null;
  @column('needs_designation', boolean) needsDesignation!: boolean;
  @column('designator_id', bigint) designatorId!: bigint | null;
  @column('outcome') outcome!: string | null;
  @column('comment') comment!: string | null;
  @column('completed_by', bigint) completedBy!: bigint | null;
  @column('completed_by_name') completedByName!: string | null;
  @column('completed_at', utc) completedAt!: At | null;
  @column('rev', integer) rev!: number;

  /** 打开着的待办谁能处理、谁被排除 */
  @many(() => CandidateRow, (w, c) => c.workItemId.eq(w.id)) candidates!: Many<CandidateRow>;
}

/** 作业 */
@table('flow_job')
export class JobRow extends Tenanted {
  @id @generated @column('id', bigint) id!: bigint;
  @column('kind') kind!: string;
  @column('instance_id', bigint) instanceId!: bigint;
  @column('work_item_id', bigint) workItemId!: bigint | null;
  @column('due_at', utc) dueAt!: At;
  @column('payload', objectJson) payload!: Record<string, unknown> | null;
  @column('state') state!: string;
  @column('attempts', integer) attempts!: number;
  @column('max_attempts', integer) maxAttempts!: number;
  @column('locked_by') lockedBy!: string | null;
  @column('locked_until', utc) lockedUntil!: At | null;
  @column('last_error') lastError!: string | null;
  @column('created_at', utc) createdAt!: At;
}

/** 抄送 */
@table('flow_cc')
export class CcRow extends Tenanted {
  @id @generated @column('id', bigint) id!: bigint;
  @column('instance_id', bigint) instanceId!: bigint;
  @column('node') node!: string | null;
  @column('node_name') nodeName!: string | null;
  @column('recipient_id', bigint) recipientId!: bigint;
  @column('recipient_name') recipientName!: string | null;
  @column('from_user_id', bigint) fromUserId!: bigint | null;
  @column('from_user_name') fromUserName!: string | null;
  @column('message') message!: string | null;
  @column('created_at', utc) createdAt!: At;
  @column('read_at', utc) readAt!: At | null;
}

/** 最近一次处理之前的快照 */
@table('flow_step')
export class StepRow extends Tenanted {
  @id @generated @column('id', bigint) id!: bigint;
  @column('instance_id', bigint) instanceId!: bigint;
  @column('work_item_id', bigint) workItemId!: bigint;
  @column('rev', integer) rev!: number;
  @column('state', runStateJson) state!: RunState;
  @column('variables', objectJson) variables!: Record<string, unknown>;
  @column('form', objectJson) form!: Record<string, unknown>;
}

/** 流转记录，只追加 */
@table('flow_event')
export class EventRow extends Tenanted {
  @id @generated @column('id', bigint) id!: bigint;
  @column('instance_id', bigint) instanceId!: bigint;
  @column('work_item_id', bigint) workItemId!: bigint | null;
  @column('type') type!: string;
  @column('node') node!: string | null;
  @column('node_name') nodeName!: string | null;
  @column('edge') edge!: string | null;
  @column('outcome') outcome!: string | null;
  @column('instance_rev', integer) instanceRev!: number;
  @column('actor_id', bigint) actorId!: bigint | null;
  @column('actor_name') actorName!: string | null;
  @column('at', utc) at!: At;
  @column('comment') comment!: string | null;
  @column('data', objectJson) data!: Record<string, unknown> | null;
}
