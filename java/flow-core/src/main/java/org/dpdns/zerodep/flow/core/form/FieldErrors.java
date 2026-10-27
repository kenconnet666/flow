package org.dpdns.zerodep.flow.core.form;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 后端校验出的问题：字段 → 给人看的说明；没有问题时是 {@link #none()} */
public class FieldErrors {
    private static final FieldErrors NONE = new FieldErrors(Map.of());

    private final Map<String, String> messages;

    private FieldErrors(Map<String, String> messages) {
        this.messages = Collections.unmodifiableMap(new LinkedHashMap<>(messages));
    }

    /** 没有问题 */
    public static FieldErrors none() { return NONE; }

    /** 一个字段的问题 */
    public static FieldErrors of(String field, String message) { return new FieldErrors(Map.of(field, message)); }

    /** 再加一个字段的问题 */
    public FieldErrors and(String field, String message) {
        Map<String, String> next = new LinkedHashMap<>(messages);
        next.put(field, message);
        return new FieldErrors(next);
    }

    public boolean isEmpty() { return messages.isEmpty(); }

    /** 字段 → 说明 */
    public Map<String, String> getMessages() { return messages; }
}
