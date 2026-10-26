package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.query.annotation.Column;
import org.dpdns.zerodep.query.annotation.Id;
import org.dpdns.zerodep.query.annotation.Table;
import org.dpdns.zerodep.query.table.Entity;
import org.dpdns.zerodep.query.table.Many;
import org.dpdns.zerodep.query.table.column.ColBigint;
import org.dpdns.zerodep.query.table.column.ColBoolean;
import org.dpdns.zerodep.query.table.column.ColClob;
import org.dpdns.zerodep.query.table.column.ColInteger;
import org.dpdns.zerodep.query.table.column.ColSmallint;
import org.dpdns.zerodep.query.table.column.ColText;
import org.dpdns.zerodep.query.table.column.ColTimestamp;

import java.time.LocalDateTime;

/**
 * 默认存储读写的表（建表脚本见 resources 下的 db/flow/mysql，列的含义见 docs/decisions/0007）。这里只声明引擎读写到的列；
 * 编号列暂时都是 BIGINT（大雪花要 query 支持"列类型按配置决定"之后再按策略声明）。时间是 UTC 的本地时间值。
 */
final class FlowTables {
    private FlowTables() { }

    /** 各表共有的租户列 */
    interface Tenanted extends Entity {
        @Column("tenant_id") ColText<String> tenantId();
    }

    @Table("flow_process")
    interface Process extends Tenanted {
        @Id @Column("id") ColBigint<Long> id();
        @Column("process_key") ColText<String> processKey();
        @Column("name") ColText<String> name();
        @Column("category") ColText<String> category();
        @Column("status") ColText<String> status();
        @Column("latest_version") ColInteger<Integer> latestVersion();
        @Column("rev") ColInteger<Integer> rev();
        @Column("created_at") ColTimestamp<LocalDateTime> createdAt();
        @Column("updated_at") ColTimestamp<LocalDateTime> updatedAt();
        @Column("updated_by") ColText<String> updatedBy();
    }

    @Table("flow_definition")
    interface Definition extends Tenanted {
        @Id @Column("id") ColBigint<Long> id();
        @Column("process_key") ColText<String> processKey();
        @Column("version") ColInteger<Integer> version();
        @Column("model") ColClob<String> model();
        @Column("form_key") ColText<String> formKey();
        @Column("form_version") ColInteger<Integer> formVersion();
        @Column("note") ColText<String> note();
        @Column("published_by") ColText<String> publishedBy();
        @Column("published_by_name") ColText<String> publishedByName();
        @Column("published_at") ColTimestamp<LocalDateTime> publishedAt();
    }

    @Table("flow_form")
    interface Form extends Tenanted {
        @Id @Column("id") ColBigint<Long> id();
        @Column("form_key") ColText<String> formKey();
        @Column("name") ColText<String> name();
        @Column("category") ColText<String> category();
        @Column("status") ColText<String> status();
        @Column("latest_version") ColInteger<Integer> latestVersion();
        @Column("rev") ColInteger<Integer> rev();
        @Column("created_at") ColTimestamp<LocalDateTime> createdAt();
        @Column("updated_at") ColTimestamp<LocalDateTime> updatedAt();
        @Column("updated_by") ColText<String> updatedBy();
    }

    @Table("flow_form_version")
    interface FormVersion extends Tenanted {
        @Id @Column("id") ColBigint<Long> id();
        @Column("form_key") ColText<String> formKey();
        @Column("version") ColInteger<Integer> version();
        @Column("client_source") ColClob<String> clientSource();
        @Column("client_compiled") ColClob<String> clientCompiled();
        @Column("compiler_version") ColText<String> compilerVersion();
        @Column("manifest") ColClob<String> manifest();
        @Column("modules") ColClob<String> modules();
        @Column("server_language") ColText<String> serverLanguage();
        @Column("server_source") ColClob<String> serverSource();
        @Column("note") ColText<String> note();
        @Column("published_by") ColText<String> publishedBy();
        @Column("published_by_name") ColText<String> publishedByName();
        @Column("published_at") ColTimestamp<LocalDateTime> publishedAt();
    }

    @Table("flow_form_data")
    interface FormData extends Tenanted {
        @Id @Column("instance_id") ColBigint<Long> instanceId();
        @Column("form_version_id") ColBigint<Long> formVersionId();
        @Column("data") ColClob<String> data();
        @Column("rev") ColInteger<Integer> rev();
        @Column("updated_at") ColTimestamp<LocalDateTime> updatedAt();
    }

    @Table("flow_instance")
    interface Instance extends Tenanted {
        @Id @Column("id") ColBigint<Long> id();
        @Column("process_key") ColText<String> processKey();
        @Column("definition_id") ColBigint<Long> definitionId();
        @Column("version") ColInteger<Integer> version();
        @Column("business_key") ColText<String> businessKey();
        @Column("title") ColText<String> title();
        @Column("priority") ColSmallint<Short> priority();
        @Column("summary") ColClob<String> summary();
        @Column("status") ColText<String> status();
        @Column("result") ColText<String> result();
        @Column("current") ColClob<String> current();
        @Column("variables") ColClob<String> variables();
        @Column("state") ColClob<String> state();
        @Column("started_by") ColText<String> startedBy();
        @Column("started_by_name") ColText<String> startedByName();
        @Column("started_at") ColTimestamp<LocalDateTime> startedAt();
        @Column("ended_at") ColTimestamp<LocalDateTime> endedAt();
        @Column("rev") ColInteger<Integer> rev();
        @Column("updated_at") ColTimestamp<LocalDateTime> updatedAt();
    }

    @Table("flow_work_item")
    interface WorkItem extends Tenanted {
        @Id @Column("id") ColBigint<Long> id();
        @Column("kind") ColText<String> kind();
        @Column("instance_id") ColBigint<Long> instanceId();
        @Column("process_key") ColText<String> processKey();
        @Column("priority") ColSmallint<Short> priority();
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
        @Column("from_node") ColText<String> fromNode();
        @Column("from_user_id") ColText<String> fromUserId();
        @Column("from_user_name") ColText<String> fromUserName();
        @Column("from_item_id") ColBigint<Long> fromItemId();
        @Column("due_at") ColTimestamp<LocalDateTime> dueAt();
        @Column("claimed_at") ColTimestamp<LocalDateTime> claimedAt();
        @Column("created_at") ColTimestamp<LocalDateTime> createdAt();
        @Column("remind_count") ColInteger<Integer> remindCount();
        @Column("needs_designation") ColBoolean<Boolean> needsDesignation();
        @Column("outcome") ColText<String> outcome();
        @Column("comment") ColClob<String> comment();
        @Column("completed_by") ColText<String> completedBy();
        @Column("completed_by_name") ColText<String> completedByName();
        @Column("completed_at") ColTimestamp<LocalDateTime> completedAt();
        @Column("rev") ColInteger<Integer> rev();

        /** 打开着的待办谁能处理、谁被排除 */
        default Many<Candidate> candidates() { return many(Candidate.class, c -> c.workItemId().eq(id())); }
    }

    @Table("flow_candidate")
    interface Candidate extends Tenanted {
        @Id @Column("id") ColBigint<Long> id();
        @Column("work_item_id") ColBigint<Long> workItemId();
        @Column("kind") ColText<String> kind();
        @Column("value") ColText<String> value();
    }

    @Table("flow_event")
    interface Event extends Tenanted {
        @Id @Column("id") ColBigint<Long> id();
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
        @Column("at") ColTimestamp<LocalDateTime> at();
        @Column("comment") ColClob<String> comment();
        @Column("data") ColClob<String> data();
    }
}
