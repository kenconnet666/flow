package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.id.IdStrategy;
import org.dpdns.zerodep.flow.core.store.EventStore;
import org.dpdns.zerodep.flow.core.store.query.FlowTables.Event;
import org.dpdns.zerodep.flow.model.runtime.FlowEvent;
import org.dpdns.zerodep.query.exec.Database;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.dpdns.zerodep.query.Sql.from;
import static org.dpdns.zerodep.query.Sql.insertInto;
import static org.dpdns.zerodep.query.Sql.orders;

/** 流转记录的默认存储：{@code flow_event} 表，只追加 */
final class QueryEventStore extends StoreSupport implements EventStore {
    QueryEventStore(Database db, IdStrategy ids, Supplier<String> tenant) { super(db, ids, tenant); }

    /** flow_event 的一行；data 是 JSON 文字 */
    record EventRow(String tenantId, Long id, Long instanceId, Long workItemId, String type, String node, String nodeName, String edge,
                    String outcome, Integer instanceRev, String actorId, String actorName, LocalDateTime at, String comment, String data) { }

    @Override
    public void append(List<FlowEvent> events) {
        if (events.isEmpty()) return;
        String t = tenant.get();
        List<EventRow> rows = new ArrayList<>();
        for (FlowEvent e : events) {
            rows.add(new EventRow(t, key(e.id()), key(e.instanceId()), e.workItemId() == null ? null : key(e.workItemId()), e.type(), e.node(),
                e.nodeName(), e.edge(), e.outcome(), e.instanceRev(), e.actorId(), e.actorName(), time(e.at()), e.comment(),
                JsonColumns.writeEventData(e.data())));
        }
        db.execute(insertInto(Event.class).values(rows));
    }

    @Override
    public List<FlowEvent> timeline(String instanceId) {
        Long instance = key(instanceId);
        return db.list(from(Event.class).where(r -> r.instanceId().eq(instance)).orderBy(r -> orders(r.instanceRev(), r.id()))
            .selectAs(EventRow.class)).stream().map(r -> new FlowEvent(text(r.id()), text(r.instanceId()),
                r.workItemId() == null ? null : text(r.workItemId()), r.type(), r.node(), r.nodeName(), r.edge(), r.outcome(), r.instanceRev(),
                r.actorId(), r.actorName(), instant(r.at()), r.comment(), JsonColumns.eventData(r.data()))).toList();
    }
}
