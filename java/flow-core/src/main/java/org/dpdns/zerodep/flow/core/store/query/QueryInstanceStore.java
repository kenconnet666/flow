package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.id.IdStrategy;
import org.dpdns.zerodep.flow.core.store.InstanceStore;
import org.dpdns.zerodep.flow.core.store.query.FlowTables.Instance;
import org.dpdns.zerodep.query.Sql;
import org.dpdns.zerodep.query.exec.Database;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.dpdns.zerodep.query.Sql.from;
import static org.dpdns.zerodep.query.Sql.insertInto;
import static org.dpdns.zerodep.query.Sql.val;

/** 流程实例的默认存储：{@code flow_instance} 表 */
final class QueryInstanceStore extends StoreSupport implements InstanceStore {
    QueryInstanceStore(Database db, IdStrategy ids, Supplier<String> tenant) { super(db, ids, tenant); }

    /** 表里的一行（列名同 {@link Instance} 的方法名；JSON 列是文字） */
    record InstanceRow(String tenantId, Long id, String processKey, Long definitionId, Integer version, String businessKey, String title,
                       Short priority, String summary, String status, String result, String current, String variables, String state,
                       String startedBy, String startedByName, LocalDateTime startedAt, LocalDateTime endedAt, Integer rev,
                       LocalDateTime updatedAt) { }

    @Override
    public void insert(InstanceRecord i) {
        db.execute(insertInto(Instance.class).values(new InstanceRow(tenant.get(), key(i.id()), i.processKey(), key(i.definitionId()),
            i.version(), i.businessKey(), i.title(), (short) i.priority(), JsonColumns.writeSummary(i.summary()), i.status(), i.result(),
            JsonColumns.writeCurrent(i.current()), JsonColumns.write(i.variables()), JsonColumns.write(i.state()), i.startedBy(),
            i.startedByName(), time(i.startedAt()), time(i.endedAt()), i.rev(), time(i.startedAt()))));
    }

    /** 先把版本加一（这条 UPDATE 锁住实例行，0007 第 5 节"推进"的第一步），再读回新的版本 */
    @Override
    public Optional<Integer> lock(String instanceId) {
        Long id = key(instanceId);
        long changed = db.execute(Sql.update(Instance.class).set(r -> r.rev().to(r.rev().plus(val(1)))).where(r -> r.id().eq(id)));
        if (changed == 0) return Optional.empty();
        return Optional.of(db.one(from(Instance.class).where(r -> r.id().eq(id)).select(r -> r.rev())));
    }

    @Override
    public Optional<InstanceRecord> find(String instanceId) {
        Long id = key(instanceId);
        return db.optional(from(Instance.class).where(r -> r.id().eq(id)).selectAs(InstanceRow.class)).map(this::toRecord);
    }

    @Override
    public List<InstanceRecord> findAll(Collection<String> instanceIds) {
        if (instanceIds.isEmpty()) return List.of();
        List<Long> keys = instanceIds.stream().map(this::key).toList();
        return db.list(from(Instance.class).where(r -> r.id().in(keys)).selectAs(InstanceRow.class)).stream().map(this::toRecord).toList();
    }

    @Override
    public void update(InstanceRecord i, Instant at) {
        Long id = key(i.id());
        db.execute(Sql.update(Instance.class).set(r -> {
            r.title().to(i.title());
            r.summary().to(JsonColumns.writeSummary(i.summary()));
            r.status().to(i.status());
            r.result().to(i.result());
            r.current().to(JsonColumns.writeCurrent(i.current()));
            r.variables().to(JsonColumns.write(i.variables()));
            r.state().to(JsonColumns.write(i.state()));
            r.endedAt().to(time(i.endedAt()));
            r.updatedAt().to(time(at));
        }).where(r -> r.id().eq(id)).expect(1));
    }

    private InstanceRecord toRecord(InstanceRow r) {
        return new InstanceRecord(text(r.id()), r.processKey(), text(r.definitionId()), r.version(), r.businessKey(), r.title(),
            r.priority() == null ? 0 : r.priority(), JsonColumns.summary(r.summary()), r.status(), r.result(), JsonColumns.current(r.current()),
            JsonColumns.variables(r.variables()), JsonColumns.runState(r.state()), r.startedBy(), r.startedByName(), instant(r.startedAt()),
            instant(r.endedAt()), r.rev());
    }
}
