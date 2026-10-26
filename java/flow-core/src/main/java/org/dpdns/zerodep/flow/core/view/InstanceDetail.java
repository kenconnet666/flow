package org.dpdns.zerodep.flow.core.view;

import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.model.runtime.FlowEvent;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance;
import org.dpdns.zerodep.flow.model.runtime.WorkItem;

import java.util.List;

/**
 * 实例详情（0006 第 4 节的 {@code instance} 与 {@code timeline}）。
 *
 * @param instance  实例
 * @param form      表单的值
 * @param workItems 全部待办，按到达的先后
 * @param timeline  流转记录，按先后
 */
public record InstanceDetail(ProcessInstance instance, FormValues form, List<WorkItem> workItems, List<FlowEvent> timeline) { }
