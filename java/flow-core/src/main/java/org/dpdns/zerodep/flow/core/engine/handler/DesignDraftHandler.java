package org.dpdns.zerodep.flow.core.engine.handler;

import org.dpdns.zerodep.flow.core.command.SaveDesignDraftCommand;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.FlowErrors;
import org.dpdns.zerodep.flow.core.entity.DraftEntity;
import org.dpdns.zerodep.flow.core.spi.Actor;
import org.dpdns.zerodep.flow.core.view.definition.DesignDraftVO;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;

import java.util.List;
import java.util.Map;

/**
 * 设计中的草稿（0004 的 5.2）：保存、放弃、读。流程与表单共用 {@code flow_draft}，每个流程、表单一行。
 * <pre>
 *   第一次保存（不带 rev）   插一行；已经有人存过草稿时报冲突
 *   之后保存（带 rev）       带条件地更新（WHERE rev = 读到的）：别人在这之间存过时报冲突，不覆盖
 *   发布成功                 删掉这个流程、表单的草稿（Publishing）
 * </pre>
 * 冲突是 {@code flow.concurrent-modification}，参数里带上最后修改人，界面提示"某某刚改过"。
 */
public class DesignDraftHandler {
    private static final List<String> KINDS = List.of(DraftEntity.PROCESS, DraftEntity.FORM);

    private final EngineContext context;
    private final LatestVersions latestVersions;

    public DesignDraftHandler(EngineContext context) {
        this.context = context;
        this.latestVersions = new LatestVersions(context);
    }

    /** 保存，返回存好的（带新的 rev） */
    public DesignDraftVO save(Actor actor, SaveDesignDraftCommand command) {
        if (!KINDS.contains(command.getKind())) throw FlowErrors.invalid("kind", "unknown");
        if (command.getKey() == null || command.getKey().isBlank()) throw FlowErrors.invalid("key", "required");
        if (command.getContent() == null) throw FlowErrors.invalid("content", "required");
        // 新建的（不基于哪个版本）不能存到已发布的流程、表单名下：新建时填了别人的标识，打开那个流程的人会载入这份草稿
        if (command.getBaseVersion() == null) latestVersions.check(command.getKind(), command.getKey(), 0);
        return context.transaction(() -> {
            DesignDraftVO existing = find(command.getKind(), command.getKey());
            if (existing == null) insert(actor, command);
            else update(actor, command, existing);
            return find(command.getKind(), command.getKey());
        });
    }

    private void insert(Actor actor, SaveDesignDraftCommand command) {
        // 带着版本来却没有草稿：草稿已经发布或被放弃了，不能当成新的悄悄建一份
        if (command.getRev() != null) throw conflict(command, null);
        DraftEntity row = new DraftEntity();
        row.setKind(command.getKind());
        row.setTargetKey(command.getKey());
        row.setName(command.getName());
        row.setContent(command.getContent());
        row.setBaseVersion(command.getBaseVersion());
        row.setRev(0);
        row.setUpdatedBy(actor.getId());
        row.setUpdatedByName(actor.getName());
        context.getDb().insertable(row).executeRows();
    }

    private void update(Actor actor, SaveDesignDraftCommand command, DesignDraftVO existing) {
        if (!existing.getRev().equals(command.getRev())) throw conflict(command, existing);
        long changed = context.getDb().updatable(DraftEntity.class)
            .setColumns(d -> {
                d.name().set(command.getName());
                d.content().set(command.getContent());
                d.baseVersion().set(command.getBaseVersion());
                d.updatedBy().set(actor.getId());
                d.updatedByName().set(actor.getName());
                d.rev().increment();
            })
            .where(d -> { d.kind().eq(command.getKind()); d.targetKey().eq(command.getKey()); d.rev().eq(command.getRev()); })
            .executeRows();
        if (changed != 1) throw conflict(command, find(command.getKind(), command.getKey()));
    }

    /** 放弃草稿：删掉这一行（没有时什么都不做） */
    public void discard(String kind, String key) {
        context.transaction(() -> {
            delete(kind, key);
            return null;
        });
    }

    /** 删掉草稿；发布成功时在发布的事务里调 */
    public void delete(String kind, String key) {
        context.getDb().deletable(DraftEntity.class).allowDeleteStatement(true)
            .where(d -> { d.kind().eq(kind); d.targetKey().eq(key); })
            .executeRows();
    }

    /** 一个流程、表单的草稿；没有时为 null */
    public DesignDraftVO find(String kind, String key) {
        return context.getDb().queryable(DraftEntity.class)
            .where(d -> { d.kind().eq(kind); d.targetKey().eq(key); })
            .select(DesignDraftVO.class).firstOrNull();
    }

    /** 某一种的全部草稿（不带内容），新改的在前：列表上标"有草稿" */
    public List<DesignDraftVO> list(String kind) {
        return context.getDb().queryable(DraftEntity.class)
            .where(d -> d.kind().eq(kind))
            .orderBy(d -> d.updatedAt().desc())
            .select(DesignDraftVO.class, d -> d.FETCHER.allFieldsExclude(d.content()))
            .toList();
    }

    private static FlowException conflict(SaveDesignDraftCommand command, DesignDraftVO now) {
        return new FlowException(FlowErrorCode.CONCURRENT_MODIFICATION, Map.of(
            command.getKind(), command.getKey(),
            "updatedBy", now == null || now.getUpdatedByName() == null ? "" : now.getUpdatedByName()));
    }
}
