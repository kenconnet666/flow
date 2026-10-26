package org.dpdns.zerodep.flow.it.scenario;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 场景里的时钟：停在一个时刻，{@code runJobs} 那一步把它拨到后面（看超时、提醒、定时节点） */
final class MovableClock extends Clock {
    private Instant now;

    MovableClock(Instant start) { this.now = start; }

    /** 拨到 at；不能往回拨 */
    void moveTo(Instant at) {
        if (at.isBefore(now)) throw new IllegalArgumentException("the scenario clock cannot go back from " + now + " to " + at);
        now = at;
    }

    @Override public Instant instant() { return now; }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }

    @Override public Clock withZone(ZoneId zone) { return this; }
}
