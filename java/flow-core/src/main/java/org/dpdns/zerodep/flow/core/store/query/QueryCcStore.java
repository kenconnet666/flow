package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.store.CcStore;
import org.dpdns.zerodep.flow.model.runtime.Cc;
import org.dpdns.zerodep.query.exec.Database;
import org.dpdns.zerodep.query.exec.Page;

import java.time.Instant;
import java.util.List;

import static org.dpdns.zerodep.query.Sql.from;
import static org.dpdns.zerodep.query.Sql.insertInto;
import static org.dpdns.zerodep.query.Sql.orders;
import static org.dpdns.zerodep.query.Sql.update;

/** 抄送的默认存储：{@code flow_cc} 表。"抄送我的"走（租户, 收到的人, 读的时间, 抄送时间）的索引 */
final class QueryCcStore implements CcStore {
    private final Database db;

    QueryCcStore(Database db) { this.db = db; }

    @Override
    public void insert(List<NewCc> ccs) {
        if (!ccs.isEmpty()) db.execute(insertInto(FlowTables.Cc.class).values(ccs));
    }

    @Override
    public Page<Cc> received(Long recipientId, int page, int size) {
        return db.page(from(FlowTables.Cc.class).where(r -> r.recipientId().eq(recipientId))
            .orderBy(r -> orders(r.readAt().desc().nullsFirst(), r.createdAt().desc(), r.id().desc()))
            .selectAs(Cc.class), page, size);
    }

    @Override
    public int markRead(Long instanceId, Long recipientId, Instant at) {
        return (int) db.execute(update(FlowTables.Cc.class).set(r -> r.readAt().to(at))
            .where(r -> { r.instanceId().eq(instanceId); r.recipientId().eq(recipientId); r.readAt().isNull(); }));
    }
}
