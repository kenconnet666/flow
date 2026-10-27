package org.dpdns.zerodep.flow.model.definition;

import lombok.Data;

/** 时限：一段时长，或到某个时刻（日期、时间的字段） */
public interface Due {
    /** 一段时长之后 */
    @Data
    static class After implements Due {
        private final Duration duration;
    }

    /** 到某个时刻。 */
    @Data
    static class Until implements Due {
        /** 直接写的时刻，或 {@link Ref} */
        private final Object value;
    }
}
