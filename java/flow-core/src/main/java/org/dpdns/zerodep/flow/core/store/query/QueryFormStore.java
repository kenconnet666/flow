package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.id.IdStrategy;
import org.dpdns.zerodep.flow.core.store.DefinitionStore;
import org.dpdns.zerodep.flow.core.store.FormStore;
import org.dpdns.zerodep.flow.core.store.query.FlowTables.Form;
import org.dpdns.zerodep.flow.core.store.query.FlowTables.FormVersion;
import org.dpdns.zerodep.query.exec.Database;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import static org.dpdns.zerodep.query.Sql.from;
import static org.dpdns.zerodep.query.Sql.insertInto;
import static org.dpdns.zerodep.query.Sql.update;
import static org.dpdns.zerodep.query.Sql.val;

/** 表单的默认存储：{@code flow_form}（表单本身）与 {@code flow_form_version}（发布的不可变版本） */
final class QueryFormStore extends StoreSupport implements FormStore {
    QueryFormStore(Database db, IdStrategy ids, Supplier<String> tenant) { super(db, ids, tenant); }

    /** flow_form 的一行 */
    record FormRow(String tenantId, Long id, String formKey, String name, String category, String status, Integer latestVersion,
                   Integer rev, LocalDateTime createdAt, LocalDateTime updatedAt, String updatedBy) { }

    /** flow_form_version 的一行 */
    record FormVersionRow(String tenantId, Long id, String formKey, Integer version, String clientSource, String clientCompiled,
                          String compilerVersion, String manifest, String serverLanguage, String serverSource, String note,
                          String publishedBy, String publishedByName, LocalDateTime publishedAt) { }

    /** 读版本时只取引擎用到的列（源码可能很大，不读） */
    record FormVersionKey(Long id, String formKey, Integer version, String manifest) { }

    @Override
    public StoredForm publish(Publication p) {
        return db.transaction(() -> {
            String t = tenant.get();
            Optional<FormRow> existing = db.optional(from(Form.class)
                .where(r -> { r.tenantId().eq(t); r.formKey().eq(p.formKey()); }).selectAs(FormRow.class));
            int version;
            if (existing.isEmpty()) {
                version = 1;
                db.execute(insertInto(Form.class).values(new FormRow(t, newId("flow_form"), p.formKey(), p.name(), p.category(),
                    DefinitionStore.StoredProcess.ACTIVE, version, 0, time(p.at()), time(p.at()), p.publishedBy())));
            } else {
                FormRow row = existing.get();
                version = row.latestVersion() + 1;
                db.execute(update(Form.class).set(r -> {
                    r.latestVersion().to(version);
                    r.name().to(p.name());
                    r.category().to(p.category());
                    r.updatedAt().to(time(p.at()));
                    r.updatedBy().to(p.publishedBy());
                    r.rev().to(r.rev().plus(val(1)));
                }).where(r -> { r.id().eq(row.id()); r.latestVersion().eq(row.latestVersion()); }).expect(1));
            }
            Long id = newId("flow_form_version");
            db.execute(insertInto(FormVersion.class).values(new FormVersionRow(t, id, p.formKey(), version, p.clientSource(),
                p.clientCompiled(), p.compilerVersion(), p.manifest(), p.serverLanguage(), p.serverSource(), p.note(), p.publishedBy(),
                p.publishedByName(), time(p.at()))));
            return new StoredForm(text(id), p.formKey(), version, p.manifest());
        });
    }

    @Override
    public Optional<StoredForm> version(String formKey, int version) {
        String t = tenant.get();
        return db.optional(from(FormVersion.class).where(r -> { r.tenantId().eq(t); r.formKey().eq(formKey); r.version().eq(version); })
            .selectAs(FormVersionKey.class)).map(this::form);
    }

    @Override
    public Optional<StoredForm> latest(String formKey) {
        String t = tenant.get();
        return db.optional(from(FormVersion.class).where(r -> { r.tenantId().eq(t); r.formKey().eq(formKey); })
            .orderBy(r -> r.version().desc()).limit(1).selectAs(FormVersionKey.class)).map(this::form);
    }

    @Override
    public Optional<StoredForm> byId(String formVersionId) {
        Long id = key(formVersionId);
        return db.optional(from(FormVersion.class).where(r -> r.id().eq(id)).selectAs(FormVersionKey.class)).map(this::form);
    }

    private StoredForm form(FormVersionKey r) { return new StoredForm(text(r.id()), r.formKey(), r.version(), r.manifest()); }
}
