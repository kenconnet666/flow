package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.RunState;
import org.dpdns.zerodep.flow.core.data.Variables;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance.CurrentNode;
import org.dpdns.zerodep.query.annotation.Column;
import org.dpdns.zerodep.query.annotation.Generated;
import org.dpdns.zerodep.query.annotation.Id;
import org.dpdns.zerodep.query.annotation.Table;
import org.dpdns.zerodep.query.table.Entity;
import org.dpdns.zerodep.query.table.Many;
import org.dpdns.zerodep.query.table.column.ColBigint;
import org.dpdns.zerodep.query.table.column.ColBoolean;
import org.dpdns.zerodep.query.table.column.ColClob;
import org.dpdns.zerodep.query.table.column.ColInteger;
import org.dpdns.zerodep.query.table.column.ColText;
import org.dpdns.zerodep.query.table.column.ColTimestamp;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 默认存储读写的表（建表脚本见 resources 下的 db/flow/mysql，列的含义见 docs/decisions/0007）。只声明引擎读写到的列。
 * <p>主键是数据库自增的 BIGINT（{@code @Generated}，插入时不写，{@code insertReturning} 换进返回的记录）。每列直接声明成引擎里的类型，
 * 库里的类型由转换器（{@link Columns}）对上：时间是 {@link Instant}、JSON 列是有名字的类型。所以读出来的行可以直接是引擎的记录（列名同记录的属性名）。
 * <p>两个父接口由 {@link FlowDatabase} 统一处理：{@link Tenanted} 的租户列在查询里自动加条件、插入时自动填；
 * {@link Touched} 的修改时间在插入、更新时自动填。
 */
final class FlowTables {
    private FlowTables() { }

    /** 带租户列的表 */
    interface Tenanted extends Entity {
        @Column("tenant_id") ColBigint<Long> tenantId();
    }

    /** 带修改时间的表 */
    interface Touched extends Entity {
        @Column(value = "updated_at", converter = Columns.Utc.class) ColTimestamp<Instant> updatedAt();
    }

    /** 流程本身：启用停用与最新版本（一个流程标识一行） */
    @Table("flow_process")
    interface Process extends Tenanted, Touched {
        @Id @Generated @Column("id") ColBigint<Long> id();
        @Column("process_key") ColText<String> processKey();
        @Column("name") ColText<String> name();
        @Column("category") ColText<String> category();
        @Column("status") ColText<String> status();
        @Column("latest_version") ColInteger<Integer> latestVersion();
        @Column("rev") ColInteger<Integer> rev();
        @Column(value = "created_at", converter = Columns.Utc.class) ColTimestamp<Instant> createdAt();
        @Column("updated_by") ColBigint<Long> updatedBy();
    }

    /** 流程发布的一个版本，发布后不再改 */
    @Table("flow_definition")
    interface Definition extends Tenanted {
        @Id @Generated @Column("id") ColBigint<Long> id();
        @Column("process_key") ColText<String> processKey();
        @Column("version") ColInteger<Integer> version();
        @Column("model") ColClob<String> model();
        @Column("form_key") ColText<String> formKey();
        @Column("form_version") ColInteger<Integer> formVersion();
        @Column("note") ColText<String> note();
        @Column("published_by") ColBigint<Long> publishedBy();
        @Column("published_by_name") ColText<String> publishedByName();
        @Column(value = "published_at", converter = Columns.Utc.class) ColTimestamp<Instant> publishedAt();
    }

    /** 表单本身：启用停用与最新版本 */
    @Table("flow_form")
    interface Form extends Tenanted, Touched {
        @Id @Generated @Column("id") ColBigint<Long> id();
        @Column("form_key") ColText<String> formKey();
        @Column("name") ColText<String> name();
        @Column("category") ColText<String> category();
        @Column("status") ColText<String> status();
        @Column("latest_version") ColInteger<Integer> latestVersion();
        @Column("rev") ColInteger<Integer> rev();
        @Column(value = "created_at", converter = Columns.Utc.class) ColTimestamp<Instant> createdAt();
        @Column("updated_by") ColBigint<Long> updatedBy();
    }

