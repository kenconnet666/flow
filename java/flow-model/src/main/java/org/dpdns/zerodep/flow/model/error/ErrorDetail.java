package org.dpdns.zerodep.flow.model.error;

import org.dpdns.zerodep.flow.model.form.FormAccess;
import org.dpdns.zerodep.flow.model.validate.Problem;

import java.util.Map;

/**
 * 错误明细的一项（0008 第 1 节响应体的 {@code errors}）：表单校验是"哪个字段、什么问题"，发布校验是"哪个节点或连线、什么问题"。
 * 与 TS 模型包的 {@code ErrorDetail} 同形；没有的部分为 null，写成 JSON 时省掉。
 *
 * @param code   问题的代号：表单的值问题（{@code required}、{@code max}……）或 0005 第 7 节的问题代号
 * @param field  表单字段
 * @param node   节点
 * @param edge   连线
 * @param lane   泳道
 * @param params 文案里要填的参数
 */
public record ErrorDetail(String code, String field, String node, String edge, String lane, Map<String, String> params) {
    /** 复制并冻结参数 */
    public ErrorDetail {
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    /** 某个字段的问题 */
    public static ErrorDetail field(String field, String code) {
        return new ErrorDetail(code, field, null, null, null, Map.of());
    }

    /** 某个节点的问题 */
    public static ErrorDetail node(String node, String code) {
        return new ErrorDetail(code, null, node, null, null, Map.of());
    }

    /** 收表单时的问题 */
    public static ErrorDetail of(FormAccess.SubmissionError error) {
        return field(error.field(), error.code());
    }

    /** 定义或字段清单的校验问题 */
    public static ErrorDetail of(Problem problem) {
        return new ErrorDetail(problem.code(), problem.field(), problem.node(), problem.edge(), problem.lane(), problem.params());
    }
}
