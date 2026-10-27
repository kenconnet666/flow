package org.dpdns.zerodep.flow.core.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 给人看的失败原因：取最里层异常的话，没有话时写类名 */
class FlowErrorsTest {
    @Test
    void 取最里层的话() {
        RuntimeException wrapped = new RuntimeException("flow.extension-failed", new IllegalStateException("外部系统不可用"));
        assertEquals("外部系统不可用", FlowErrors.rootMessage(wrapped));
    }

    @Test
    void 没有话时写类名() {
        assertEquals(NullPointerException.class.getName(), FlowErrors.rootMessage(new RuntimeException("外层", new NullPointerException())));
    }
}
