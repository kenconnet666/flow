package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.store.DefinitionStore.StoredDefinition;
import org.dpdns.zerodep.flow.core.store.FormStore.StoredForm;
import org.dpdns.zerodep.flow.core.store.InstanceStore.InstanceRecord;
import org.dpdns.zerodep.flow.model.definition.Definitions;
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
    private final EngineContext context;
    private final Map<String, LoadedDefinition> loaded = new ConcurrentHashMap<>();

    public DefinitionCache(EngineContext context) { this.context = context; }

    /** 实例按哪个版本走 */
    public LoadedDefinition of(InstanceRecord instance) {
        LoadedDefinition found = loaded.get(instance.definitionId());
        if (found != null) return found;
        return of(context.stores().definitions().byId(instance.definitionId())
            .orElseThrow(() -> FlowErrors.notFound("definition", instance.definitionId())));
    }

    /** 某个存着的版本 */
    public LoadedDefinition of(StoredDefinition stored) {
        return loaded.computeIfAbsent(stored.id(), id -> load(stored));
    }

    private LoadedDefinition load(StoredDefinition stored) {
        ProcessGraph graph = ProcessGraph.of(Definitions.parse(stored.model()));
        if (stored.formKey() == null) return new LoadedDefinition(stored, graph, null, null);
        StoredForm form = context.stores().forms().version(stored.formKey(), stored.formVersion())
            .orElseThrow(() -> FlowErrors.notFound("form", stored.formKey() + "@" + stored.formVersion()));
        return new LoadedDefinition(stored, graph, form, FormManifest.parse(Json.read(form.manifest())));
    }
}
