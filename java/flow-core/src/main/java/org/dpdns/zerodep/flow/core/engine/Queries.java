package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.command.InstanceDetail;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.store.FormDataStore.StoredFormData;
import org.dpdns.zerodep.flow.core.store.InstanceStore.InstanceRecord;
import org.dpdns.zerodep.flow.core.store.WorkItemStore.Candidate;
import org.dpdns.zerodep.flow.core.store.WorkItemStore.Member;
import org.dpdns.zerodep.flow.model.runtime.TodoItem;
import org.dpdns.zerodep.flow.model.runtime.WorkItem;
import org.dpdns.zerodep.query.exec.Page;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 查询（0006 第 4 节）：现在有收件箱与实例详情；已办、我发起的、抄送、进度、统计随场景补上。
 */
public final class Queries {
    private final EngineContext context;

    public Queries(EngineContext context) { this.context = context; }

    /**
     * 我的待办：落到我的，加上我、我的角色或部门能认领的（我没被排除），按到达时间从新到旧；
     * 每行带上实例的标题与摘要列（0006 第 4 节"待办的业务摘要列"）。
     */
    public Page<TodoItem> todo(Actor actor, int page, int size) {
        Map<String, Set<String>> memberships = new LinkedHashMap<>();
        memberships.put(Candidate.USER, Set.of(actor.id()));
        memberships.put(Candidate.ROLE, actor.roles());
        memberships.put(Candidate.DEPARTMENT, actor.departments());
        Page<WorkItem> items = context.stores().workItems().todo(new Member(actor.id(), memberships), page, size);

        Set<Long> instanceIds = items.rows().stream().map(WorkItem::instanceId).collect(Collectors.toSet());
        Map<Long, InstanceRecord> instances = context.stores().instances().findAll(instanceIds).stream()
            .collect(Collectors.toMap(InstanceRecord::id, Function.identity()));
        List<TodoItem> rows = items.rows().stream().map(item -> {
            InstanceRecord instance = instances.get(item.instanceId());
            return new TodoItem(item, instance == null ? null : instance.title(), instance == null ? Map.of() : instance.summary());
        }).toList();
        return new Page<>(rows, items.total(), items.page(), items.size());
    }

    /**
     * 实例详情：实例、表单的值、全部待办、流转记录。
     * 表单按查看人的字段权限过滤（0004 的 4.5、0005 的 {@code formView}）随表单组件一起补上，现在给全部字段。
     */
    public InstanceDetail instance(Long instanceId) {
        InstanceRecord instance = context.stores().instances().find(instanceId).orElseThrow(() -> FlowErrors.notFound("instance", instanceId));
        FormValues form = context.stores().formData().read(instanceId).map(StoredFormData::values).orElse(FormValues.empty());
        return new InstanceDetail(instance.view(), form, context.stores().workItems().ofInstance(instanceId),
            context.stores().events().timeline(instanceId));
    }
}
