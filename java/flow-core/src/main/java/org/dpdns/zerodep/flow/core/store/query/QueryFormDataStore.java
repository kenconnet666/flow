package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.store.FormDataStore;
import org.dpdns.zerodep.flow.core.store.query.FlowTables.FormData;
import org.dpdns.zerodep.query.Sql;
import org.dpdns.zerodep.query.exec.Database;

import java.util.Optional;

import static org.dpdns.zerodep.query.Sql.from;
import static org.dpdns.zerodep.query.Sql.insertInto;
import static org.dpdns.zerodep.query.Sql.val;

/** 实例上表单的值的默认存储：{@code flow_form_data} 表，每个实例一行 */
final class QueryFormDataStore implements FormDataStore {
    private final Database db;

    QueryFormDataStore(Database db) { this.db = db; }

    /** 发起时插入的一行 */
    record NewFormData(Long instanceId, Long formVersionId, FormValues values, Integer rev) { }

    @Override
    public Optional<StoredFormData> read(Long instanceId) {
        return db.optional(from(FormData.class).where(r -> r.instanceId().eq(instanceId)).selectAs(StoredFormData.class));
    }

    @Override
    public void insert(Long instanceId, Long formVersionId, FormValues values) {
        db.execute(insertInto(FormData.class).values(new NewFormData(instanceId, formVersionId, values, 0)));
    }

    @Override
    public void update(Long instanceId, FormValues values) {
        // 方法名 update 遮住了静态导入的 Sql.update，这里写全名
        db.execute(Sql.update(FormData.class).set(r -> {
            r.values().to(values);
            r.rev().to(r.rev().plus(val(1)));
        }).where(r -> r.instanceId().eq(instanceId)).expect(1));
    }
}
