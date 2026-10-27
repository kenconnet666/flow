package org.dpdns.zerodep.flow.core.extension;

import lombok.Data;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.view.InstanceVO;
import org.dpdns.zerodep.flow.core.view.WorkItemVO;

import java.util.Map;

/**
 * 提交前的守卫（0005 处理结果的 {@code guards}）：接入方按名字注册，处理人选了这个结果、表单已经合并好之后、
 * 待办改成已处理之前调；不通过时整个处理不做，报 {@code flow.guard-rejected}，明细里是守卫的名字与原因。
 * 例如"预算余额够不够""附件传了没有"。
 */
public interface OutcomeGuard {
    /** 定义里写的名字 */
    String name();

    /** 通过返回 null；不通过返回给处理人看的原因 */
    String check(Context context);

    /** 判断时能拿到的。 */
    @Data
    static class Context {
        /** 处理人 */
        private final Actor actor;
        /** 实例 */
        private final InstanceVO instance;
        /** 正在处理的待办 */
        private final WorkItemVO workItem;
        /** 选的结果 */
        private final String outcome;
        /** 合并了这次提交之后的表单的值 */
        private final Map<String, Object> form;
    }
}
