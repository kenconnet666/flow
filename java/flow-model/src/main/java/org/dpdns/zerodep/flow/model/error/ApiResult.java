package org.dpdns.zerodep.flow.model.error;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 响应体（docs/decisions/0008 的第 1 节）：HTTP 一律 200，成功与失败都看 {@code code}。与 TS 模型包的 {@code ApiResult} 同形。
 *
 * @param code    成功是 {@link #OK}；失败是任何包的业务错误码
 * @param message 后端的说明，只作兜底
 * @param params  文案里要填的参数
 * @param errors  按字段、按问题的明细
 * @param data    成功时的数据
 */
public record ApiResult<T>(String code, String message, Map<String, String> params, List<ErrorDetail> errors, T data) {
    /** 成功的码 */
    public static final String OK = "ok";

    /** 成功的响应 */
    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(OK, null, null, null, data);
    }

    /** 失败的响应：{@link FlowException} 用它的码，其余是 {@code flow.internal-error} */
    public static <T> ApiResult<T> fail(Throwable error) {
        return fail(error, e -> null);
    }

    /**
     * 失败的响应；{@code codeOf} 认出别的包的异常（引擎用它把 query 的异常写成 {@code query.} 码，0008 第 3 节"原样用它的码"），
     * 认不出时返回 null。
     */
    public static <T> ApiResult<T> fail(Throwable error, Function<Throwable, String> codeOf) {
        if (error instanceof FlowException flow) {
            return new ApiResult<>(flow.code().code(), flow.getMessage(), flow.params(), flow.errors().isEmpty() ? null : flow.errors(), null);
        }
        String code = codeOf.apply(error);
        return new ApiResult<>(code == null ? FlowErrorCode.INTERNAL_ERROR.code() : code, error.getMessage(), null, null, null);
    }
}
