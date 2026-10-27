package org.dpdns.zerodep.flow.core.engine.store;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.engine.FlowErrors;
import org.dpdns.zerodep.flow.core.entity.FormDataEntity;
import org.dpdns.zerodep.flow.core.entity.InstanceEntity;
import org.dpdns.zerodep.flow.core.view.instance.InstanceVO;

/**
 * 几个处理类都要做的实例与表单值的读写。推进一个实例前先 {@link #lock}：版本加一，各库都会锁住这一行，
 * 并发的推进在这里排队（0001 第 3 条）。
 */
public class Instances {
    private Instances() { }

    /** 版本加一锁住这一行，再读出来；不存在时报 {@code flow.not-found} */
    public static InstanceEntity lock(EasyEntityQuery db, Long instanceId) {
        InstanceEntity locked = lockIfExists(db, instanceId);
        if (locked == null) throw FlowErrors.notFound("instance", instanceId);
        return locked;
    }

    /** 同上，实例不存在时为 null（作业的实例可能已经没了） */
    public static InstanceEntity lockIfExists(EasyEntityQuery db, Long instanceId) {
        long locked = db.updatable(InstanceEntity.class).setColumns(i -> i.rev().increment()).whereById(instanceId).executeRows();
        return locked == 0 ? null : find(db, instanceId);
    }

    /** 读一行；不存在时报 {@code flow.not-found} */
    public static InstanceEntity find(EasyEntityQuery db, Long instanceId) {
        return db.queryable(InstanceEntity.class).whereById(instanceId).singleNotNull(() -> FlowErrors.notFound("instance", instanceId));
    }

    /** 给前端的样子 */
    public static InstanceVO view(EasyEntityQuery db, Long instanceId) {
        return db.queryable(InstanceEntity.class).whereById(instanceId).select(InstanceVO.class).singleNotNull();
    }

    /** 实例上表单的值；流程没有表单时是空的 */
    public static FormValues form(EasyEntityQuery db, Long instanceId) {
        FormDataEntity data = db.queryable(FormDataEntity.class).whereById(instanceId).singleOrNull();
        return data == null ? FormValues.empty() : data.getValues();
    }

    /** 处理时整份写回表单的值（调用前已锁住实例行） */
    public static void saveForm(EasyEntityQuery db, Long instanceId, FormValues values) {
        db.updatable(FormDataEntity.class)
            .setColumns(f -> { f.values().set(values); f.rev().increment(); })
            .whereById(instanceId)
            .executeRows();
    }
}
