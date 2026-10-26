package org.dpdns.zerodep.flow.model.definition;

/** 字段在一个节点上的权限（0004 第 4 节），从严到宽；{@link #json()} 是 JSON 里的写法 */
public enum FieldAccess {
    HIDDEN("hidden"), READONLY("readonly"), EDITABLE("editable"), REQUIRED("required");

    private final String json;

    FieldAccess(String json) { this.json = json; }

    /** JSON 里的写法 */
    public String json() { return json; }

    /** 按 JSON 里的写法找；没有时报 IllegalArgumentException */
    public static FieldAccess of(String json) {
        for (FieldAccess access : values()) if (access.json.equals(json)) return access;
        throw new IllegalArgumentException("unknown field access '" + json + "'");
    }
}
