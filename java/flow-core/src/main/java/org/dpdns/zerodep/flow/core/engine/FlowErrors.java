package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.store.InstanceStore.InstanceRecord;
import org.dpdns.zerodep.flow.model.error.ErrorDetail;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;

import java.util.List;
import java.util.Map;

/**
 * 处理类里常抛的带业务错误码的异常（0006 第 8 节、0008），抛错的地方一行写完。参数是给前端文案填的文字，编号写成文字。
 */
final class FlowErrors {
    private FlowErrors() { }

    /** 实例、待办、定义、表单不存在：{@code what} 是哪一种，{@code id} 是编号或标识 */
    static FlowException notFound(String what, Object id) {
        return new FlowException(FlowErrorCode.NOT_FOUND, Map.of(what, String.valueOf(id)));
    }

    /** 不让做：{@code reason} 说明为什么（停用了、不是发起人……） */
    static FlowException forbidden(String target, Object id, String reason) {
        return new FlowException(FlowErrorCode.FORBIDDEN, Map.of(target, String.valueOf(id), "reason", reason));
    }

    /** 某个参数不对：例如结果不认识、意见必填 */
    static FlowException invalid(String field, String code) {
        return new FlowException(FlowErrorCode.VALIDATION_FAILED, field + ": " + code, List.of(ErrorDetail.field(field, code)));
    }

    /** 待办已处理、已取消（重复提交） */
    static FlowException notOpen(Long workItemId) { return workItem(FlowErrorCode.WORK_ITEM_NOT_OPEN, workItemId); }

    /** 别人已认领 */
    static FlowException alreadyClaimed(Long workItemId) { return workItem(FlowErrorCode.ALREADY_CLAIMED, workItemId); }

    /** 不是这个待办的处理人或候选 */
    static FlowException notCandidate(Long workItemId) { return workItem(FlowErrorCode.NOT_CANDIDATE, workItemId); }

    /** 实例已结束、已暂停 */
    static FlowException notActive(InstanceRecord instance) {
        return new FlowException(FlowErrorCode.INSTANCE_NOT_ACTIVE, Map.of("instance", String.valueOf(instance.id()), "status", instance.status()));
    }

    private static FlowException workItem(FlowErrorCode code, Long workItemId) {
        return new FlowException(code, Map.of("workItem", String.valueOf(workItemId)));
    }
}
