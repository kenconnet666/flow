package org.dpdns.zerodep.flow.core.extension;

import org.dpdns.zerodep.flow.model.runtime.ProcessInstance;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 处理人解析器（0005 的 3.2 {@code { "resolver": "deptLeader", "args": { "level": 2 } }}）：接入方按名字注册，
 * 把"部门负责人""上级第 N 级""供应商的账号"这类规则算成人。人工节点、会签的参与人、抄送节点的收件人都能用。
 */
public interface PeopleResolver {
    /** 定义里写的名字 */
    String name();

    /** 算出的人（用户编号），按先后；一个也没有时返回空列表，按节点的 {@code emptyAssignee} 处理 */
    List<Long> resolve(Context context);

    /**
     * 解析时能拿到的。
     *
     * @param instance 实例
     * @param args     定义里写的参数，其中的 {@code { ref }} 已经换成值
     * @param read     按引用的名字取值（{@code form.amount}、{@code sys.initiator}……）
     */
    record Context(ProcessInstance instance, Map<String, Object> args, Function<String, Object> read) { }
}
