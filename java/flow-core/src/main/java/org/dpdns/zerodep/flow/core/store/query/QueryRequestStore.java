package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.store.RequestStore;
import org.dpdns.zerodep.query.exec.Database;

import java.util.Optional;

import static org.dpdns.zerodep.query.Sql.from;
import static org.dpdns.zerodep.query.Sql.insertInto;

/** 幂等记录的默认存储：{@code flow_request} 表，按（租户, 请求编号）唯一 */
final class QueryRequestStore implements RequestStore {
    private final Database db;

    QueryRequestStore(Database db) { this.db = db; }

    @Override
    public Optional<StoredRequest> find(String requestId) {
        return db.optional(from(FlowTables.Request.class).where(r -> r.requestId().eq(requestId)).selectAs(StoredRequest.class));
    }

    @Override
    public void save(NewRequest request) {
        db.execute(insertInto(FlowTables.Request.class).values(request));
    }
}
