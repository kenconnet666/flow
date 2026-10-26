package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.store.DefinitionStore.StoredDefinition;
import org.dpdns.zerodep.flow.core.store.FormStore.StoredForm;
import org.dpdns.zerodep.flow.model.definition.GatewayPairs;
import org.dpdns.zerodep.flow.model.definition.ProcessDefinition;
import org.dpdns.zerodep.flow.model.definition.ProcessGraph;
import org.dpdns.zerodep.flow.model.form.FormManifest;

/**
 * 解析好的一个发布版本：定义的图，加上它用的表单版本与字段清单（没有表单时这两个为 null）。发布的版本不会变，所以可以缓存。
 *
 * @param stored   存着的版本
 * @param graph    定义的图（节点、连线、泳道按标识查）
 * @param form     用的表单版本
 * @param manifest 表单的字段清单
 * @param pairs    并行、包容网关的分叉与汇合配对
 */
public record LoadedDefinition(StoredDefinition stored, ProcessGraph graph, StoredForm form, FormManifest manifest, GatewayPairs pairs) {
    /** 定义本身 */
    public ProcessDefinition model() { return graph.definition(); }

    /** 流程有没有表单 */
    public boolean hasForm() { return manifest != null; }
}