    /** 表单发布的一个版本：字段清单与源码 */
    @Table("flow_form_version")
    interface FormVersion extends Tenanted {
        @Id @Generated @Column("id") ColBigint<Long> id();
        @Column("form_key") ColText<String> formKey();
        @Column("version") ColInteger<Integer> version();
        @Column("client_source") ColClob<String> clientSource();
        @Column("client_compiled") ColClob<String> clientCompiled();
        @Column("compiler_version") ColText<String> compilerVersion();
        @Column("manifest") ColClob<String> manifest();
        @Column("server_language") ColText<String> serverLanguage();
        @Column("server_source") ColClob<String> serverSource();
        @Column("note") ColText<String> note();
        @Column("published_by") ColBigint<Long> publishedBy();
        @Column("published_by_name") ColText<String> publishedByName();
        @Column(value = "published_at", converter = Columns.Utc.class) ColTimestamp<Instant> publishedAt();
    }

    /** 一个实例填的表单的值（每个实例一行） */
    @Table("flow_form_data")
    interface FormData extends Tenanted, Touched {
        @Id @Column("instance_id") ColBigint<Long> instanceId();
        @Column("form_version_id") ColBigint<Long> formVersionId();
        @Column(value = "data", converter = Columns.FormValuesJson.class) ColClob<FormValues> values();
        @Column("rev") ColInteger<Integer> rev();
    }

    /** 流程实例：读出来就是 InstanceRecord */
    @Table("flow_instance")
    interface Instance extends Tenanted, Touched {
        @Id @Generated @Column("id") ColBigint<Long> id();
        @Column("process_key") ColText<String> processKey();
        @Column("definition_id") ColBigint<Long> definitionId();
        @Column("version") ColInteger<Integer> version();
        @Column("business_key") ColText<String> businessKey();
        @Column("title") ColText<String> title();
        @Column("priority") ColInteger<Integer> priority();
        @Column(value = "summary", converter = Columns.ObjectJson.class) ColClob<Map<String, Object>> summary();
        @Column("status") ColText<String> status();
        @Column("result") ColText<String> result();
        @Column(value = "current", converter = Columns.CurrentNodesJson.class) ColClob<List<CurrentNode>> current();
        @Column(value = "variables", converter = Columns.VariablesJson.class) ColClob<Variables> variables();
        @Column(value = "state", converter = Columns.RunStateJson.class) ColClob<RunState> state();
        @Column("started_by") ColBigint<Long> startedBy();
        @Column("started_by_name") ColText<String> startedByName();
        @Column(value = "started_at", converter = Columns.Utc.class) ColTimestamp<Instant> startedAt();
        @Column(value = "ended_at", converter = Columns.Utc.class) ColTimestamp<Instant> endedAt();
        @Column("rev") ColInteger<Integer> rev();
    }

    /** 待办：读出来就是模型包的 WorkItem */
    @Table("flow_work_item")
    interface WorkItem extends Tenanted {
        @Id @Generated @Column("id") ColBigint<Long> id();
        @Column("kind") ColText<String> kind();
        @Column("instance_id") ColBigint<Long> instanceId();
        @Column("process_key") ColText<String> processKey();
        @Column("priority") ColInteger<Integer> priority();
        @Column("node") ColText<String> node();
        @Column("node_name") ColText<String> nodeName();
        @Column("round") ColInteger<Integer> round();
        @Column("state") ColText<String> state();
        @Column("assignment") ColText<String> assignment();
        @Column("arrival") ColText<String> arrival();
        @Column("inbox_kind") ColText<String> inboxKind();
        @Column("sign_seq") ColInteger<Integer> signSeq();
        @Column("weight") ColInteger<Integer> weight();
        @Column("inbox_id") ColBigint<Long> inboxId();
        @Column("assignee_id") ColBigint<Long> assigneeId();
        @Column("assignee_name") ColText<String> assigneeName();
        @Column("from_user_id") ColBigint<Long> fromUserId();
        @Column("from_user_name") ColText<String> fromUserName();
        @Column("parent_item_id") ColBigint<Long> parentItemId();
        @Column("add_mode") ColText<String> addMode();
        @Column(value = "due_at", converter = Columns.Utc.class) ColTimestamp<Instant> dueAt();
        @Column(value = "claimed_at", converter = Columns.Utc.class) ColTimestamp<Instant> claimedAt();
        @Column(value = "created_at", converter = Columns.Utc.class) ColTimestamp<Instant> createdAt();
        @Column("remind_count") ColInteger<Integer> remindCount();
        @Column(value = "last_reminded_at", converter = Columns.Utc.class) ColTimestamp<Instant> lastRemindedAt();
        @Column("needs_designation") ColBoolean<Boolean> needsDesignation();
        @Column("outcome") ColText<String> outcome();
        @Column("comment") ColClob<String> comment();
        @Column("completed_by") ColBigint<Long> completedBy();
        @Column("completed_by_name") ColText<String> completedByName();
        @Column(value = "completed_at", converter = Columns.Utc.class) ColTimestamp<Instant> completedAt();
        @Column("rev") ColInteger<Integer> rev();

