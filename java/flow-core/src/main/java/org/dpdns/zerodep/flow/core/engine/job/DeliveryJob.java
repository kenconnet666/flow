package org.dpdns.zerodep.flow.core.engine.job;

import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.EventDispatcher;
import org.dpdns.zerodep.flow.core.engine.store.Instances;
import org.dpdns.zerodep.flow.core.engine.store.WorkItems;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.entity.JobEntity;
import org.dpdns.zerodep.flow.core.spi.FlowListener;
import org.dpdns.zerodep.flow.core.spi.FlowNotice;
import org.dpdns.zerodep.flow.core.spi.JobHandler;
import org.dpdns.zerodep.flow.core.spi.Notification;
import org.dpdns.zerodep.flow.core.spi.NotificationChannel;
import org.dpdns.zerodep.flow.core.view.instance.FlowEventVO;
import org.dpdns.zerodep.flow.core.view.instance.InstanceVO;
import org.dpdns.zerodep.flow.model.definition.ProcessGraph;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 发件箱的投递（{@code deliver}）：把一份 {@link OutboxEntry} 投给可靠的监听或渠道。记录与待办按编号从库里读，实例是投的那时的样子。
 * 抛错时整份重投（已经投过的那几条会再收到一次，所以接入方按记录的编号去重），重试用完标成失败，进管理端的异常处理：重试或放弃。
 * 实例结束时这种作业不删（结束那一步的记录也要投出去）。
 */
public class DeliveryJob implements JobHandler {
    public static final String KIND = "deliver";
    /** 最多试几次：外部系统可能要一阵子才恢复，比内置作业多两次（1、4、9、16 分钟后重试） */
    public static final int MAX_ATTEMPTS = 5;

    private final EngineContext context;

    public DeliveryJob(EngineContext context) { this.context = context; }

    @Override
    public String kind() { return KIND; }

    @Override
    public Next run(JobEntity job, Instant now) {
        OutboxEntry entry = OutboxEntry.fromPayload(job.getPayload());
        InstanceVO instance = Instances.view(context.getDb(), job.getInstanceId());
        ProcessGraph graph = context.getDefinitions().of(Instances.find(context.getDb(), job.getInstanceId())).getGraph();
        EventDispatcher dispatcher = context.getDispatcher();
        if (entry.getListener() != null) {
            FlowListener listener = dispatcher.reliableListener(entry.getListener());
            for (FlowEventVO event : events(entry.getEvents()).values()) {
                listener.on(new FlowNotice(event, instance, EventDispatcher.metaOf(graph, event.getNode())));
            }
            return null;
        }
        NotificationChannel channel = dispatcher.reliableChannel(entry.getChannel());
        Map<Long, FlowEventVO> events = events(entry.getNotifications().stream().map(OutboxEntry.Note::getEvent).toList());
        for (OutboxEntry.Note note : entry.getNotifications()) {
            channel.send(new Notification(note.getKind(), note.getTemplate(), note.getRecipients(), events.get(note.getEvent()), instance,
                note.getWorkItem() == null ? null : WorkItems.find(context.getDb(), note.getWorkItem())));
        }
        return null;
    }

    /** 按编号读流转记录，按编号的先后 */
    private Map<Long, FlowEventVO> events(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return context.getDb().queryable(EventEntity.class).where(e -> e.id().in(ids)).orderBy(e -> e.id().asc())
            .select(FlowEventVO.class).toList().stream()
            .collect(Collectors.toMap(FlowEventVO::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }
}
