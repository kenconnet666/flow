package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.entity.FormVersionEntity;
import org.dpdns.zerodep.flow.core.entity.ProcessVersionEntity;
import org.dpdns.zerodep.flow.model.definition.GatewayPairs;
import org.dpdns.zerodep.flow.model.definition.ProcessDefinition;
import org.dpdns.zerodep.flow.model.definition.ProcessGraph;
import org.dpdns.zerodep.flow.model.form.FormManifest;

/**
 * 解析好的一个发布版本：定义的图，加上它用的表单的字段清单（没有表单时为 null）。发布的版本不会变，所以可以缓存。
 *
 * @param stored   存着的版本，连同导航 {@code form}
 * @param graph    定义的图（节点、连线、泳道按标识查）
 * @param manifest 表单的字段清单
 * @param pairs    并行、包容网关的分叉与汇合配对
 */
public record LoadedDefinition(ProcessVersionEntity stored, ProcessGraph graph, FormManifest manifest, GatewayPairs pairs) {
    /** 定义本身 */
    public ProcessDefinition model() { return graph.definition(); }

    /** 用的表单版本（不含源码与编译结果）；没有表单时为 null */
    public FormVersionEntity form() { return stored.getForm(); }

    /** 流程有没有表单 */
    public boolean hasForm() { return manifest != null; }
}
