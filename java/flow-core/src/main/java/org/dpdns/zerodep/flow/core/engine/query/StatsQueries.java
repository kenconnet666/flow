package org.dpdns.zerodep.flow.core.engine.query;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.proxy.sql.GroupKeys;
import com.easy.query.core.proxy.sql.Select;
import org.dpdns.zerodep.flow.core.db.FlowInterceptors;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.entity.JobEntity;
import org.dpdns.zerodep.flow.core.entity.WorkItemEntity;
import org.dpdns.zerodep.flow.core.entity.proxy.WorkItemEntityProxy;
import org.dpdns.zerodep.flow.core.view.stats.CountRow;
import org.dpdns.zerodep.flow.core.view.stats.HandlerStats;
import org.dpdns.zerodep.flow.core.view.stats.NodeRow;
import org.dpdns.zerodep.flow.core.view.stats.NodeStats;
import org.dpdns.zerodep.flow.core.view.stats.OpsStats;
import org.dpdns.zerodep.flow.core.view.stats.ProcessStats;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计（0002 §9）：各流程的实例数、各人工节点的停留与超时、处理人的工作量。
 * 都在库里分组算（个数、平均停留、超时的个数），取回来的只有每组一行。
 */
public class StatsQueries {
    private final EngineContext context;

    public StatsQueries(EngineContext context) {
        this.context = context;
    }

    /** 各流程的实例数：进行中（含暂停）、正常结束、终止与撤回 */
    public List<ProcessStats> processes() {
        List<CountRow> rows = context.getDb().queryable(InstanceEntity.class)
            .where(i -> i.status().ne(InstanceEntity.DRAFT))
            .groupBy(i -> GroupKeys.of(i.processKey(), i.status()))
            .select(CountRow.class, g -> Select.of(g.key1().as(CountRow.Fields.key1), g.key2().as(CountRow.Fields.key2),
                g.count().as(CountRow.Fields.count)))
            .toList();
        Map<String, ProcessStats> out = new LinkedHashMap<>();
        for (CountRow row : rows) {
            ProcessStats stats = out.computeIfAbsent(row.getKey1(), key -> {
                ProcessStats s = new ProcessStats();
                s.setProcessKey(key);
                return s;
            });
            switch (row.getKey2()) {
                case InstanceEntity.ACTIVE, InstanceEntity.SUSPENDED -> stats.setRunning(stats.getRunning() + row.getCount());
                case InstanceEntity.COMPLETED -> stats.setCompleted(stats.getCompleted() + row.getCount());
                default -> stats.setStopped(stats.getStopped() + row.getCount());
            }
        }
        return new ArrayList<>(out.values());
    }

    /** 一个流程各人工节点从 since 起处理完的待办：个数、平均停留、超时比例 */
    public List<NodeStats> nodes(String processKey, Instant since) {
        // 在库里分组算：一个月处理完的待办可能有几万条，不取回来
        List<NodeRow> rows = context.getDb().queryable(WorkItemEntity.class)
            .where(w -> { w.processKey().eq(processKey); w.state().eq(WorkItemEntity.DONE); w.completedAt().ge(since); })
            .groupBy(w -> GroupKeys.of(w.node(), w.nodeName()))
            .select(NodeRow.class, g -> {
                WorkItemEntityProxy w = g.groupTable();
                // Instant 列在代理上是任意类型，asLocalDateTime 只为让编译器认作时间（生成的仍是原列）
                return Select.of(g.key1().as(NodeRow.Fields.node), g.key2().as(NodeRow.Fields.nodeName), g.count().as(NodeRow.Fields.done),
                    w.createdAt().asLocalDateTime().duration(w.completedAt().asLocalDateTime()).toSeconds().avg().as(NodeRow.Fields.averageSeconds),
                    w.id().count().filter(() -> w.dueAt().isNotNull()).as(NodeRow.Fields.withDue),
                    w.id().count().filter(() -> { w.dueAt().isNotNull(); w.completedAt().gt(w.dueAt()); }).as(NodeRow.Fields.overdue));
            })
            .toList();
        List<NodeStats> out = new ArrayList<>();
        for (NodeRow row : rows) {
            NodeStats stats = new NodeStats();
            stats.setNode(row.getNode());
            stats.setNodeName(row.getNodeName());
            stats.setDone(row.getDone());
            stats.setAverageHours(row.getAverageSeconds() == null ? 0 : row.getAverageSeconds().doubleValue() / 3600);
            stats.setOverdueRate(row.getWithDue() == 0 ? 0 : (double) row.getOverdue() / row.getWithDue());
            out.add(stats);
        }
        return out;
    }

