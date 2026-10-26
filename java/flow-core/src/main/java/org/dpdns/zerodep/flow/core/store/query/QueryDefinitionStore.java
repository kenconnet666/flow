package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.store.DefinitionStore;
import org.dpdns.zerodep.flow.core.store.query.FlowTables.Definition;
import org.dpdns.zerodep.flow.core.store.query.FlowTables.Process;
import org.dpdns.zerodep.query.exec.Database;

import java.time.Instant;
import java.util.Optional;

import static org.dpdns.zerodep.query.Sql.from;
import static org.dpdns.zerodep.query.Sql.insertInto;
import static org.dpdns.zerodep.query.Sql.update;
import static org.dpdns.zerodep.query.Sql.val;

/** 流程定义的默认存储：{@code flow_process}（流程本身）与 {@code flow_definition}（发布的不可变版本） */
final class QueryDefinitionStore implements DefinitionStore {
    private final Database db;

    QueryDefinitionStore(Database db) { this.db = db; }

    /** 新建流程时插入的一行 */
    record NewProcess(String processKey, String name, String category, String status, Integer latestVersion, Integer rev,
                      Instant createdAt, String updatedBy) { }

    /** 发布的一个版本（插入的一行；编号由数据库自增） */
    record NewDefinition(String processKey, Integer version, String model, String formKey, Integer formVersion, String note,
                         String publishedBy, String publishedByName, Instant publishedAt) { }

    /** 第一次发布时建上流程本身；之后最新版本号加一（只在最新版本还是读到的那个时改到：两个人同时发布，后到的失败） */
    @Override
    public StoredDefinition publish(Publication p) {
        return db.transaction(() -> {
            int version = process(p.processKey()).map(existing -> {
                int next = existing.latestVersion() + 1;
                db.execute(update(Process.class).set(r -> {
                    r.latestVersion().to(next);
                    r.name().to(p.name());
                    r.category().to(p.category());
                    r.updatedBy().to(p.publishedBy());
                    r.rev().to(r.rev().plus(val(1)));
                }).where(r -> { r.processKey().eq(p.processKey()); r.latestVersion().eq(existing.latestVersion()); }).expect(1));
                return next;
            }).orElseGet(() -> {
                db.execute(insertInto(Process.class).values(new NewProcess(p.processKey(), p.name(), p.category(),
                    StoredProcess.ACTIVE, 1, 0, p.at(), p.publishedBy())));
                return 1;
            });
            NewDefinition row = new NewDefinition(p.processKey(), version, p.model(), p.formKey(), p.formVersion(),
                p.note(), p.publishedBy(), p.publishedByName(), p.at());
            Long id = db.<Long>insertKeys(insertInto(Definition.class).values(row)).get(0);
            return new StoredDefinition(id, p.processKey(), version, p.model(), p.formKey(), p.formVersion());
        });
    }

    @Override
    public Optional<StoredProcess> process(String processKey) {
        return db.optional(from(Process.class).where(r -> r.processKey().eq(processKey)).selectAs(StoredProcess.class));
    }

    @Override
    public Optional<StoredDefinition> latest(String processKey) {
        return db.optional(from(Definition.class).where(r -> r.processKey().eq(processKey)).orderBy(r -> r.version().desc()).limit(1)
            .selectAs(StoredDefinition.class));
    }

    @Override
    public Optional<StoredDefinition> byId(Long definitionId) {
        return db.optional(from(Definition.class).where(r -> r.id().eq(definitionId)).selectAs(StoredDefinition.class));
    }
}
