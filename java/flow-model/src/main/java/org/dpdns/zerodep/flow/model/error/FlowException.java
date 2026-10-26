package org.dpdns.zerodep.flow.model.error;

import java.util.List;
import java.util.Map;

/**
 * 带业务错误码的异常（0008）：引擎的命令、查询出错时抛它，HTTP 接口按 {@link ApiResult#fail} 写成响应体。
 * 扩展抛的原始异常放在 cause（{@link FlowErrorCode#EXTENSION_FAILED}）。
 */
public class FlowException extends RuntimeException {
    private final FlowErrorCode code;
    private final Map<String, String> params;
    private final List<ErrorDetail> errors;

    /** 只有码 */
    public FlowException(FlowErrorCode code) {
        this(code, code.code(), Map.of(), List.of(), null);
    }

    /** 码与文案里要填的参数 */
    public FlowException(FlowErrorCode code, Map<String, String> params) {
        this(code, code.code(), params, List.of(), null);
    }

    /** 码与明细（表单校验、发布校验） */
    public FlowException(FlowErrorCode code, String message, List<ErrorDetail> errors) {
        this(code, message, Map.of(), errors, null);
    }

    /**
     * 全部写明。
     *
     * @param message 后端的说明，只作兜底（前端按码取文案）
     * @param params  文案里要填的参数
     * @param errors  按字段、按问题的明细
     * @param cause   原始异常
     */
    public FlowException(FlowErrorCode code, String message, Map<String, String> params, List<ErrorDetail> errors, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.params = params == null ? Map.of() : Map.copyOf(params);
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /** 业务错误码 */
    public FlowErrorCode code() { return code; }

    /** 文案里要填的参数 */
    public Map<String, String> params() { return params; }

    /** 按字段、按问题的明细 */
    public List<ErrorDetail> errors() { return errors; }
}