        /** 打开着的待办谁能处理、谁被排除 */
        default Many<Candidate> candidates() { return many(Candidate.class, c -> c.workItemId().eq(id())); }
    }

    /** 打开着的待办谁能认领、谁被排除 */
    @Table("flow_candidate")
    interface Candidate extends Tenanted {
        @Id @Generated @Column("id") ColBigint<Long> id();
        @Column("work_item_id") ColBigint<Long> workItemId();
        @Column("kind") ColText<String> kind();
        @Column("target_id") ColBigint<Long> targetId();
    }

    /** 作业：到时候要做的事；读出来就是 JobStore.Job */
    @Table("flow_job")
    interface Job extends Tenanted {
        @Id @Generated @Column("id") ColBigint<Long> id();
        @Column("kind") ColText<String> kind();
        @Column("instance_id") ColBigint<Long> instanceId();
        @Column("work_item_id") ColBigint<Long> workItemId();
        @Column(value = "due_at", converter = Columns.Utc.class) ColTimestamp<Instant> dueAt();
        @Column(value = "payload", converter = Columns.ObjectJson.class) ColClob<Map<String, Object>> payload();
        @Column("state") ColText<String> state();
        @Column("attempts") ColInteger<Integer> attempts();
        @Column("max_attempts") ColInteger<Integer> maxAttempts();
        @Column("locked_by") ColText<String> lockedBy();
        @Column(value = "locked_until", converter = Columns.Utc.class) ColTimestamp<Instant> lockedUntil();
        @Column("last_error") ColClob<String> lastError();
        @Column(value = "created_at", converter = Columns.Utc.class) ColTimestamp<Instant> createdAt();
    }

    /** 抄送：读出来就是模型包的 Cc */
    @Table("flow_cc")
    interface Cc extends Tenanted {
        @Id @Generated @Column("id") ColBigint<Long> id();
        @Column("instance_id") ColBigint<Long> instanceId();
        @Column("node") ColText<String> node();
        @Column("node_name") ColText<String> nodeName();
        @Column("recipient_id") ColBigint<Long> recipientId();
        @Column("recipient_name") ColText<String> recipientName();
        @Column("from_user_id") ColBigint<Long> fromUserId();
        @Column("from_user_name") ColText<String> fromUserName();
        @Column("message") ColText<String> message();
        @Column(value = "created_at", converter = Columns.Utc.class) ColTimestamp<Instant> createdAt();
        @Column(value = "read_at", converter = Columns.Utc.class) ColTimestamp<Instant> readAt();
    }

    /** 流转记录，只追加：读出来就是模型包的 FlowEvent */
    @Table("flow_event")
    interface Event extends Tenanted {
        @Id @Generated @Column("id") ColBigint<Long> id();
        @Column("instance_id") ColBigint<Long> instanceId();
        @Column("work_item_id") ColBigint<Long> workItemId();
        @Column("type") ColText<String> type();
        @Column("node") ColText<String> node();
        @Column("node_name") ColText<String> nodeName();
        @Column("edge") ColText<String> edge();
        @Column("outcome") ColText<String> outcome();
        @Column("instance_rev") ColInteger<Integer> instanceRev();
        @Column("actor_id") ColBigint<Long> actorId();
        @Column("actor_name") ColText<String> actorName();
        @Column(value = "at", converter = Columns.Utc.class) ColTimestamp<Instant> at();
        @Column("comment") ColClob<String> comment();
        @Column(value = "data", converter = Columns.ObjectJson.class) ColClob<Map<String, Object>> data();
    }
}
