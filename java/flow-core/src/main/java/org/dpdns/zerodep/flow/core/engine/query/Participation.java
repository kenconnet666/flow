package org.dpdns.zerodep.flow.core.engine.query;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.FlowErrors;
import org.dpdns.zerodep.flow.core.engine.assignee.CandidateRules;
import org.dpdns.zerodep.flow.core.engine.store.Archives;
import org.dpdns.zerodep.flow.core.engine.store.WorkItems;
import org.dpdns.zerodep.flow.core.entity.CcEntity;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.entity.WorkItemEntity;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.identity.Viewing;

import java.util.List;

/**
 * 操作人与一个实例的关系，交给 {@code ViewPolicy} 判断能不能看。实例在热表里找不到时看归档表（归档的实例照样能打开）；
 * 参与过没有在第一次问时才查，查一次记下。
 */
public class Participation implements Viewing {
    private final EngineContext context;
    private final Actor actor;
    private final InstanceEntity instance;
    private final boolean archived;
    private Boolean participant;

    private Participation(EngineContext context, Actor actor, InstanceEntity instance, boolean archived) {
        this.context = context;
        this.actor = actor;
        this.instance = instance;
        this.archived = archived;
    }

    /** 操作人与这个实例；实例不存在时报 {@code flow.not-found} */
    public static Participation of(EngineContext context, Actor actor, Long instanceId) {
        EasyEntityQuery db = context.getDb();
        InstanceEntity hot = db.queryable(InstanceEntity.class).whereById(instanceId).singleOrNull();
        if (hot != null) return new Participation(context, actor, hot, false);
        InstanceEntity archived = Archives.query(db, InstanceEntity.class).whereById(instanceId)
            .singleNotNull(() -> FlowErrors.notFound("instance", instanceId));
        return new Participation(context, actor, archived, true);
    }

    @Override public Long getInstanceId() { return instance.getId(); }

    @Override public String getProcessKey() { return instance.getProcessKey(); }

    @Override public Long getStartedBy() { return instance.getStartedBy(); }

    @Override
    public boolean isParticipant() {
        if (participant == null) participant = findParticipation();
        return participant;
    }

    private boolean findParticipation() {
        Long me = actor.getId();
        if (me.equals(instance.getStartedBy()) || context.getOrg().administrators().contains(me)) return true;
        EasyEntityQuery db = context.getDb();
        Long id = instance.getId();
        boolean handled = Archives.from(db, WorkItemEntity.class, archived).where(w -> {
            w.instanceId().eq(id);
            w.or(() -> { w.assigneeId().eq(me); w.completedBy().eq(me); w.fromUserId().eq(me); w.designatorId().eq(me); });
        }).any();
        if (handled) return true;
        if (Archives.from(db, CcEntity.class, archived).where(c -> { c.instanceId().eq(id); c.recipientId().eq(me); }).any()) return true;
        if (Archives.from(db, EventEntity.class, archived).where(e -> { e.instanceId().eq(id); e.actorId().eq(me); }).any()) return true;
        // 收件箱里的待办：按角色、部门匹配候选（候选只在待办打开时存在，归档的实例没有）
        if (archived) return false;
        List<Long> open = db.queryable(WorkItemEntity.class).where(w -> { w.instanceId().eq(id); w.state().eq(WorkItemEntity.OPEN); w.assigneeId().isNull(); })
            .select(w -> w.id()).toList();
        return open.stream().anyMatch(item -> CandidateRules.isCandidate(actor, WorkItems.candidates(db, item)));
    }
}
