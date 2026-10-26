package org.dpdns.zerodep.flow.core.id;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("雪花编号")
class SnowflakeTest {
    private static final Instant NOW = Instant.parse("2026-10-26T08:00:00Z");

    @Test
    void 同一毫秒里按序号递增_序号用完等下一毫秒() {
        Snowflake ids = new Snowflake(SnowflakeLayout.CLASSIC, WorkerIdProvider.fixed(5), Clock.systemUTC());
        Set<String> seen = new HashSet<>();
        long previous = -1;
        for (int i = 0; i < 10_000; i++) {
            long id = Long.parseLong(ids.next("flow_instance"));
            assertTrue(id > previous, "ids increase");
            previous = id;
            seen.add(Long.toString(id));
        }
        assertEquals(10_000, seen.size());
    }

    @Test
    void 编号由时间_机器号_序号拼成() {
        Snowflake ids = new Snowflake(SnowflakeLayout.CLASSIC, WorkerIdProvider.fixed(5), Clock.fixed(NOW, ZoneOffset.UTC));
        long id = Long.parseLong(ids.next("flow_instance"));
        long millis = NOW.toEpochMilli() - SnowflakeLayout.CLASSIC.epoch().toEpochMilli();
        assertEquals(millis, id >>> 22);
        assertEquals(5, (id >>> 12) & 0x3FF);
        assertEquals(0, id & 0xFFF);
        assertEquals(1, Long.parseLong(ids.next("flow_instance")) & 0xFFF);
    }

    @Test
    void 布局与机器号越界时报错() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeLayout(NOW, 41, 12, 12));
        assertThrows(IllegalArgumentException.class, () -> new Snowflake(SnowflakeLayout.CLASSIC, WorkerIdProvider.fixed(1024), Clock.systemUTC()));
    }
}
