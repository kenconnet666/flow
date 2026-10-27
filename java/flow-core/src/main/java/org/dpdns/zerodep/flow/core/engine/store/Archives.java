package org.dpdns.zerodep.flow.core.engine.store;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.api.proxy.entity.select.EntityQueryable;
import com.easy.query.core.proxy.ProxyEntity;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import lombok.Data;
import org.dpdns.zerodep.flow.core.db.FlowInterceptors;
import org.dpdns.zerodep.flow.core.entity.CcEntity;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.entity.FormDataEntity;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.entity.WorkItemEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 归档（0007 第 6 节）：结束得早的实例连同待办、流转记录、表单的值、抄送整批搬进 {@code *_archive}（V11 建的，结构同原表，
 * 多一列 {@code archived_at}），在同一个事务里删掉原行。搬的时候不分租户（维护作业），按列名对上（读 information_schema），
 * 原表以后加列只要归档表同样加上。读归档的行用同一个实体，换个表名（{@link #query}，easy-query 的 asTable）。
 */
public class Archives {
    /** 跟着实例一起搬的表，与实例的编号连在哪一列；实例本身最后搬 */
    private static final List<Table> TABLES = List.of(
        new Table("flow_work_item", "instance_id"),
        new Table("flow_event", "instance_id"),
        new Table("flow_form_data", "instance_id"),
        new Table("flow_cc", "instance_id"),
        new Table("flow_instance", "id"));

    /** 实体 → 它的归档表 */
    private static final Map<Class<?>, String> ARCHIVE_TABLES = Map.of(
        InstanceEntity.class, "flow_instance_archive",
        WorkItemEntity.class, "flow_work_item_archive",
        EventEntity.class, "flow_event_archive",
        FormDataEntity.class, "flow_form_data_archive",
        CcEntity.class, "flow_cc_archive");

    /** 原表的列（不含 archived_at），按表名记住：一个进程里表结构不会变 */
    private static final Map<String, String> COLUMNS = new ConcurrentHashMap<>();

    private Archives() { }

    /** 一张跟着搬的表 */
    @Data
    private static class Table {
        private final String name;
        /** 实例的编号在哪一列 */
        private final String instanceColumn;
    }

    /**
     * 搬一批：结束时间早于 before 的实例，最多 batch 个（按结束时间先后），连同它们的待办、流转记录、表单的值、抄送。
     * 调用方开事务；返回搬了几个实例（不到 batch 说明搬完了）。
     */
    public static int archiveBatch(EasyEntityQuery db, Instant before, int batch, Instant now) {
        List<Long> ids = db.queryable(InstanceEntity.class).noInterceptor(FlowInterceptors.TENANT)
            .where(i -> { i.endedAt().isNotNull(); i.endedAt().lt(before); })
            .orderBy(i -> { i.endedAt().asc(); i.id().asc(); })
            .limit(batch)
            .selectColumn(i -> i.id()).toList();
        if (ids.isEmpty()) return 0;
        String in = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        for (Table table : TABLES) {
            String columns = columnsOf(db, table.getName());
            db.sqlExecute("INSERT INTO " + table.getName() + "_archive (" + columns + ", archived_at) SELECT " + columns + ", ? FROM " + table.getName()
                + " WHERE " + table.getInstanceColumn() + " IN (" + in + ")", List.of(now));
            db.sqlExecute("DELETE FROM " + table.getName() + " WHERE " + table.getInstanceColumn() + " IN (" + in + ")");
        }
        return ids.size();
    }

    /** 读热表，或者（archived 时）它的归档表 */
    public static <P extends ProxyEntity<P, T>, T extends ProxyEntityAvailable<T, P>> EntityQueryable<P, T> from(EasyEntityQuery db, Class<T> entity,
                                                                                                             boolean archived) {
        return archived ? query(db, entity) : db.queryable(entity);
    }

    /** 读归档表：同一个实体，换成它的归档表 */
    public static <P extends ProxyEntity<P, T>, T extends ProxyEntityAvailable<T, P>> EntityQueryable<P, T> query(EasyEntityQuery db, Class<T> entity) {
        String table = ARCHIVE_TABLES.get(entity);
        if (table == null) throw new IllegalArgumentException(entity.getSimpleName() + " has no archive table");
        return db.queryable(entity).asTable(table);
    }

    /** 一张表的列（按表里的先后），写成 SQL 的列清单 */
    private static String columnsOf(EasyEntityQuery db, String table) {
        return COLUMNS.computeIfAbsent(table, name -> {
            List<String> names = new ArrayList<>(db.sqlQuery(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION",
                String.class, List.of(name)));
            return names.stream().map(column -> "`" + column + "`").collect(Collectors.joining(", "));
        });
    }
}
