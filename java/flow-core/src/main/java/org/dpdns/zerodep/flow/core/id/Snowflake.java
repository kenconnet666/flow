package org.dpdns.zerodep.flow.core.id;

import java.time.Clock;

/**
 * 雪花编号（默认的主键策略，0006 的 6.1）：落库是 BIGINT，引擎里是它的十进制文字。所有表共用一个发号器，按毫秒递增。
 * 时钟回拨时等到时钟追上上一次发号的时间；回拨太多（超过 {@code maxBackwardsMillis}）报错，免得长时间卡住。
 * <pre>{@code
 * IdStrategy ids = new Snowflake(SnowflakeLayout.CLASSIC, WorkerIdProvider.fixed(1), Clock.systemUTC());
 * }</pre>
 */
public final class Snowflake implements IdStrategy {
    /** 回拨超过这么多毫秒时报错，不再等 */
    private static final long MAX_BACKWARDS_MILLIS = 5_000;

    private final SnowflakeLayout layout;
    private final Clock clock;
    private final long worker;
    private final long timeLimit;
    private final long sequenceMask;
    private long lastMillis = -1;
    private long sequence;

    /** 机器号在构造时取一次 */
    public Snowflake(SnowflakeLayout layout, WorkerIdProvider workers, Clock clock) {
        this.layout = layout;
        this.clock = clock;
        this.worker = workers.workerId(layout.workerLimit());
        this.timeLimit = 1L << layout.timeBits();
        this.sequenceMask = (1L << layout.sequenceBits()) - 1;
    }

    @Override
    public synchronized String next(String table) {
        long now = millis();
        if (now < lastMillis) now = waitUntil(lastMillis, now);
        if (now == lastMillis) {
            sequence = (sequence + 1) & sequenceMask;
            // 这一毫秒的序号用完：等下一毫秒
            if (sequence == 0) now = waitUntil(lastMillis + 1, now);
        } else {
            sequence = 0;
        }
        lastMillis = now;
        if (now >= timeLimit) throw new IllegalStateException("the snowflake time bits are used up; move the epoch or widen timeBits");
        long id = (now << (layout.workerBits() + layout.sequenceBits())) | (worker << layout.sequenceBits()) | sequence;
        return Long.toString(id);
    }

    @Override
    public Object toDatabase(String id) { return Long.parseLong(id); }

    @Override
    public String fromDatabase(Object value) { return value == null ? null : value.toString(); }

    /** 从起始时间算起的毫秒数 */
    private long millis() {
        long now = clock.millis() - layout.epoch().toEpochMilli();
        if (now < 0) throw new IllegalStateException("the clock is before the snowflake epoch " + layout.epoch());
        return now;
    }

    /** 等到时钟走到 target；回拨太多时报错 */
    private long waitUntil(long target, long now) {
        if (target - now > MAX_BACKWARDS_MILLIS) {
            throw new IllegalStateException("the clock moved back " + (target - now) + " ms; refusing to issue ids until it catches up");
        }
        while (now < target) {
            Thread.onSpinWait();
            now = millis();
        }
        return now;
    }
}
