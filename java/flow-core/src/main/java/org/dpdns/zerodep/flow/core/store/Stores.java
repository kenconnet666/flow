package org.dpdns.zerodep.flow.core.store;

/**
 * 引擎用到的十个存储（0006 第 6 节"存储"一行）：默认都来自 {@link QueryStores#create}，每个都能单独换。
 */
public record Stores(DefinitionStore definitions, FormStore forms, FormDataStore formData, InstanceStore instances,
                     WorkItemStore workItems, EventStore events, JobStore jobs, CcStore ccs, WaitStore waits, RequestStore requests) { }
