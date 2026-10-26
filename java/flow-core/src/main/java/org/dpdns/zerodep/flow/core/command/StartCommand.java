package org.dpdns.zerodep.flow.core.command;

import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.data.Variables;

import java.util.List;
import java.util.Map;

/**
 * 发起一个实例（docs/decisions/0006 的 3.1）：按这个流程最新发布的版本走。
 *
 * @param key         流程标识
 * @param form        表单的值：只取开始节点上能改的字段
 * @param variables   流程变量的初始值，盖过定义里的默认值
 * @param businessKey 业务单据编号；同一个编号可以发起多次（重新提交）
 * @param title       实例标题；不写时按定义的模板生成
 * @param priority    紧急程度，0 是常规，越大越急
 * @param picks       第一步要发起人选处理人的节点（0005 的 5.6 {@code pick}）→ 选的人
 */
public record StartCommand(String key, FormValues form, Variables variables, String businessKey, String title, int priority,
                           Map<String, List<Long>> picks) {
    /** 补上空的表单、变量与选人 */
    public StartCommand {
        form = form == null ? FormValues.empty() : form;
        variables = variables == null ? Variables.empty() : variables;
        picks = picks == null ? Map.of() : Map.copyOf(picks);
    }

    /** 只给流程标识与表单的值 */
    public static StartCommand of(String key, Map<String, ?> form) {
        return new StartCommand(key, FormValues.of(form), Variables.empty(), null, null, 0, Map.of());
    }
}
