package org.dpdns.zerodep.flow.core.view;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 批量认领、处理时一个待办的结果：做成了没有，没做成时是业务错误码（各个待办各自一个事务，互不影响） */
@Getter
@AllArgsConstructor
public class BatchResult {
    private final Long workItemId;
    private final boolean ok;
    /** 没做成时的业务错误码（如 {@code flow.already-claimed}、{@code flow.validation-failed}）；做成了为 null */
    private final String code;
}
