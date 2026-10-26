package org.dpdns.zerodep.flow.model.error;

import java.util.List;
import java.util.Map;

/**
 * 带业务错误码的异常（0008）：引擎的命令、查询出错时抛它，HTTP 接口按 {@link ApiResult#fail} 写成响应体。
 * 扩展抛的原始异常放在 cause（{@link FlowErrorCode#EXTENSION_FAILED}）。
 */
public class FlowException extends RuntimeException {
    private final FlowErrorCode code;
    private final Map<String, Object> params;
    private final List<Object> errors;

    /** 只有码 */
    public FlowException(FlowErrorCode code) {
        this(code, code.code(), Map.of(), List.of(), null);
    }

    /** 码与文案里要填的参数 */
    public FlowException(FlowErrorCode code, Map<String, Object> params) {
        this(code, code.code(), params, List.of(), null);
    }

    /**
     * 全部写明。
     *
     * @param message 后端的说明，只作兜底（前端按码取文案）
     * @param params  文案里要填的参数
     * @param errors  按字段、按问题的明细
     * @param cause   原始异常
     */
    public FlowException(FlowErrorCode code, String message, Map<String, Object> params, List<?> errors, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.params = params == null ? Map.of() : Map.copyOf(params);
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /** 业务错误码 */
    public FlowErrorCode code() { return code; }

    /** 文案里要填的参数 */
    public Map<String, Object> params() { return params; }

    /** 按字段、按问题的明细 */
    public List<Object> errors() { return errors; }
}
