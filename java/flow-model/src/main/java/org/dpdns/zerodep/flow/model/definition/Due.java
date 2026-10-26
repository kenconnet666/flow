package org.dpdns.zerodep.flow.model.definition;

/** 时限：一段时长，或到某个时刻（日期、时间的字段） */
public sealed interface Due {
    /** 一段时长之后 */
    record After(Duration duration) implements Due { }

    /**
     * 到某个时刻。
     *
     * @param value 直接写的时刻，或 {@link Ref}
     */
    record Until(Object value) implements Due { }
}
