package org.dpdns.zerodep.flow.model.error;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** 响应体（docs/decisions/0008 的第 1 节）：HTTP 一律 200，成功与失败都看 {@code code}。与 TS 模型包的 {@code ApiResult} 同形。 */
@Data
public class ApiResult<T> {
    /** 成功是 {@link #OK}；失败是任何包的业务错误码 */
    private final String code;
    /** 后端的说明，只作兜底 */
    private final String message;
    /** 文案里要填的参数 */
    private final Map<String, String> params;
    /** 按字段、按问题的明细 */
    private final List<ErrorDetail> errors;
    /** 成功时的数据 */
    private final T data;

    /** 成功的码 */
    public static final String OK = "ok";

    /** 成功的响应 */
    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(OK, null, null, null, data);
    }

    /** 失败的响应：{@link FlowException} 用它的码，其余是 {@code flow.internal-error} */
    public static <T> ApiResult<T> fail(Throwable error) {
        if (error instanceof FlowException flow) {
            return new ApiResult<>(flow.getCode().code(), flow.getMessage(), flow.getParams(), flow.getErrors().isEmpty() ? null : flow.getErrors(), null);
        }
        return new ApiResult<>(FlowErrorCode.INTERNAL_ERROR.code(), error.getMessage(), null, null, null);
    }
}
