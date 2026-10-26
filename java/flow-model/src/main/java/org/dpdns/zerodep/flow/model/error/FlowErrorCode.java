package org.dpdns.zerodep.flow.model.error;

/**
 * flow 的业务错误码（docs/decisions/0008、0006 第 8 节），与 spec/error-codes.json、TS 的 {@code FLOW_ERROR_CODES} 一致，
 * 先后也一样。{@link #code()} 是响应体里的写法（{@code flow.work-item-not-open}）。
 */
public enum FlowErrorCode {
    /** 实例、待办、定义不存在 */
    NOT_FOUND("flow.not-found"),
    /** 权限扩展点不放行 */
    FORBIDDEN("flow.forbidden"),
    /** 不是这个待办的处理人或候选 */
    NOT_CANDIDATE("flow.not-candidate"),
    /** 别人已认领 */
    ALREADY_CLAIMED("flow.already-claimed"),
    /** 待办已处理、已取消（重复提交） */
    WORK_ITEM_NOT_OPEN("flow.work-item-not-open"),
    /** 实例已结束、已暂停 */
    INSTANCE_NOT_ACTIVE("flow.instance-not-active"),
    /** 版本冲突（乐观锁） */
    CONCURRENT_MODIFICATION("flow.concurrent-modification"),
    /** 守卫不通过 */
    GUARD_REJECTED("flow.guard-rejected"),
    /** 表单或参数校验不通过，errors 里是按字段的问题 */
    VALIDATION_FAILED("flow.validation-failed"),
    /** 发布时校验不通过，errors 里是问题列表 */
    DEFINITION_INVALID("flow.definition-invalid"),
    /** 扩展抛错，带原始错误 */
    EXTENSION_FAILED("flow.extension-failed"),
    /** 没认出来的异常 */
    INTERNAL_ERROR("flow.internal-error");

    private final String code;

    FlowErrorCode(String code) { this.code = code; }

    /** 响应体里的写法 */
    public String code() { return code; }
}
