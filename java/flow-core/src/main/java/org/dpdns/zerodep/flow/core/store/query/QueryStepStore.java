package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.store.StepStore;
import org.dpdns.zerodep.query.exec.Database;

import java.util.Optional;

import static org.dpdns.zerodep.query.Sql.deleteFrom;
import static org.dpdns.zerodep.query.Sql.from;
import static org.dpdns.zerodep.query.Sql.insertInto;

/** 处理之前的快照的默认存储：{@code flow_step} 表，每个实例一行 */
final class QueryStepStore implements StepStore {
    private final Database db;

    QueryStepStore(Database db) { this.db = db; }

    @Override
    public void save(StepSnapshot snapshot) {
        remove(snapshot.instanceId());
        db.execute(insertInto(FlowTables.Step.class).values(snapshot));
    }

    @Override
    public Optional<StepSnapshot> find(Long instanceId) {
        return db.optional(from(FlowTables.Step.class).where(r -> r.instanceId().eq(instanceId)).selectAs(StepSnapshot.class));
    }

    @Override
    public void remove(Long instanceId) {
        db.execute(deleteFrom(FlowTables.Step.class).where(r -> r.instanceId().eq(instanceId)));
    }
}
