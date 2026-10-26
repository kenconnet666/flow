package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.id.IdStrategy;
import org.dpdns.zerodep.flow.core.store.FormDataStore;
import org.dpdns.zerodep.flow.core.store.query.FlowTables.FormData;
import org.dpdns.zerodep.query.Sql;
import org.dpdns.zerodep.query.exec.Database;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import static org.dpdns.zerodep.query.Sql.from;
import static org.dpdns.zerodep.query.Sql.insertInto;
import static org.dpdns.zerodep.query.Sql.val;

/** 实例上表单的值的默认存储：{@code flow_form_data} 表，每个实例一行 */
final class QueryFormDataStore extends StoreSupport implements FormDataStore {
    QueryFormDataStore(Database db, IdStrategy ids, Supplier<String> tenant) { super(db, ids, tenant); }

    /** 表里的一行 */
    record FormDataRow(String tenantId, Long instanceId, Long formVersionId, String data, Integer rev, LocalDateTime updatedAt) { }

    @Override
    public Optional<StoredFormData> read(String instanceId) {
        Long id = key(instanceId);
        return db.optional(from(FormData.class).where(r -> r.instanceId().eq(id)).selectAs(FormDataRow.class))
            .map(r -> new StoredFormData(text(r.formVersionId()), JsonColumns.formValues(r.data())));
    }

    @Override
    public void insert(String instanceId, String formVersionId, FormValues values, Instant at) {
        db.execute(insertInto(FormData.class).values(new FormDataRow(tenant.get(), key(instanceId), key(formVersionId),
            JsonColumns.write(values), 0, time(at))));
    }

    @Override
    public void update(String instanceId, FormValues values, Instant at) {
        Long id = key(instanceId);
        db.execute(Sql.update(FormData.class).set(r -> {
            r.data().to(JsonColumns.write(values));
            r.updatedAt().to(time(at));
            r.rev().to(r.rev().plus(val(1)));
        }).where(r -> r.instanceId().eq(id)).expect(1));
    }
}
