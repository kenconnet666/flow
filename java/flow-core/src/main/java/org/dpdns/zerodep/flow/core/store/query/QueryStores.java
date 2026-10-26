package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.id.IdStrategy;
import org.dpdns.zerodep.flow.core.store.Stores;
import org.dpdns.zerodep.query.exec.Database;

import java.util.function.Supplier;

/**
 * 存储的默认实现：用 query 读写建表脚本（db/flow/mysql）建的表，写法与索引按 docs/decisions/0007 的第 4、5 节。
 * <p>编号经 {@link IdStrategy} 换成库里的值，时间存 UTC 的本地时间值，JSON 列存 {@code Json.write} 写出的文字。
 * 每张表都带租户列：写入时取 {@code tenant}，按标识查（流程、表单、收件箱）时带上它，按编号查的不用。
 */
public final class QueryStores {
    private QueryStores() { }

    /** 六个默认存储，共用一个数据库实例；tenant 给出当前的租户，不用多租户时是 {@code () -> ""} */
    public static Stores create(Database db, IdStrategy ids, Supplier<String> tenant) {
        return new Stores(new QueryDefinitionStore(db, ids, tenant), new QueryFormStore(db, ids, tenant), new QueryFormDataStore(db, ids, tenant),
            new QueryInstanceStore(db, ids, tenant), new QueryWorkItemStore(db, ids, tenant), new QueryEventStore(db, ids, tenant));
    }
}
