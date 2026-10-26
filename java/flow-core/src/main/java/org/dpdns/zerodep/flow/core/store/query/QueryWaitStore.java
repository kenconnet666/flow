package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.store.WaitStore;
import org.dpdns.zerodep.query.exec.Database;

import java.util.List;

import static org.dpdns.zerodep.query.Sql.deleteFrom;
import static org.dpdns.zerodep.query.Sql.from;
import static org.dpdns.zerodep.query.Sql.insertInto;

/** 等外部事件的默认存储：{@code flow_wait} 表，按（租户, 事件名, 业务单据编号）找 */
final class QueryWaitStore implements WaitStore {
    private final Database db;

    QueryWaitStore(Database db) { this.db = db; }

    @Override
    public void insert(NewWait wait) {
        db.execute(insertInto(FlowTables.Wait.class).values(wait));
    }

    @Override
    public List<Wait> ofInstance(Long instanceId, String event) {
        return db.list(from(FlowTables.Wait.class).where(r -> { r.instanceId().eq(instanceId); r.event().eq(event); })
            .orderBy(r -> r.id()).selectAs(Wait.class));
    }

    @Override
    public List<Wait> ofBusinessKey(String businessKey, String event) {
        return db.list(from(FlowTables.Wait.class).where(r -> { r.event().eq(event); r.businessKey().eq(businessKey); })
            .orderBy(r -> r.id()).selectAs(Wait.class));
    }

    @Override
    public boolean remove(Long waitId) {
        return db.execute(deleteFrom(FlowTables.Wait.class).where(r -> r.id().eq(waitId))) == 1;
    }

    @Override
    public void removeForInstance(Long instanceId) {
        db.execute(deleteFrom(FlowTables.Wait.class).where(r -> r.instanceId().eq(instanceId)));
    }
}
