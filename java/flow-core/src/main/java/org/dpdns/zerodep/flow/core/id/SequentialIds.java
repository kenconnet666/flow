package org.dpdns.zerodep.flow.core.id;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 按顺序发号（1、2、3……，所有表共用一个计数）：场景用例与测试用它，让两个后端跑出来的编号逐个相同（0001 的"场景用例的格式"）。
 * 只适合单进程，生产用 {@link Snowflake}。
 */
public final class SequentialIds implements IdStrategy {
    private final AtomicLong next;

    /** 从 1 开始 */
    public SequentialIds() { this(1); }

    /** 从 first 开始 */
    public SequentialIds(long first) { this.next = new AtomicLong(first); }

    @Override
    public String next(String table) { return Long.toString(next.getAndIncrement()); }

    @Override
    public Object toDatabase(String id) { return Long.parseLong(id); }

    @Override
    public String fromDatabase(Object value) { return value == null ? null : value.toString(); }
}
