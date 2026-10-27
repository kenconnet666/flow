package org.dpdns.zerodep.flow.core.engine.query;

import com.easy.query.core.proxy.sql.GroupKeys;
import com.easy.query.core.proxy.sql.Select;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.entity.WorkItemEntity;
import org.dpdns.zerodep.flow.core.view.stats.CountRow;
import org.dpdns.zerodep.flow.core.view.stats.HandlerStats;
import org.dpdns.zerodep.flow.core.view.stats.NodeStats;
import org.dpdns.zerodep.flow.core.view.stats.ProcessStats;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计（0002 §9）：各流程的实例数、各人工节点的停留与超时、处理人的工作量。
 * 计数在库里分组算；停留时长、超时比例取这段时间里处理完的待办在内存里算（只取几列）。
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
        List<WorkItemEntity> done = context.getDb().queryable(WorkItemEntity.class)
            .where(w -> { w.processKey().eq(processKey); w.state().eq(WorkItemEntity.DONE); w.completedAt().ge(since); })
            .select(w -> w.FETCHER.node().nodeName().createdAt().completedAt().dueAt())
            .toList();
        Map<String, List<WorkItemEntity>> byNode = new LinkedHashMap<>();
        for (WorkItemEntity item : done) byNode.computeIfAbsent(item.getNode(), n -> new ArrayList<>()).add(item);
        List<NodeStats> out = new ArrayList<>();
        byNode.forEach((node, items) -> {
            NodeStats stats = new NodeStats();
            stats.setNode(node);
            stats.setNodeName(items.get(0).getNodeName());
            stats.setDone(items.size());
            stats.setAverageHours(items.stream().mapToLong(i -> Duration.between(i.getCreatedAt(), i.getCompletedAt()).toSeconds()).average().orElse(0) / 3600);
            long withDue = items.stream().filter(i -> i.getDueAt() != null).count();
            long overdue = items.stream().filter(i -> i.getDueAt() != null && i.getCompletedAt().isAfter(i.getDueAt())).count();
            stats.setOverdueRate(withDue == 0 ? 0 : (double) overdue / withDue);
            out.add(stats);
        });
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

    private static HandlerStats handler(Map<Long, HandlerStats> out, CountRow row) {
        return out.computeIfAbsent(row.getId(), id -> {
            HandlerStats s = new HandlerStats();
            s.setUserId(id);
            s.setUserName(row.getName());
            return s;
        });
    }
}
