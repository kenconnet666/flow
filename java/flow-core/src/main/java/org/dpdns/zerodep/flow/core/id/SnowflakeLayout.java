package org.dpdns.zerodep.flow.core.id;

import java.time.Instant;

/**
 * 雪花编号的位布局（0006 的 6.1）：从高到低是时间、机器号、序号，总共不超过 63 位（BIGINT 的正数）。
 *
 * @param epoch        起始时间：时间段记的是从它开始的毫秒数
 * @param timeBits     时间的位数：41 位约 69 年
 * @param workerBits   机器号的位数：10 位是 1024 台
 * @param sequenceBits 每毫秒序号的位数：12 位是每毫秒 4096 个
 */
public record SnowflakeLayout(Instant epoch, int timeBits, int workerBits, int sequenceBits) {
    /** 经典布局：起始时间 2026-01-01，时间 41 位、机器号 10 位、序号 12 位 */
    public static final SnowflakeLayout CLASSIC = new SnowflakeLayout(Instant.parse("2026-01-01T00:00:00Z"), 41, 10, 12);

    /** 校验位数 */
    public SnowflakeLayout {
        if (epoch == null) throw new IllegalArgumentException("epoch is required");
        if (timeBits < 1 || workerBits < 0 || sequenceBits < 1) throw new IllegalArgumentException("bit counts must be positive");
        if (timeBits + workerBits + sequenceBits > 63) {
            throw new IllegalArgumentException("a BIGINT snowflake has at most 63 bits, got " + (timeBits + workerBits + sequenceBits));
        }
    }

    /** 机器号的上限（不含） */
    public long workerLimit() { return 1L << workerBits; }
}
