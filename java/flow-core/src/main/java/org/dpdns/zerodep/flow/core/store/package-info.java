/**
 * 存储接口（docs/decisions/0006 第 6 节"存储"一行）：引擎只通过这六个接口读写，每个都能单独替换（{@code FlowEngine.Builder} 上给）。
 * <pre>
 *   DefinitionStore   流程本身与发布的版本（flow_process、flow_definition）
 *   FormStore         表单本身与发布的版本（flow_form、flow_form_version）
 *   FormDataStore     实例上表单的值（flow_form_data）
 *   InstanceStore     流程实例（flow_instance）：推进前 lock 锁住这一行
 *   WorkItemStore     待办与候选（flow_work_item、flow_candidate）：状态的变化都是带条件的更新
 *   EventStore        流转记录（flow_event），只追加
 * </pre>
 * 接口里的记录（{@code InstanceRecord}、{@code StoredDefinition}……）就是引擎读写的样子；默认实现在 {@code store.query}。
 */
package org.dpdns.zerodep.flow.core.store;
