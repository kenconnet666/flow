package org.dpdns.zerodep.flow.core.engine;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import org.dpdns.zerodep.flow.core.entity.FormVersionEntity;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.entity.ProcessVersionEntity;
import org.dpdns.zerodep.flow.model.definition.Definitions;
import org.dpdns.zerodep.flow.model.definition.GatewayPairs;
import org.dpdns.zerodep.flow.model.definition.ProcessGraph;
import org.dpdns.zerodep.flow.model.form.FormManifest;
import org.dpdns.zerodep.wire.Json;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 发布的版本解析一次就记住（按定义的编号）：每次推进都要读定义，而发布的版本不会再变。
 * 集群里别的节点发布新版本时不需要通知这里——新版本是新编号。
 */
public final class DefinitionCache {
    private final EasyEntityQuery db;
    private final Map<Long, LoadedDefinition> loaded = new ConcurrentHashMap<>();

    public DefinitionCache(EasyEntityQuery db) { this.db = db; }

    /** 实例按哪个版本走 */
    public LoadedDefinition of(InstanceEntity instance) { return byId(instance.getDefinitionId()); }

    /** 按编号取一个版本 */
    public LoadedDefinition byId(Long definitionId) {
        LoadedDefinition found = loaded.get(definitionId);
        if (found != null) return found;
        return of(db.queryable(ProcessVersionEntity.class).whereById(definitionId)
            .singleNotNull(() -> FlowErrors.notFound("definition", definitionId)));
    }

    /** 某个存着的版本 */
    public LoadedDefinition of(ProcessVersionEntity stored) {
        return loaded.computeIfAbsent(stored.getId(), id -> load(stored));
    }

    private LoadedDefinition load(ProcessVersionEntity stored) {
        ProcessGraph graph = ProcessGraph.of(Definitions.parse(stored.getModel()));
        GatewayPairs pairs = GatewayPairs.of(graph.definition());
        if (stored.getFormKey() == null) return new LoadedDefinition(stored, graph, null, null, pairs);
        FormVersionEntity form = db.queryable(FormVersionEntity.class)
            .where(f -> { f.formKey().eq(stored.getFormKey()); f.version().eq(stored.getFormVersion()); })
            .select(f -> f.FETCHER.id().formKey().version().manifest())
            .singleNotNull(() -> FlowErrors.notFound("form", stored.getFormKey() + "@" + stored.getFormVersion()));
        return new LoadedDefinition(stored, graph, form, FormManifest.parse(Json.read(form.getManifest())), pairs);
    }
}
