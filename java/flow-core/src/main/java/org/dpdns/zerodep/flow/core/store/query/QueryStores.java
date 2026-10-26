package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.store.Stores;
import org.dpdns.zerodep.query.exec.Database;

/**
 * 存储的默认实现：用 query 读写建表脚本（db/flow/mysql）建的表，写法与索引按 docs/decisions/0007 的第 4、5 节。
 * 数据库实例要用 {@link FlowDatabase#create} 建的（租户、修改时间由它统一处理）。
 */
public final class QueryStores {
    private QueryStores() { }

    /** 九个默认存储，共用一个数据库实例 */
    public static Stores create(Database db) {
        return new Stores(new QueryDefinitionStore(db), new QueryFormStore(db), new QueryFormDataStore(db),
            new QueryInstanceStore(db), new QueryWorkItemStore(db), new QueryEventStore(db), new QueryJobStore(db),
            new QueryCcStore(db), new QueryWaitStore(db));
    }
}
