package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.store.InstanceStore;
import org.dpdns.zerodep.flow.core.store.query.FlowTables.Instance;
import org.dpdns.zerodep.query.Sql;
import org.dpdns.zerodep.query.exec.Database;
import org.dpdns.zerodep.query.exec.Page;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.dpdns.zerodep.query.Sql.from;
import static org.dpdns.zerodep.query.Sql.insertInto;
import static org.dpdns.zerodep.query.Sql.val;

/** 流程实例的默认存储：{@code flow_instance} 表。实例的记录（{@link InstanceRecord}）就是表的一行，直接读写 */
final class QueryInstanceStore implements InstanceStore {
    private final Database db;

    QueryInstanceStore(Database db) { this.db = db; }

    @Override
    public InstanceRecord insert(InstanceRecord instance) {
        return db.<InstanceRecord>insertReturning(insertInto(Instance.class).values(instance)).get(0);
    }

    /** 先把版本加一（这条 UPDATE 锁住实例行，0007 第 5 节"推进"的第一步），再读回新的版本 */
    @Override
    public Optional<Integer> lock(Long instanceId) {
        long changed = db.execute(Sql.update(Instance.class).set(r -> r.rev().to(r.rev().plus(val(1)))).where(r -> r.id().eq(instanceId)));
        if (changed == 0) return Optional.empty();
        return Optional.of(db.one(from(Instance.class).where(r -> r.id().eq(instanceId)).select(r -> r.rev())));
    }

    @Override
    public Optional<InstanceRecord> find(Long instanceId) {
        return db.optional(from(Instance.class).where(r -> r.id().eq(instanceId)).selectAs(InstanceRecord.class));
    }

    @Override
    public List<InstanceRecord> findAll(Collection<Long> instanceIds) {
        if (instanceIds.isEmpty()) return List.of();
        return db.list(from(Instance.class).where(r -> r.id().in(instanceIds)).selectAs(InstanceRecord.class));
    }

    @Override
    public List<InstanceRecord> children(Long parentInstanceId, String parentNode, int parentRound) {
        return db.list(from(Instance.class)
            .where(r -> { r.parentInstanceId().eq(parentInstanceId); r.parentNode().eq(parentNode); r.parentRound().eq(parentRound); })
            .orderBy(r -> r.id()).selectAs(InstanceRecord.class));
    }

    @Override
    public List<InstanceRecord> activeChildren(Long parentInstanceId) {
        return db.list(from(Instance.class).where(r -> { r.parentInstanceId().eq(parentInstanceId); r.endedAt().isNull(); })
            .orderBy(r -> r.id()).selectAs(InstanceRecord.class));
    }

    /** 走（租户, 发起人, 发起时间）的索引 */
    @Override
    public Page<InstanceRecord> startedBy(Long userId, int page, int size) {
        return db.page(from(Instance.class).where(r -> r.startedBy().eq(userId))
            .orderBy(r -> Sql.orders(r.startedAt().desc(), r.id().desc())).selectAs(InstanceRecord.class), page, size);
    }

    /** 按编号整行写回（调用前已锁住这一行；版本列照记录里的写，不再加一） */
    @Override
    public void update(InstanceRecord instance) {
        db.execute(Sql.update(Instance.class).byId(instance));
    }
}
