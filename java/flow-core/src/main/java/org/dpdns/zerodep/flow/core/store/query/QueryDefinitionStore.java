package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.id.IdStrategy;
import org.dpdns.zerodep.flow.core.store.DefinitionStore;
import org.dpdns.zerodep.flow.core.store.query.FlowTables.Definition;
import org.dpdns.zerodep.flow.core.store.query.FlowTables.Process;
import org.dpdns.zerodep.query.exec.Database;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import static org.dpdns.zerodep.query.Sql.from;
import static org.dpdns.zerodep.query.Sql.insertInto;
import static org.dpdns.zerodep.query.Sql.update;
import static org.dpdns.zerodep.query.Sql.val;

/** 流程定义的默认存储：{@code flow_process}（流程本身）与 {@code flow_definition}（发布的不可变版本） */
final class QueryDefinitionStore extends StoreSupport implements DefinitionStore {
    QueryDefinitionStore(Database db, IdStrategy ids, Supplier<String> tenant) { super(db, ids, tenant); }

    /** flow_process 的一行（列名同表接口的方法名） */
    record ProcessRow(String tenantId, Long id, String processKey, String name, String category, String status, Integer latestVersion,
                      Integer rev, LocalDateTime createdAt, LocalDateTime updatedAt, String updatedBy) { }

    /** flow_definition 的一行 */
    record DefinitionRow(String tenantId, Long id, String processKey, Integer version, String model, String formKey, Integer formVersion,
                         String note, String publishedBy, String publishedByName, LocalDateTime publishedAt) { }

    @Override
    public StoredDefinition publish(Publication p) {
        return db.transaction(() -> {
            String t = tenant.get();
            Optional<ProcessRow> existing = db.optional(from(Process.class)
                .where(r -> { r.tenantId().eq(t); r.processKey().eq(p.processKey()); }).selectAs(ProcessRow.class));
            int version;
            if (existing.isEmpty()) {
                version = 1;
                db.execute(insertInto(Process.class).values(new ProcessRow(t, newId("flow_process"), p.processKey(), p.name(), p.category(),
                    StoredProcess.ACTIVE, version, 0, time(p.at()), time(p.at()), p.publishedBy())));
            } else {
                ProcessRow row = existing.get();
                version = row.latestVersion() + 1;
                // 只在最新版本还是读到的那个时改到：两个人同时发布，后到的失败
                db.execute(update(Process.class).set(r -> {
                    r.latestVersion().to(version);
                    r.name().to(p.name());
                    r.category().to(p.category());
                    r.updatedAt().to(time(p.at()));
                    r.updatedBy().to(p.publishedBy());
                    r.rev().to(r.rev().plus(val(1)));
                }).where(r -> { r.id().eq(row.id()); r.latestVersion().eq(row.latestVersion()); }).expect(1));
            }
            Long id = newId("flow_definition");
            db.execute(insertInto(Definition.class).values(new DefinitionRow(t, id, p.processKey(), version, p.model(), p.formKey(),
                p.formVersion(), p.note(), p.publishedBy(), p.publishedByName(), time(p.at()))));
            return new StoredDefinition(text(id), p.processKey(), version, p.model(), p.formKey(), p.formVersion());
        });
    }

    @Override
    public Optional<StoredProcess> process(String processKey) {
        String t = tenant.get();
        return db.optional(from(Process.class).where(r -> { r.tenantId().eq(t); r.processKey().eq(processKey); }).selectAs(ProcessRow.class))
            .map(r -> new StoredProcess(r.processKey(), r.name(), r.status(), r.latestVersion()));
    }

    @Override
    public Optional<StoredDefinition> latest(String processKey) {
        String t = tenant.get();
        return db.optional(from(Definition.class).where(r -> { r.tenantId().eq(t); r.processKey().eq(processKey); })
            .orderBy(r -> r.version().desc()).limit(1).selectAs(DefinitionRow.class)).map(this::definition);
    }

    @Override
    public Optional<StoredDefinition> byId(String definitionId) {
        Long id = key(definitionId);
        return db.optional(from(Definition.class).where(r -> r.id().eq(id)).selectAs(DefinitionRow.class)).map(this::definition);
    }

    private StoredDefinition definition(DefinitionRow r) {
        return new StoredDefinition(text(r.id()), r.processKey(), r.version(), r.model(), r.formKey(), r.formVersion());
    }
}
