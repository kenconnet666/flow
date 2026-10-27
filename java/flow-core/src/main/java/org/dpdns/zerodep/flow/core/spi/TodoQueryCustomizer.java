package org.dpdns.zerodep.flow.core.spi;

import org.dpdns.zerodep.flow.core.entity.proxy.WorkItemEntityProxy;
import org.dpdns.zerodep.flow.core.view.workitem.TodoItem;

import java.util.List;
import java.util.Map;

/**
 * 待办列表连接入方自己的表（0006 第 4、6 节）：默认只有实例上的摘要列（{@code summary}）。实现它可以
 * <ul>
 *   <li>加条件（{@link #filter}）：我的待办、我已办的每段查询上都调一次，拿到的是 easy-query 的待办代理——
 *       沿导航取实例（{@code item.instance().businessKey()}），或用子查询连自己的表：
 *       <pre>
 *   item.expression().exists(() -> item.expression().subQueryable(OrderEntity.class)
 *       .where(o -> { o.orderNo().eq(item.instance().businessKey()); o.supplierId().eq(Long.valueOf(params.get("supplier"))); }));
 *       </pre></li>
 *   <li>补业务列（{@link #columns}）：拿到这一页的行，批量查自己的表，按待办编号给出列，放进 {@link TodoItem#getBusiness()}。</li>
 * </ul>
 * 筛选参数是前端传的、接入方自己定的名字（HTTP 查询串里 {@code biz.} 开头的，去掉前缀）。默认什么都不做。
 */
public interface TodoQueryCustomizer {
    /** 什么都不做 */
    TodoQueryCustomizer NONE = new TodoQueryCustomizer() { };

    /** 加条件；params 是接入方自己的筛选参数，没传时是空的 */
    default void filter(Actor actor, Map<String, String> params, WorkItemEntityProxy item) { }

    /** 给这一页的行补业务列：待办编号 → 列名 → 值；没有的行不用给 */
    default Map<Long, Map<String, Object>> columns(Actor actor, List<TodoItem> rows) { return Map.of(); }
}
