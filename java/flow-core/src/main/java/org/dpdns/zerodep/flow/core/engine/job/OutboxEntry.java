package org.dpdns.zerodep.flow.core.engine.job;

import lombok.Data;
import org.dpdns.zerodep.flow.core.spi.Notification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发件箱里的一份：要可靠投给一个监听的记录，或要可靠发给一个渠道的通知。记成一个 {@link DeliveryJob} 作业，payload 是它的 JSON 形状：
 * <pre>
 *   { "listener": "名字", "events": [12, 13] }
 *   { "channel": "名字", "notifications": [{ "kind": "arrived", "template": null, "recipients": [1002], "event": 12, "workItem": 31 }] }
 * </pre>
 * 只记编号，投的时候从库里读出记录与待办（通知的实例、待办因此是投的那时的样子）。
 */
@Data
public class OutboxEntry {
    /** 监听的名字；投给渠道时为 null */
    private final String listener;
    /** 投给监听的流转记录的编号，按先后 */
    private final List<Long> events;
    /** 渠道的名字；投给监听时为 null */
    private final String channel;
    /** 发给渠道的通知 */
    private final List<Note> notifications;

    /** 一条通知：引擎算好的种类、模板、收件人，加上引起它的记录、相关的待办的编号 */
    @Data
    public static class Note {
        private final String kind;
        private final String template;
        private final List<Long> recipients;
        private final Long event;
        private final Long workItem;
    }

    /** 投给一个监听 */
    public static OutboxEntry forListener(String name, List<Long> eventIds) {
        return new OutboxEntry(name, List.copyOf(eventIds), null, List.of());
    }

    /** 发给一个渠道 */
    public static OutboxEntry forChannel(String name, List<Notification> notifications) {
        List<Note> notes = notifications.stream()
            .map(n -> new Note(n.getKind(), n.getTemplate(), n.getRecipients(), n.getEvent().getId(), n.getWorkItem() == null ? null : n.getWorkItem().getId()))
            .toList();
        return new OutboxEntry(null, List.of(), name, notes);
    }

    /** 作业的 payload */
    public Map<String, Object> toPayload() {
        Map<String, Object> out = new LinkedHashMap<>();
        if (listener != null) {
            out.put("listener", listener);
            out.put("events", events);
        } else {
            out.put("channel", channel);
            out.put("notifications", notifications.stream().map(OutboxEntry::noteToMap).toList());
        }
        return out;
    }

    /** 从作业的 payload 读回来（JSON 里的数可能读成 Integer） */
    public static OutboxEntry fromPayload(Map<String, Object> payload) {
        if (payload.get("listener") instanceof String name) return forListener(name, ids(payload.get("events")));
        List<Note> notes = new ArrayList<>();
        if (payload.get("notifications") instanceof List<?> list) {
            for (Object item : list) {
                Map<?, ?> map = (Map<?, ?>) item;
                notes.add(new Note((String) map.get("kind"), (String) map.get("template"), ids(map.get("recipients")), id(map.get("event")), id(map.get("workItem"))));
            }
        }
        return new OutboxEntry(null, List.of(), (String) payload.get("channel"), notes);
    }

    private static Map<String, Object> noteToMap(Note note) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kind", note.getKind());
        map.put("template", note.getTemplate());
        map.put("recipients", note.getRecipients());
        map.put("event", note.getEvent());
        map.put("workItem", note.getWorkItem());
        return map;
    }

    private static List<Long> ids(Object value) {
        return value instanceof List<?> list ? list.stream().map(OutboxEntry::id).toList() : List.of();
    }

    private static Long id(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }
}
