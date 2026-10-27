package org.dpdns.zerodep.flow.core.engine.job;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 发件箱的一份记成作业的 payload，再读回来一样；JSON 里的数读回来可能是 Integer */
class OutboxEntryTest {
    @Test
    void 投给监听的往返() {
        OutboxEntry entry = OutboxEntry.forListener("mirror", List.of(12L, 13L));
        assertEquals(entry, OutboxEntry.fromPayload(entry.toPayload()));
    }

    @Test
    void 发给渠道的从JSON读回() {
        Map<String, Object> payload = Map.of("channel", "mail", "notifications", List.of(
            Map.of("kind", "arrived", "recipients", List.of(1002, 1003), "event", 12),
            Map.of("kind", "finished", "template", "done", "recipients", List.of(1001), "event", 15, "workItem", 31)));
        OutboxEntry entry = OutboxEntry.fromPayload(payload);
        assertNull(entry.getListener());
        assertEquals("mail", entry.getChannel());
        OutboxEntry.Note first = entry.getNotifications().get(0);
        assertEquals(List.of(1002L, 1003L), first.getRecipients());
        assertEquals(12L, first.getEvent());
        assertNull(first.getTemplate());
        assertNull(first.getWorkItem());
        assertEquals(31L, entry.getNotifications().get(1).getWorkItem());
        assertEquals(entry, OutboxEntry.fromPayload(entry.toPayload()));
    }
}
