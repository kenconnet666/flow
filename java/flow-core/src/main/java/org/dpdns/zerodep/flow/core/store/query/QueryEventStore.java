package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.store.EventStore;
import org.dpdns.zerodep.flow.core.store.query.FlowTables.Event;
import org.dpdns.zerodep.flow.model.runtime.FlowEvent;
import org.dpdns.zerodep.query.exec.Database;

import java.util.ArrayList;
import java.util.List;

import static org.dpdns.zerodep.query.Sql.from;
import static org.dpdns.zerodep.query.Sql.insertInto;
import static org.dpdns.zerodep.query.Sql.orders;

/** 流转记录的默认存储：{@code flow_event} 表，只追加。流转记录（{@link FlowEvent}）就是表的一行 */
final class QueryEventStore implements EventStore {
    private final Database db;

    QueryEventStore(Database db) { this.db = db; }

    @Override
    public List<FlowEvent> append(List<FlowEvent> events) {
        if (events.isEmpty()) return events;
        List<Long> ids = db.insertKeys(insertInto(Event.class).values(events));
        List<FlowEvent> saved = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) saved.add(events.get(i).withId(ids.get(i)));   // insertKeys 按行的先后给编号
        return saved;
    }

    @Override
    public List<FlowEvent> timeline(Long instanceId) {
        return db.list(from(Event.class).where(r -> r.instanceId().eq(instanceId)).orderBy(r -> orders(r.instanceRev(), r.id()))
            .selectAs(FlowEvent.class));
    }
}
