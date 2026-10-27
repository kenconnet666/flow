package org.dpdns.zerodep.flow.model;

import org.dpdns.zerodep.flow.model.error.ApiResult;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 业务错误码：与 spec/error-codes.json、TS 一侧一致 */
@DisplayName("业务错误码")
class ErrorCodesTest {
    @Test
    @DisplayName("枚举与清单一致，先后也一样")
    void matchesSpec() {
        List<?> spec = ((List<?>) Spec.read("error-codes.json").get("codes")).stream().map(c -> ((Map<?, ?>) c).get("code")).toList();
        assertEquals(spec, Arrays.stream(FlowErrorCode.values()).map(FlowErrorCode::code).toList());
    }

    @Test
    @DisplayName("响应：成功是 ok；FlowException 用它的码；认得的别的包原样用码；其余是 internal-error")
    void results() {
        assertEquals("ok", ApiResult.ok(1).code());
        ApiResult<Object> fail = ApiResult.fail(new FlowException(FlowErrorCode.WORK_ITEM_NOT_OPEN, Map.of("workItem", "7")));
        assertEquals("flow.work-item-not-open", fail.code());
        assertEquals(Map.of("workItem", "7"), fail.params());
        assertEquals("flow.internal-error", ApiResult.fail(new IllegalStateException("x")).code());
    }
}
