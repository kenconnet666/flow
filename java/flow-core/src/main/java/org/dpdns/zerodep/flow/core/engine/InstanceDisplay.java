package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.store.InstanceStore.InstanceRecord;
import org.dpdns.zerodep.flow.core.store.WorkItemStore;
import org.dpdns.zerodep.flow.model.Values;
import org.dpdns.zerodep.flow.model.definition.ProcessDefinition;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance.CurrentNode;
import org.dpdns.zerodep.flow.model.runtime.WorkItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 实例在列表上显示的三样东西，每一步结束时按最新的表单与变量重算（0005 第 2 节："每次提交时重新生成"）：
 * <ul>
 *   <li>标题：定义的 {@code title} 模板，花括号里的引用换成值，如 {@code "采购申请：{form.title}"} → "采购申请：办公桌"；</li>
 *   <li>摘要列：定义的 {@code summary} 里每个引用 → 值，收件箱直接显示，不用再查表单；</li>
 *   <li>正在处理的节点：还开着的待办在哪些节点上（去重），加上停着等的定时节点、异步的自动节点；实例结束了就是空的。</li>
 * </ul>
 */
final class InstanceDisplay {
    /** 标题模板里的占位：{form.title} */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)\\}");

    private InstanceDisplay() { }

    /**
     * 重算之后的实例。
     *
     * @param fixedTitle 发起时显式给的标题；为 null 时按模板生成
     * @param read       取引用的值
     */
    static InstanceRecord refresh(InstanceRecord instance, ProcessDefinition definition, String fixedTitle, Function<String, Object> read,
                                  WorkItemStore workItems) {
        String title = fixedTitle != null ? fixedTitle : title(definition, read);
        Map<String, Object> summary = new LinkedHashMap<>();
        for (String name : definition.summary()) summary.put(name, read.apply(name));
        return instance.withDisplay(title, summary, currentNodes(instance, definition, workItems));
    }

    /** 按模板生成标题；不写模板时用流程名 */
    private static String title(ProcessDefinition definition, Function<String, Object> read) {
        String template = definition.title();
        if (template == null || template.isBlank()) return definition.name();
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Object value = read.apply(m.group(1).trim());
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : Values.toText(value)));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** 还开着的待办所在的节点，按到达的先后、去重；再加上停着等的节点（定时、异步的自动节点） */
    private static List<CurrentNode> currentNodes(InstanceRecord instance, ProcessDefinition definition, WorkItemStore workItems) {
        // 结束了的没有正在处理的节点；暂停的照样显示停在哪
        if (instance.endedAt() != null) return List.of();
        Map<String, CurrentNode> open = new LinkedHashMap<>();
        for (WorkItem item : workItems.ofInstance(instance.id())) {
            if (WorkItem.OPEN.equals(item.state())) open.putIfAbsent(item.node(), new CurrentNode(item.node(), item.nodeName()));
        }
        for (String node : instance.state().waits().keySet()) {
            String name = definition.nodes().stream().filter(n -> n.id().equals(node)).map(ProcessNode::name).findFirst().orElse(null);
            open.putIfAbsent(node, new CurrentNode(node, name));
        }
        return List.copyOf(open.values());
    }
}
