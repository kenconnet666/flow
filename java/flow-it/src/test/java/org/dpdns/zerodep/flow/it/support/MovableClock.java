package org.dpdns.zerodep.flow.it.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 测试用的时钟：停在一个时刻，由测试拨到后面（场景的 {@code runJobs}、看停留时长与超时） */
public class MovableClock extends Clock {
    private Instant now;

    public MovableClock(Instant start) { this.now = start; }

    /** 拨到 at；不能往回拨 */
    public void moveTo(Instant at) {
        if (at.isBefore(now)) throw new IllegalArgumentException("the test clock cannot go back from " + now + " to " + at);
        now = at;
    }

    @Override public Instant instant() { return now; }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }

    @Override public Clock withZone(ZoneId zone) { return this; }
}
