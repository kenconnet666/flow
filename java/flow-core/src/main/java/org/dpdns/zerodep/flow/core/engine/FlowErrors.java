package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.model.error.ErrorDetail;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;

import java.util.List;
import java.util.Map;

/** 常用的几种带业务错误码的异常（0006 第 8 节、0008），让处理类里抛错的地方一行写完 */
final class FlowErrors {
    private FlowErrors() { }

    /** 实例、待办、定义、表单不存在：{@code what} 是哪一种，{@code id} 是编号或标识 */
    static FlowException notFound(String what, String id) {
        return new FlowException(FlowErrorCode.NOT_FOUND, Map.of(what, String.valueOf(id)));
    }

    /** 不让做：{@code reason} 说明为什么（停用了、不是发起人……） */
    static FlowException forbidden(String target, String id, String reason) {
        return new FlowException(FlowErrorCode.FORBIDDEN, Map.of(target, String.valueOf(id), "reason", reason));
    }

    /** 某个参数不对：例如结果不认识、意见必填 */
    static FlowException invalid(String field, String code) {
        return new FlowException(FlowErrorCode.VALIDATION_FAILED, field + ": " + code, List.of(ErrorDetail.field(field, code)));
    }

    /** 待办已处理、已取消 */
    static FlowException notOpen(String workItemId) {
        return new FlowException(FlowErrorCode.WORK_ITEM_NOT_OPEN, Map.of("workItem", workItemId));
    }
}
