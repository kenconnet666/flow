package org.dpdns.zerodep.flow.core.engine.query;

import com.easy.query.core.api.dynamic.sort.ObjectSort;
import com.easy.query.core.api.dynamic.sort.ObjectSortBuilder;
import lombok.Data;
import org.dpdns.zerodep.flow.core.engine.FlowErrors;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 列表的排序：前端表格（组件库的 useTableQuery）发来的 {@code sort}，写法同组件库的 formatSorts——
 * {@code "-startedAt,title"} 是先按发起时间从新到旧、再按标题。列名就是实体的属性名，每个列表只认自己允许的几列
 * （{@link #allow}），交给 easy-query 的 {@code orderByObject}；列表自己的默认顺序接在后面，保证翻页时顺序稳定。
 */
public class SortOrder implements ObjectSort {
    /** 不排（用列表的默认顺序） */
    public static final SortOrder NONE = new SortOrder(List.of());

    private final List<Item> items;

    private SortOrder(List<Item> items) { this.items = List.copyOf(items); }

    /** 一列。 */
    @Data
    public static class Item {
        /** 实体的属性名 */
        private final String column;
        private final boolean ascending;
    }

    /** 读 {@code "-a,b"}；空的、null 是不排 */
    public static SortOrder parse(String text) {
        if (text == null || text.isBlank()) return NONE;
        List<Item> items = new ArrayList<>();
        for (String part : text.split(",")) {
            String trimmed = part.trim();
            boolean descending = trimmed.startsWith("-");
            String column = descending ? trimmed.substring(1) : trimmed;
            if (!column.isEmpty()) items.add(new Item(column, !descending));
        }
        return new SortOrder(items);
    }

    /** 只认这几列：别的列报 {@code flow.validation-failed}（sort: unknown-column），不让前端按任意列排 */
    public SortOrder allow(Set<String> columns) {
        for (Item item : items) if (!columns.contains(item.getColumn())) throw FlowErrors.invalid("sort", "unknown-column");
        return this;
    }

    public boolean isEmpty() { return items.isEmpty(); }

    public List<Item> getItems() { return items; }

    @Override
    public void configure(ObjectSortBuilder builder) {
        for (Item item : items) builder.orderBy(item.getColumn(), item.isAscending());
    }
}
