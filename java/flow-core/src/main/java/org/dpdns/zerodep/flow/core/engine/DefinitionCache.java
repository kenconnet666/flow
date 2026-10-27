package org.dpdns.zerodep.flow.core.engine;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.api.proxy.entity.select.EntityQueryable;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.entity.ProcessEntity;
import org.dpdns.zerodep.flow.core.entity.ProcessVersionEntity;
import org.dpdns.zerodep.flow.core.entity.proxy.ProcessEntityProxy;
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
 * <p>版本总是连同它的表单一起读（导航 {@code form}；表单的源码与编译结果默认不读）。
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
        return of(db.queryable(ProcessVersionEntity.class).include(v -> v.form()).whereById(definitionId)
            .singleNotNull(() -> FlowErrors.notFound("definition", definitionId)));
    }

    /** 流程，连同最新发布的版本与它的表单：读出来的交给 {@link #latest} */
    public EntityQueryable<ProcessEntityProxy, ProcessEntity> processes() {
        return db.queryable(ProcessEntity.class).include(p -> p.latest(), v -> v.include(x -> x.form()));
    }

    /** 流程最新发布的版本；流程要经 {@link #processes()} 读出 */
    public LoadedDefinition latest(ProcessEntity process) {
        if (process.getLatest() == null) throw FlowErrors.notFound("process", process.getProcessKey());
        return of(process.getLatest());
    }

    private LoadedDefinition of(ProcessVersionEntity stored) {
        return loaded.computeIfAbsent(stored.getId(), id -> load(stored));
    }

    private static LoadedDefinition load(ProcessVersionEntity stored) {
        ProcessGraph graph = ProcessGraph.of(Definitions.parse(stored.getModel()));
        GatewayPairs pairs = GatewayPairs.of(graph.definition());
        if (stored.getFormKey() == null) return new LoadedDefinition(stored, graph, null, pairs);
        if (stored.getForm() == null) throw FlowErrors.notFound("form", stored.getFormKey() + "@" + stored.getFormVersion());
        return new LoadedDefinition(stored, graph, FormManifest.parse(Json.read(stored.getForm().getManifest())), pairs);
    }
}
