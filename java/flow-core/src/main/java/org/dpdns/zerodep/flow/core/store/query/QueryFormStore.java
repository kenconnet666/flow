package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.store.DefinitionStore.StoredProcess;
import org.dpdns.zerodep.flow.core.store.FormStore;
import org.dpdns.zerodep.flow.core.store.query.FlowTables.Form;
import org.dpdns.zerodep.flow.core.store.query.FlowTables.FormVersion;
import org.dpdns.zerodep.query.exec.Database;

import java.time.Instant;
import java.util.Optional;

import static org.dpdns.zerodep.query.Sql.from;
import static org.dpdns.zerodep.query.Sql.insertInto;
import static org.dpdns.zerodep.query.Sql.update;
import static org.dpdns.zerodep.query.Sql.val;

/** 表单的默认存储：{@code flow_form}（表单本身）与 {@code flow_form_version}（发布的不可变版本） */
final class QueryFormStore implements FormStore {
    private final Database db;

    QueryFormStore(Database db) { this.db = db; }

    /** 新建表单时插入的一行 */
    record NewForm(String formKey, String name, String category, String status, Integer latestVersion, Integer rev,
                   Instant createdAt, Long updatedBy) { }

    /** 再次发布时表单本身要改的几列（属性名就是列名） */
    record FormChange(Integer latestVersion, String name, String category, Long updatedBy) { }

    /** 发布的一个版本（插入的一行；编号由数据库自增） */
    record NewFormVersion(String formKey, Integer version, String clientSource, String clientCompiled, String compilerVersion,
                          String manifest, String serverLanguage, String serverSource, String note, Long publishedBy,
                          String publishedByName, Instant publishedAt) { }

    /** 表单本身的最新版本号（只读这一列） */
    record LatestVersion(Integer latestVersion) { }

    /** 同流程定义：第一次发布时建上表单本身，之后最新版本号加一 */
    @Override
    public StoredForm publish(Publication p) {
        return db.transaction(() -> {
            Optional<LatestVersion> existing = db.optional(from(Form.class).where(r -> r.formKey().eq(p.formKey())).selectAs(LatestVersion.class));
            int version;
            if (existing.isPresent()) {
                int current = existing.get().latestVersion();
                version = current + 1;
                db.execute(update(Form.class)
                    .set(new FormChange(version, p.name(), p.category(), p.publishedBy()))
                    .set(r -> r.rev().to(r.rev().plus(val(1))))
                    .where(r -> { r.formKey().eq(p.formKey()); r.latestVersion().eq(current); }).expect(1));
            } else {
                version = 1;
                db.execute(insertInto(Form.class).values(new NewForm(p.formKey(), p.name(), p.category(),
                    StoredProcess.ACTIVE, version, 0, p.at(), p.publishedBy())));
            }
            NewFormVersion row = new NewFormVersion(p.formKey(), version, p.clientSource(), p.clientCompiled(),
                p.compilerVersion(), p.manifest(), p.serverLanguage(), p.serverSource(), p.note(), p.publishedBy(), p.publishedByName(), p.at());
            Long id = db.<Long>insertKeys(insertInto(FormVersion.class).values(row)).get(0);
            return new StoredForm(id, p.formKey(), version, p.manifest());
        });
    }

    /** 读版本时只取引擎用到的列（源码可能很大，不读） */
    @Override
    public Optional<StoredForm> version(String formKey, int version) {
        return db.optional(from(FormVersion.class).where(r -> { r.formKey().eq(formKey); r.version().eq(version); }).selectAs(StoredForm.class));
    }

    @Override
    public Optional<StoredForm> latest(String formKey) {
        return db.optional(from(FormVersion.class).where(r -> r.formKey().eq(formKey)).orderBy(r -> r.version().desc()).limit(1)
            .selectAs(StoredForm.class));
    }

    @Override
    public Optional<StoredForm> byId(Long formVersionId) {
        return db.optional(from(FormVersion.class).where(r -> r.id().eq(formVersionId)).selectAs(StoredForm.class));
    }

    @Override
    public Optional<FormClient> client(Long formVersionId) {
        return db.optional(from(FormVersion.class).where(r -> r.id().eq(formVersionId)).selectAs(FormClient.class));
    }
}
