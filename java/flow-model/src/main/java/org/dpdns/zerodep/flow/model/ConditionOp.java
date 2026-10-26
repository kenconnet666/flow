package org.dpdns.zerodep.flow.model;

/** 比较的方式，同组件库的 14 种；{@link #json()} 是 JSON 里的写法 */
public enum ConditionOp {
    /** 等于 */
    EQ("eq"),
    /** 不等于 */
    NE("ne"),
    /** 大于 */
    GT("gt"),
    /** 大于等于 */
    GTE("gte"),
    /** 小于 */
    LT("lt"),
    /** 小于等于 */
    LTE("lte"),
    /** 介于两个值之间，含两端 */
    BETWEEN("between"),
    /** 是其中之一 */
    IN("in"),
    /** 不是其中任何一个 */
    NOT_IN("notIn"),
    /** 文字包含 */
    CONTAINS("contains"),
    /** 文字不包含 */
    NOT_CONTAINS("notContains"),
    /** 文字以它开头 */
    STARTS_WITH("startsWith"),
    /** 有值 */
    EXISTS("exists"),
    /** 没有值 */
    EMPTY("empty");

    private final String json;

    ConditionOp(String json) { this.json = json; }

    /** JSON 里的写法 */
    public String json() { return json; }

    /** 按 JSON 里的写法找；没有这种比较时报 IllegalArgumentException */
    public static ConditionOp of(String json) {
        for (ConditionOp op : values()) if (op.json.equals(json)) return op;
        throw new IllegalArgumentException("unknown condition op '" + json + "'");
    }

    /** 这种比较要不要值 */
    public boolean needsValue() { return this != EXISTS && this != EMPTY; }

    /** "没有一项满足"的比较：字段的值是数组时，要求每一项都不满足对应的肯定比较 */
    boolean negative() { return this == NE || this == NOT_IN || this == NOT_CONTAINS; }
}
