package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.RunState;
import org.dpdns.zerodep.flow.core.data.Variables;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance.CurrentNode;
import org.dpdns.zerodep.query.annotation.Column;
import org.dpdns.zerodep.query.annotation.Generated;
import org.dpdns.zerodep.query.annotation.Generated;
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
 * <p>主键是数据库自增的 BIGINT（{@code @Generated}，插入时不写，{@code insertKeys} 取回）。每列直接声明成引擎里的类型，
 * 库里的类型由转换器（{@link Columns}）对上：时间是 {@link Instant}、JSON 列是有名字的类型。所以读出来的行可以直接是引擎的记录（列名同记录的属性名）。
 * <p>两个父接口由 {@link FlowDatabase} 统一处理：{@link Tenanted} 的租户列在查询里自动加条件、插入时自动填；
 * {@link Touched} 的修改时间在插入、更新时自动填。
 */
final class FlowTables {
    private FlowTables() { }

    /** 带租户列的表 */
    interface Tenanted extends Entity {
        @Column("tenant_id") ColText<String> tenantId();
    }

    /** 带修改时间的表 */
    interface Touched extends Entity {
        @Column(value = "updated_at", converter = Columns.Utc.class) ColTimestamp<Instant> updatedAt();
    }

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
        @Column("updated_by") ColText<String> updatedBy();
    }

    @Table("flow_definition")
    interface Definition extends Tenanted {
        @Id @Generated @Column("id") ColBigint<Long> id();
        @Column("process_key") ColText<String> processKey();
        @Column("version") ColInteger<Integer> version();
        @Column("model") ColClob<String> model();
        @Column("form_key") ColText<String> formKey();
        @Column("form_version") ColInteger<Integer> formVersion();
        @Column("note") ColText<String> note();
        @Column("published_by") ColText<String> publishedBy();
        @Column("published_by_name") ColText<String> publishedByName();
        @Column(value = "published_at", converter = Columns.Utc.class) ColTimestamp<Instant> publishedAt();
    }

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
        @Column("updated_by") ColText<String> updatedBy();
    }

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
        @Column("published_by") ColText<String> publishedBy();
        @Column("published_by_name") ColText<String> publishedByName();
        @Column(value = "published_at", converter = Columns.Utc.class) ColTimestamp<Instant> publishedAt();
    }

    @Table("flow_form_data")
    interface FormData extends Tenanted, Touched {
        @Id @Column("instance_id") ColBigint<Long> instanceId();
        @Column("form_version_id") ColBigint<Long> formVersionId();
        @Column(value = "data", converter = Columns.FormValuesJson.class) ColClob<FormValues> values();
        @Column("rev") ColInteger<Integer> rev();
    }

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
        @Column("started_by") ColText<String> startedBy();
        @Column("started_by_name") ColText<String> startedByName();
        @Column(value = "started_at", converter = Columns.Utc.class) ColTimestamp<Instant> startedAt();
        @Column(value = "ended_at", converter = Columns.Utc.class) ColTimestamp<Instant> endedAt();
        @Column("rev") ColInteger<Integer> rev();
    }

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
        @Column("inbox_id") ColText<String> inboxId();
        @Column("assignee_id") ColText<String> assigneeId();
        @Column("assignee_name") ColText<String> assigneeName();
        @Column(value = "due_at", converter = Columns.Utc.class) ColTimestamp<Instant> dueAt();
        @Column(value = "claimed_at", converter = Columns.Utc.class) ColTimestamp<Instant> claimedAt();
        @Column(value = "created_at", converter = Columns.Utc.class) ColTimestamp<Instant> createdAt();
        @Column("remind_count") ColInteger<Integer> remindCount();
        @Column("needs_designation") ColBoolean<Boolean> needsDesignation();
        @Column("outcome") ColText<String> outcome();
        @Column("comment") ColClob<String> comment();
        @Column("completed_by") ColText<String> completedBy();
        @Column("completed_by_name") ColText<String> completedByName();
        @Column(value = "completed_at", converter = Columns.Utc.class) ColTimestamp<Instant> completedAt();
        @Column("rev") ColInteger<Integer> rev();

        /** 打开着的待办谁能处理、谁被排除 */
        default Many<Candidate> candidates() { return many(Candidate.class, c -> c.workItemId().eq(id())); }
    }

    @Table("flow_candidate")
    interface Candidate extends Tenanted {
        @Id @Generated @Column("id") ColBigint<Long> id();
        @Column("work_item_id") ColBigint<Long> workItemId();
        @Column("kind") ColText<String> kind();
        @Column("value") ColText<String> value();
    }

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
        @Column("actor_id") ColText<String> actorId();
        @Column("actor_name") ColText<String> actorName();
        @Column(value = "at", converter = Columns.Utc.class) ColTimestamp<Instant> at();
        @Column("comment") ColClob<String> comment();
        @Column(value = "data", converter = Columns.ObjectJson.class) ColClob<Map<String, Object>> data();
    }
}