    /** 处理人的工作量：从 since 起处理完的个数，加上现在手上打开着的个数，处理得多的在前 */
    public List<HandlerStats> handlers(Instant since) {
        Map<Long, HandlerStats> out = new LinkedHashMap<>();
        List<CountRow> done = context.getDb().queryable(WorkItemEntity.class)
            .where(w -> { w.state().eq(WorkItemEntity.DONE); w.completedAt().ge(since); w.completedBy().isNotNull(); })
            .groupBy(w -> GroupKeys.of(w.completedBy(), w.completedByName()))
            .select(CountRow.class, g -> Select.of(g.key1().as(CountRow.Fields.id), g.key2().as(CountRow.Fields.name), g.count().as(CountRow.Fields.count)))
            .toList();
        for (CountRow row : done) handler(out, row).setDone(row.getCount());
        List<CountRow> open = context.getDb().queryable(WorkItemEntity.class)
            .where(w -> { w.state().eq(WorkItemEntity.OPEN); w.assigneeId().isNotNull(); })
            .groupBy(w -> GroupKeys.of(w.assigneeId(), w.assigneeName()))
            .select(CountRow.class, g -> Select.of(g.key1().as(CountRow.Fields.id), g.key2().as(CountRow.Fields.name), g.count().as(CountRow.Fields.count)))
            .toList();
        for (CountRow row : open) handler(out, row).setOpen(row.getCount());
        List<HandlerStats> list = new ArrayList<>(out.values());
        list.sort((a, b) -> Long.compare(b.getDone(), a.getDone()));
        return list;
    }

    /** 运维看的数（全部租户：作业与实例都不按当前租户过滤） */
    public OpsStats ops(Instant now) {
        EasyEntityQuery db = context.getDb();
        OpsStats ops = new OpsStats();
        ops.setAt(now);
        ops.setDueJobs(db.queryable(JobEntity.class).noInterceptor(FlowInterceptors.TENANT)
            .where(j -> { j.state().eq(JobEntity.PENDING); j.dueAt().le(now); }).count());
        Instant oldest = db.queryable(JobEntity.class).noInterceptor(FlowInterceptors.TENANT)
            .where(j -> { j.state().eq(JobEntity.PENDING); j.dueAt().le(now); })
            .orderBy(j -> j.dueAt().asc()).select(j -> j.dueAt()).firstOrNull();
        ops.setOldestDueSeconds(oldest == null ? 0 : Duration.between(oldest, now).toSeconds());
        ops.setFailedJobs(db.queryable(JobEntity.class).noInterceptor(FlowInterceptors.TENANT).where(j -> j.state().eq(JobEntity.FAILED)).count());
        ops.setActiveInstances(db.queryable(InstanceEntity.class).noInterceptor(FlowInterceptors.TENANT)
            .where(i -> i.status().in(List.of(InstanceEntity.ACTIVE, InstanceEntity.SUSPENDED))).count());
        return ops;
    }

    private static HandlerStats handler(Map<Long, HandlerStats> out, CountRow row) {
        return out.computeIfAbsent(row.getId(), id -> {
            HandlerStats s = new HandlerStats();
            s.setUserId(id);
            s.setUserName(row.getName());
            return s;
        });
    }
}
