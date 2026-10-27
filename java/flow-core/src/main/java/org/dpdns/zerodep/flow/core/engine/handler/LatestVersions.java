package org.dpdns.zerodep.flow.core.engine.handler;

import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.entity.DefinitionEntity;
import org.dpdns.zerodep.flow.core.entity.DraftEntity;
import org.dpdns.zerodep.flow.core.entity.FormEntity;
import org.dpdns.zerodep.flow.core.entity.FormVersionEntity;
import org.dpdns.zerodep.flow.core.entity.ProcessEntity;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;

import java.util.Map;

/**
 * 发布、存草稿前核对编辑时看到的最新版本，不覆盖别人的。新建时看到的是 0：标识已经有人发布过报 {@code reason=key-taken}；
 * 打开已有的改时看到的是当时最新的版本号，这期间别人又发布过报 {@code reason=newer-version}。都是 {@code flow.concurrent-modification}，
 * 参数带上现在的最新版本（latest）与谁发布的（publishedBy）。
 */
class LatestVersions {
    private final EngineContext context;

    LatestVersions(EngineContext context) {
        this.context = context;
    }

    /** kind 是 {@link DraftEntity#PROCESS} 或 {@link DraftEntity#FORM}；expectedLatest 为 null 时不核对（脚本、导入） */
    void check(String kind, String key, Integer expectedLatest) {
        if (expectedLatest == null) return;
        int latest = latest(kind, key);
        if (latest == expectedLatest) return;
        String publishedBy = latest == 0 ? null : publishedBy(kind, key, latest);
        throw new FlowException(FlowErrorCode.CONCURRENT_MODIFICATION, Map.of(
            "reason", expectedLatest == 0 ? "key-taken" : "newer-version",
            "latest", String.valueOf(latest),
            "publishedBy", publishedBy == null ? "" : publishedBy));
    }

    /** 已发布的最新版本号；没发布过为 0 */
    private int latest(String kind, String key) {
        Integer latest = DraftEntity.PROCESS.equals(kind)
            ? context.getDb().queryable(ProcessEntity.class).where(p -> p.processKey().eq(key)).select(p -> p.latestVersion()).singleOrNull()
            : context.getDb().queryable(FormEntity.class).where(f -> f.formKey().eq(key)).select(f -> f.latestVersion()).singleOrNull();
        return latest == null ? 0 : latest;
    }

    private String publishedBy(String kind, String key, int version) {
        return DraftEntity.PROCESS.equals(kind)
            ? context.getDb().queryable(DefinitionEntity.class).where(d -> { d.processKey().eq(key); d.version().eq(version); })
                .select(d -> d.publishedByName()).singleOrNull()
            : context.getDb().queryable(FormVersionEntity.class).where(f -> { f.formKey().eq(key); f.version().eq(version); })
                .select(f -> f.publishedByName()).singleOrNull();
    }
}
