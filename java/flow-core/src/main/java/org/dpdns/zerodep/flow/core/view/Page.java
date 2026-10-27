package org.dpdns.zerodep.flow.core.view;

import com.easy.query.core.api.pagination.EasyPageResult;

import java.util.List;
import java.util.function.Function;

/**
 * 一页数据，同 vue-tsx-pro 的 {@code TablePage}：{@code { rows, total, page, size, totalPages }}，页码从 1 开始。
 * 条数是 int：编码成 typed JSON 时是普通的 JSON 数字（Long 会写成 bigint 的文字）。
 *
 * @param rows  本页的行
 * @param total 不分页时满足条件的总行数
 */
public record Page<T>(List<T> rows, int total, int page, int size, int totalPages) {
    public Page {
        rows = List.copyOf(rows);
    }

    /** easy-query 的分页结果 */
    public static <T> Page<T> of(EasyPageResult<T> result, int page, int size) {
        return of(result.getData(), result.getTotal(), page, size);
    }

    /** 已经取好的一页 */
    public static <T> Page<T> of(List<T> rows, long total, int page, int size) {
        return new Page<>(rows, Math.toIntExact(total), page, size, size == 0 ? 0 : Math.toIntExact((total + size - 1) / size));
    }

    /** 每一行换个样子 */
    public <R> Page<R> map(Function<T, R> mapper) {
        return new Page<>(rows.stream().map(mapper).toList(), total, page, size, totalPages);
    }
}
