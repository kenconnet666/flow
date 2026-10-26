package org.dpdns.zerodep.flow.core.store.query;

import org.dpdns.zerodep.flow.core.id.IdStrategy;
import org.dpdns.zerodep.query.exec.Database;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.function.Supplier;

/**
 * 默认存储的共同部分：数据库、主键策略、当前租户，以及编号与时间的换算。
 * 引擎里编号是字符串、时间是 {@link Instant}；库里编号是 BIGINT（主键策略决定）、时间是 UTC 的本地时间值（0001 的"UTC 时间"）。
 */
abstract class StoreSupport {
    final Database db;
    final IdStrategy ids;
    final Supplier<String> tenant;

    StoreSupport(Database db, IdStrategy ids, Supplier<String> tenant) {
        this.db = db;
        this.ids = ids;
        this.tenant = tenant;
    }

    /** 给这张表发一个新编号（库里的值） */
    Long newId(String table) { return key(ids.next(table)); }

    /** 引擎里的编号 → 库里的值；编号列现在都是 BIGINT，大雪花要 query 支持"列类型按配置决定"之后再放开 */
    Long key(String id) { return id == null ? null : (Long) ids.toDatabase(id); }

    /** 库里的值 → 引擎里的编号 */
    String text(Long value) { return value == null ? null : ids.fromDatabase(value); }

    /** 时刻 → 库里的 UTC 本地时间 */
    static LocalDateTime time(Instant at) { return at == null ? null : LocalDateTime.ofInstant(at, ZoneOffset.UTC); }

    /** 库里的 UTC 本地时间 → 时刻 */
    static Instant instant(LocalDateTime at) { return at == null ? null : at.toInstant(ZoneOffset.UTC); }
}
